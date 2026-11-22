package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.MessageType
import com.example.data.ParticipantRole
import com.example.data.RoomParticipant
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.media.RemoteVideoFeed
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.RolePillBadge
import com.example.ui.components.SafetyTagChip
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.VoiceWaveformBars
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan
import com.example.viewmodel.RoomViewModel
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack

private data class ActionTarget(
    val userId: String,
    val displayName: String,
    val tier: StaffTier,
    val participant: RoomParticipant?,
    val message: ChatMessage?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    viewModel: RoomViewModel,
    userProfile: UserProfile,
    staffCapabilities: StaffCapabilities,
    onExitRoom: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val participantsState by viewModel.participantsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val modLogsState by viewModel.modLogsState.collectAsStateWithLifecycle()
    val liveKitRoom by viewModel.liveKitRoom.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoFeeds by viewModel.remoteVideoFeeds.collectAsStateWithLifecycle()
    val isMicActive by viewModel.isMicActive.collectAsStateWithLifecycle()
    val isCamActive by viewModel.isCamActive.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val isHandRaised by viewModel.isHandRaised.collectAsStateWithLifecycle()
    val pushToTalkMode by viewModel.pushToTalkMode.collectAsStateWithLifecycle()
    val liveVoiceAmplitude by viewModel.liveVoiceAmplitude.collectAsStateWithLifecycle()
    val slowModeCountdown by viewModel.slowModeCountdown.collectAsStateWithLifecycle()
    val isCurrentlyInvisible by viewModel.isCurrentlyInvisible.collectAsStateWithLifecycle()

    var showModCenter by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var reportTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var suspendTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var alertTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var editUserProfileTarget by remember { mutableStateOf<UserProfile?>(null) }
    var stageExpanded by remember { mutableStateOf(true) }
    var messageInput by remember { mutableStateOf("") }

    LaunchedEffect(context) {
        viewModel.attachMediaContext(context)
    }

    LaunchedEffect(staffCapabilities) {
        viewModel.updateStaffCapabilities(staffCapabilities)
    }

    LaunchedEffect(userProfile) {
        viewModel.onUserProfileUpdated(userProfile)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setCameraLive(true)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setMicLive(true)
        }
    }

    fun requestOrToggleCamera() {
        if (isCamActive) {
            viewModel.setCameraLive(false)
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) viewModel.setCameraLive(true)
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestOrSetMic(live: Boolean) {
        if (!live) {
            viewModel.setMicLive(false)
            return
        }
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) viewModel.setMicLive(true)
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler {
        viewModel.leaveRoomAndExit(onExitRoom)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.roomExitSignal.collect { reason ->
            snackbarHostState.showSnackbar(reason)
            viewModel.leaveRoomAndExit(onExitRoom)
        }
    }

    val room = (roomState as? UiState.Success)?.data
    val participants = (participantsState as? UiState.Success)?.data.orEmpty()
    val visibleParticipants = remember(participants) {
        participants.filter { !it.invisible }
    }
    val allMessages = (messagesState as? UiState.Success)?.data.orEmpty()
    val visibleMessages = remember(allMessages, userProfile.blockedUserIds) {
        allMessages.filterNot { it.senderId in userProfile.blockedUserIds }
    }
    val modLogs = (modLogsState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(room?.id) {
        if (room != null && room.status == "LIVE") {
            viewModel.ensureJoined(room, userProfile)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        when (val state = roomState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VoiceCyan)
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is UiState.Success -> {
                val activeRoom = state.data
                if (activeRoom == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Room not found or ended.")
                    }
                } else {
                    val canEditRoom = activeRoom.canEditOrCloseRoom(viewModel.currentUserId, staffCapabilities.tier)
                    val isMod = activeRoom.hasModPrivileges(viewModel.currentUserId, staffCapabilities.tier)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        RoomTopBar(
                            room = activeRoom,
                            participantCount = visibleParticipants.size.coerceAtLeast(activeRoom.participantCount.toInt()),
                            isMod = isMod,
                            canEditRoom = canEditRoom,
                            canToggleInvisible = staffCapabilities.effectiveCanEnterInvisible,
                            isCurrentlyInvisible = isCurrentlyInvisible,
                            staffTier = staffCapabilities.tier,
                            onBack = { viewModel.leaveRoomAndExit(onExitRoom) },
                            onEditRoomName = { showEditRoomDialog = true },
                            onToggleInvisible = { viewModel.toggleInRoomInvisible() },
                            onOpenParticipants = { showParticipants = true },
                            onOpenModCenter = { showModCenter = true }
                        )

                        if (isCurrentlyInvisible) {
                            Surface(
                                color = usernameColor(staffCapabilities.tier).copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VisibilityOff,
                                            contentDescription = "Invisible mode active",
                                            tint = usernameColor(staffCapabilities.tier),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (staffCapabilities.isAdmin) {
                                                "INVISIBLE MODE · Hidden from room (Admin can speak on mic anytime)"
                                            } else {
                                                "INVISIBLE MODE · Hidden from room (Go Visible to use mic)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = usernameColor(staffCapabilities.tier)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.toggleInRoomInvisible() },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("go_visible_banner_button")
                                    ) {
                                        Text("Go Visible", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        if (activeRoom.adminMicPriorityActive) {
                            val adminRed = usernameColor(StaffTier.ADMIN)
                            Surface(
                                color = adminRed.copy(alpha = 0.18f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Admin mic priority",
                                        tint = adminRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (staffCapabilities.isAdmin) {
                                            if (isCurrentlyInvisible) {
                                                "EXCLUSIVE ADMIN MIC LIVE (INVISIBLE) — All other microphones locked"
                                            } else {
                                                "EXCLUSIVE ADMIN MIC PRIORITY ACTIVE — Nobody else can use the mic"
                                            }
                                        } else {
                                            "Main Administrator is on the microphone — all other microphones are locked"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = adminRed
                                    )
                                }
                            }
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val isWide = maxWidth >= 700.dp
                            if (isWide) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    StageSection(
                                        room = activeRoom,
                                        participants = visibleParticipants,
                                        currentUserId = viewModel.currentUserId,
                                        liveKitRoom = liveKitRoom,
                                        localVideoTrack = localVideoTrack,
                                        remoteVideoFeeds = remoteVideoFeeds,
                                        isCamActive = isCamActive,
                                        useFrontCamera = useFrontCamera,
                                        isMicActive = isMicActive,
                                        liveAmplitude = liveVoiceAmplitude,
                                        expanded = true,
                                        onToggleExpand = {},
                                        onFlipCamera = { viewModel.flipCamera() },
                                        onSelectParticipant = { p ->
                                            actionTarget = ActionTarget(p.userId, p.displayName, p.staffTierEnum(), p, null)
                                        },
                                        modifier = Modifier
                                            .weight(0.46f)
                                            .fillMaxHeight()
                                    )
                                    ChatTimelineSection(
                                        room = activeRoom,
                                        messages = visibleMessages,
                                        currentUserId = viewModel.currentUserId,
                                        onTapMessage = { msg ->
                                            val p = participants.firstOrNull { it.userId == msg.senderId }
                                            actionTarget = ActionTarget(
                                                userId = msg.senderId,
                                                displayName = msg.senderName,
                                                tier = p?.staffTierEnum() ?: msg.senderTierEnum(),
                                                participant = p,
                                                message = msg
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(0.54f)
                                            .fillMaxHeight()
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    StageSection(
                                        room = activeRoom,
                                        participants = visibleParticipants,
                                        currentUserId = viewModel.currentUserId,
                                        liveKitRoom = liveKitRoom,
                                        localVideoTrack = localVideoTrack,
                                        remoteVideoFeeds = remoteVideoFeeds,
                                        isCamActive = isCamActive,
                                        useFrontCamera = useFrontCamera,
                                        isMicActive = isMicActive,
                                        liveAmplitude = liveVoiceAmplitude,
                                        expanded = stageExpanded,
                                        onToggleExpand = { stageExpanded = !stageExpanded },
                                        onFlipCamera = { viewModel.flipCamera() },
                                        onSelectParticipant = { p ->
                                            actionTarget = ActionTarget(p.userId, p.displayName, p.staffTierEnum(), p, null)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    ChatTimelineSection(
                                        room = activeRoom,
                                        messages = visibleMessages,
                                        currentUserId = viewModel.currentUserId,
                                        onTapMessage = { msg ->
                                            val p = participants.firstOrNull { it.userId == msg.senderId }
                                            actionTarget = ActionTarget(
                                                userId = msg.senderId,
                                                displayName = msg.senderName,
                                                tier = p?.staffTierEnum() ?: msg.senderTierEnum(),
                                                participant = p,
                                                message = msg
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }

                        RoomBottomDock(
                            room = activeRoom,
                            currentUserId = viewModel.currentUserId,
                            userProfile = userProfile,
                            staffCapabilities = staffCapabilities,
                            isCurrentlyInvisible = isCurrentlyInvisible,
                            messageInput = messageInput,
                            onMessageInputChange = { messageInput = it.take(500) },
                            slowModeCountdown = slowModeCountdown,
                            isMicActive = isMicActive,
                            isCamActive = isCamActive,
                            isHandRaised = isHandRaised,
                            pushToTalkMode = pushToTalkMode,
                            liveAmplitude = liveVoiceAmplitude,
                            onSend = {
                                if (messageInput.isNotBlank()) {
                                    viewModel.sendChatMessage(messageInput, userProfile)
                                    messageInput = ""
                                }
                            },
                            onSendReaction = { emoji ->
                                viewModel.sendChatMessage(emoji, userProfile, isReaction = true)
                            },
                            onToggleMicMode = { viewModel.togglePushToTalkMode() },
                            onMicStateChange = { live -> requestOrSetMic(live) },
                            onToggleCamera = { requestOrToggleCamera() },
                            onToggleRaiseHand = { viewModel.toggleRaiseHand() }
                        )
                    }
                }
            }
        }
    }

    if (showEditRoomDialog && room != null) {
        EditRoomNameDialog(
            room = room,
            onSave = { newTitle, newTopic ->
                viewModel.editRoomDetails(userProfile.displayName, newTitle, newTopic)
            },
            onDeleteName = { topic ->
                viewModel.editRoomDetails(userProfile.displayName, "", topic)
            },
            onDismiss = { showEditRoomDialog = false }
        )
    }

    if (showModCenter && room != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showModCenter = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ModerationCenterSheetContent(
                room = room,
                currentUserId = viewModel.currentUserId,
                staffCapabilities = staffCapabilities,
                modLogs = modLogs,
                onUpdateSettings = { autoMod, strict, links, locked, stageLocked, slow, video, words, actionType, reason ->
                    viewModel.updateSafetySettings(
                        actorName = userProfile.displayName,
                        autoModEnabled = autoMod,
                        autoModBlockMode = strict,
                        linksBlocked = links,
                        roomLocked = locked,
                        stageMicLocked = stageLocked,
                        slowModeSeconds = slow,
                        videoEnabled = video,
                        blockedWords = words,
                        actionType = actionType,
                        reason = reason
                    )
                },
                onOpenEditRoom = {
                    showModCenter = false
                    showEditRoomDialog = true
                },
                onUnmuteUser = { uid ->
                    viewModel.toggleMuteParticipant(uid, "User", userProfile.displayName, mute = false)
                },
                onUnbanUser = { uid ->
                    viewModel.unbanUser(uid, userProfile.displayName)
                },
                onCloseRoom = {
                    showModCenter = false
                    viewModel.closeRoomAsHostOrAdmin(userProfile.displayName, onExitRoom)
                }
            )
        }
    }

    if (showParticipants && room != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showParticipants = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ParticipantsSheetContent(
                room = room,
                participants = participants,
                currentUserId = viewModel.currentUserId,
                staffCapabilities = staffCapabilities,
                onSelectParticipant = { p ->
                    showParticipants = false
                    actionTarget = ActionTarget(p.userId, p.displayName, p.staffTierEnum(), p, null)
                },
                onApproveSpeaker = { p ->
                    viewModel.changeParticipantRole(p.userId, p.displayName, userProfile.displayName, ParticipantRole.SPEAKER)
                }
            )
        }
    }

    val currentTarget = actionTarget
    if (currentTarget != null && room != null) {
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            UserModerationActionSheet(
                room = room,
                currentUserId = viewModel.currentUserId,
                staffCapabilities = staffCapabilities,
                targetUserId = currentTarget.userId,
                targetName = currentTarget.displayName,
                targetTier = currentTarget.tier,
                targetParticipant = currentTarget.participant,
                selectedMessage = currentTarget.message,
                onMuteToggle = { mute ->
                    viewModel.toggleMuteParticipant(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        mute = mute
                    )
                },
                onChangeRole = { newRole ->
                    viewModel.changeParticipantRole(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        newRole = newRole
                    )
                },
                onBounce = {
                    viewModel.bounceUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        reason = "Bounced by ${userProfile.displayName}"
                    )
                },
                onKick = {
                    viewModel.kickOrBanUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        ban = false,
                        reason = "Kicked by ${userProfile.displayName}"
                    )
                },
                onBan = {
                    viewModel.kickOrBanUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        actorName = userProfile.displayName,
                        ban = true,
                        reason = "Banned by ${userProfile.displayName}"
                    )
                },
                onSuspend = { suspendTarget = currentTarget },
                onSendAlert = { alertTarget = currentTarget },
                onToggleSupportRole = { makeSupport ->
                    viewModel.setSupportRoleInRoom(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        makeSupport = makeSupport,
                        canEnterInvisible = false,
                        adminName = userProfile.displayName
                    )
                },
                onEditUserAdmin = {
                    viewModel.fetchTargetUserProfile(currentTarget.userId) { loaded ->
                        if (loaded != null) {
                            editUserProfileTarget = loaded
                        }
                    }
                },
                onRedactMessage = {
                    currentTarget.message?.let { msg ->
                        viewModel.redactMessage(msg, userProfile.displayName)
                    }
                },
                onReport = { reportTarget = currentTarget },
                onBlockUser = {
                    viewModel.blockUser(
                        targetUserId = currentTarget.userId,
                        targetName = currentTarget.displayName,
                        currentBlocked = userProfile.blockedUserIds
                    )
                },
                onDismiss = { actionTarget = null }
            )
        }
    }

    reportTarget?.let { target ->
        ReportSafetyDialog(
            targetName = target.displayName,
            onDismiss = { reportTarget = null },
            onSubmit = { category, details ->
                viewModel.submitReport(
                    reporterName = userProfile.displayName,
                    targetUserId = target.userId,
                    targetName = target.displayName,
                    category = category,
                    details = details
                )
                reportTarget = null
            }
        )
    }

    suspendTarget?.let { target ->
        SuspendUserDialog(
            targetName = target.displayName,
            onSuspend = { minutes, reason ->
                viewModel.suspendUser(
                    targetUserId = target.userId,
                    targetName = target.displayName,
                    actorName = userProfile.displayName,
                    durationMinutes = minutes,
                    reason = reason
                )
            },
            onDismiss = { suspendTarget = null }
        )
    }

    alertTarget?.let { target ->
        SendOrEditAlertDialog(
            existingAlert = null,
            targetName = target.displayName,
            onSave = { title, message, severity, _ ->
                viewModel.sendAlertToUser(
                    targetUserId = target.userId,
                    targetName = target.displayName,
                    senderName = userProfile.displayName,
                    title = title,
                    message = message,
                    severity = severity
                )
            },
            onDismiss = { alertTarget = null }
        )
    }

    editUserProfileTarget?.let { loadedProfile ->
        EditUserAdminDialog(
            profile = loadedProfile,
            onSave = { displayName, handle, bio, globallyBanned, disableCreateRoom, disableEnterRoom, disableMic, disableVideo, suspendedUntilMillis, suspensionReason ->
                viewModel.adminEditUserInRoom(
                    targetUserId = loadedProfile.userId,
                    displayName = displayName,
                    handle = handle,
                    bio = bio,
                    globallyBanned = globallyBanned,
                    disableCreateRoom = disableCreateRoom,
                    disableEnterRoom = disableEnterRoom,
                    disableMic = disableMic,
                    disableVideo = disableVideo,
                    suspendedUntilMillis = suspendedUntilMillis,
                    suspensionReason = suspensionReason,
                    actorName = userProfile.displayName
                )
            },
            onDismiss = { editUserProfileTarget = null }
        )
    }
}

@Composable
private fun LiveKitTrackRenderer(
    room: Room,
    videoTrack: VideoTrack,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                room.initVideoRenderer(this)
                setMirror(mirror)
                videoTrack.addRenderer(this)
            }
        
