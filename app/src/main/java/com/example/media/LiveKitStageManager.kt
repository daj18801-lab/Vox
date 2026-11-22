package com.example.media

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RemoteVideoFeed(
    val userId: String,
    val displayName: String,
    val track: VideoTrack
)

class LiveKitStageManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var lkRoom: Room? = null
    private var connectJob: Job? = null
    private var eventJob: Job? = null
    private var meterJob: Job? = null
    private var connectedRoomId: String? = null
    private var isRoomConnected = false

    private val _roomInstance = MutableStateFlow<Room?>(null)
    val roomInstance: StateFlow<Room?> = _roomInstance.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoFeeds = MutableStateFlow<List<RemoteVideoFeed>>(emptyList())
    val remoteVideoFeeds: StateFlow<List<RemoteVideoFeed>> = _remoteVideoFeeds.asStateFlow()

    private val _speakingAmplitude = MutableStateFlow(0f)
    val speakingAmplitude: StateFlow<Float> = _speakingAmplitude.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    fun connectIfNeeded(roomId: String, userId: String, displayName: String) {
        if (connectedRoomId == roomId && (isRoomConnected || connectJob?.isActive == true)) return
        disconnect()
        connectedRoomId = roomId

        val tokenServerId = BuildConfig.LIVEKIT_TOKEN_SERVER_ID.trim().ifEmpty { "vox-2qnude" }

        connectJob = scope.launch {
            try {
                val room = LiveKit.create(appContext)
                lkRoom = room
                _roomInstance.value = room

                eventJob = launch {
                    room.events.collect { event ->
                        when (event) {
                            is RoomEvent.TrackSubscribed,
                            is RoomEvent.TrackUnsubscribed,
                            is RoomEvent.TrackPublished,
                            is RoomEvent.TrackUnpublished,
                            is RoomEvent.TrackMuted,
                            is RoomEvent.TrackUnmuted,
                            is RoomEvent.ParticipantDisconnected,
                            is RoomEvent.ParticipantConnected -> refreshTracks()
                            else -> Unit
                        }
                    }
                }

                val tokenSource = TokenSource.fromDevelopmentTokenServer(tokenServerId)
                val credentials = tokenSource.fetch(
                    TokenRequestOptions(
                        roomName = roomId,
                        participantName = displayName.ifBlank { "Member" },
                        participantIdentity = userId
                    )
                ).getOrThrow()

                room.connect(
                    url = credentials.serverUrl,
                    token = credentials.participantToken
                )

                isRoomConnected = true
                _connectionError.value = null
                refreshTracks()
                startSpeakingLevelLoop()
            } catch (e: Exception) {
                Log.e(TAG, "LiveKit connect failed", e)
                isRoomConnected = false
                connectedRoomId = null
                _connectionError.value = "Media server error: ${e.message}"
            }
        }
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        try {
            // Wait for room.connect() to finish if it's still connecting
            connectJob?.join()

            val room = lkRoom
            if (room == null || !isRoomConnected) {
                if (enabled) {
                    _connectionError.value = _connectionError.value
                        ?: "Mic failed: LiveKit room is not connected."
                }
                return
            }
            room.localParticipant.setMicrophoneEnabled(enabled)
            if (!enabled) _speakingAmplitude.value = 0f
        } catch (e: Exception) {
            Log.w(TAG, "Failed toggling mic: ${e.message}")
            _connectionError.value = "Mic publish error: ${e.message}"
        }
    }

    suspend fun setCameraEnabled(enabled: Boolean, useFrontCamera: Boolean) {
        try {
            // Wait for room.connect() to finish if it's still connecting
            connectJob?.join()

            val room = lkRoom
            if (room == null || !isRoomConnected) {
                if (enabled) {
                    _connectionError.value = _connectionError.value
                        ?: "Camera failed: LiveKit room is not connected."
                }
                return
            }
            val local = room.localParticipant
            local.setCameraEnabled(enabled)
            if (enabled) {
                val videoTrack = local.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                videoTrack?.switchCamera(
                    position = if (useFrontCamera) CameraPosition.FRONT else CameraPosition.BACK
                )
            }
            refreshTracks()
        } catch (e: Exception) {
            Log.w(TAG, "Failed toggling camera: ${e.message}")
            _connectionError.value = "Camera publish error: ${e.message}"
        }
    }

    suspend fun switchCamera(useFrontCamera: Boolean) {
        try {
            val videoTrack = lkRoom?.localParticipant
                ?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
            videoTrack?.switchCamera(
                position = if (useFrontCamera) CameraPosition.FRONT else CameraPosition.BACK
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed switching camera: ${e.message}")
        }
    }

    private fun refreshTracks() {
        val room = lkRoom ?: return
        val localCam = room.localParticipant
            .getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        _localVideoTrack.value = if (room.localParticipant.isCameraEnabled) localCam else null

        val remotes = room.remoteParticipants.values.mapNotNull { remote ->
            val pub = remote.getTrackPublication(Track.Source.CAMERA)
            val vTrack = pub?.track as? VideoTrack
            if (vTrack != null && !pub.muted) {
                RemoteVideoFeed(
                    userId = remote.identity?.value ?: remote.sid.value,
                    displayName = remote.name ?: "Speaker",
                    track = vTrack
                )
            } else {
                null
            }
        }
        _remoteVideoFeeds.value = remotes
    }

    private fun startSpeakingLevelLoop() {
        meterJob?.cancel()
        meterJob = scope.launch {
            while (isActive) {
                val local = lkRoom?.localParticipant
                _speakingAmplitude.value = if (local != null && local.isMicrophoneEnabled) {
                    local.audioLevel.coerceIn(0f, 1f)
                } else {
                    0f
                }
                delay(120L)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        meterJob?.cancel()
        meterJob = null
        eventJob?.cancel()
        eventJob = null
        isRoomConnected = false
        connectedRoomId = null
        _localVideoTrack.value = null
        _remoteVideoFeeds.value = emptyList()
        _speakingAmplitude.value = 0f
        try {
            lkRoom?.disconnect()
            lkRoom?.release()
        } catch (_: Exception) {
        }
        lkRoom = null
        _roomInstance.value = null
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    private companion object {
        const val TAG = "LiveKitStageManager"
    }
}
