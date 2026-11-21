package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlertSeverity
import com.example.data.ChatRoom
import com.example.data.ReportStatus
import com.example.data.RoomCategory
import com.example.data.RoomCoverKey
import com.example.data.RoomVisibility
import com.example.data.SafetyReport
import com.example.data.StaffCapabilities
import com.example.data.StaffInvite
import com.example.data.StaffRole
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserAlert
import com.example.data.UserProfile
import com.example.data.VoxStageRepository
import com.example.data.unwrapFirestoreError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: VoxStageRepository,
    val currentUserId: String,
    initialDisplayName: String,
    val initialEmail: String,
    val initialEmailVerified: Boolean
) : ViewModel() {

    private val isBootstrappedAdmin: Boolean =
        repository.isBootstrappedAdmin(initialEmail, initialEmailVerified)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<RoomCategory?>(null)
    val selectedCategory: StateFlow<RoomCategory?> = _selectedCategory.asStateFlow()

    private val _showOnlyMyRooms = MutableStateFlow(false)
    val showOnlyMyRooms: StateFlow<Boolean> = _showOnlyMyRooms.asStateFlow()

    private val _isCreatingRoom = MutableStateFlow(false)
    val isCreatingRoom: StateFlow<Boolean> = _isCreatingRoom.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val staffCapabilities: StateFlow<StaffCapabilities> =
        repository.observeMyStaffCapabilities(currentUserId, initialEmail, initialEmailVerified)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = if (isBootstrappedAdmin) {
                    StaffCapabilities(StaffTier.ADMIN, true)
                } else {
                    StaffCapabilities(StaffTier.USER, false)
                }
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val publicRoomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observePublicRooms()
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing public rooms", root)
            emit(UiState.Error(root.message ?: "Failed to load live rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val myRoomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observeMyRooms(currentUserId)
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing hosted rooms", root)
            emit(UiState.Error(root.message ?: "Failed to load your rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val safetyReportsState: StateFlow<UiState<List<SafetyReport>>> =
        repository.observeSafetyReports(currentUserId, isBootstrappedAdmin)
            .map<List<SafetyReport>, UiState<List<SafetyReport>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing safety reports", root)
                emit(UiState.Error(root.message ?: "Failed to load safety reports"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val unreadAlertsState: StateFlow<List<UserAlert>> =
        repository.observeMyUnreadAlerts(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing unread alerts", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    // Admin-only collections (zero queries issued for non-admins)
    val staffRolesState: StateFlow<List<StaffRole>> =
        (if (isBootstrappedAdmin) repository.observeAllStaffRoles() else flowOf(emptyList()))
            .catch { error ->
                Log.w(TAG, "Error observing staff roles", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val staffInvitesState: StateFlow<List<StaffInvite>> =
        (if (isBootstrappedAdmin) repository.observeAllStaffInvites() else flowOf(emptyList()))
            .catch { error ->
                Log.w(TAG, "Error observing staff invites", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val allUsersState: StateFlow<List<UserProfile>> =
        (if (isBootstrappedAdmin) repository.observeAllUsersForAdmin() else flowOf(emptyList()))
            .catch { error ->
                Log.w(TAG, "Error observing user directory", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val allAlertsState: StateFlow<List<UserAlert>> =
        (if (isBootstrappedAdmin) repository.observeAllAlertsForAdmin() else flowOf(emptyList()))
            .catch { error ->
                Log.w(TAG, "Error observing all alerts", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    init {
        viewModelScope.launch {
            val emailPrefix = initialEmail.substringBefore("@").ifBlank { "studio" }
            repository.ensureUserProfile(
                displayName = initialDisplayName.ifBlank { "Studio Host" },
                emailPrefix = emailPrefix
            ).onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not initialize profile")
            }
            repository.ensureStaffRoleOnSignIn(
                displayName = initialDisplayName.ifBlank { "Studio Host" },
                email = initialEmail,
                emailVerified = initialEmailVerified
            )
        }
    }

    private fun currentActorName(): String =
        (userProfileState.value as? UiState.Success)?.data?.displayName?.ifBlank { "Administrator" }
            ?: "Administrator"

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: RoomCategory?) {
        _selectedCategory.value = category
    }

    fun setShowOnlyMyRooms(onlyMine: Boolean) {
        _showOnlyMyRooms.value = onlyMine
    }

    fun canEnterRoomOrNotify(): Boolean {
        if (staffCapabilities.value.isAdmin) return true
        val profile = (userProfileState.value as? UiState.Success)?.data ?: return true
        return when {
            profile.globallyBanned -> {
                _snackbarEvents.tryEmit("Your account is banned from entering rooms.")
                false
            }
            profile.currentlySuspended() -> {
                val left = profile.remainingSuspensionLabel()
                _snackbarEvents.tryEmit("Suspended for $left. ${profile.suspensionReason}".trim())
                false
            }
            profile.disableEnterRoom -> {
                _snackbarEvents.tryEmit("Entering rooms has been disabled on your account by an Administrator.")
                false
            }
            else -> true
        }
    }

    fun createRoom(
        title: String,
        topic: String,
        category: RoomCategory,
        coverKey: RoomCoverKey,
        visibility: RoomVisibility,
        videoEnabled: Boolean,
        autoModEnabled: Boolean,
        autoModBlockMode: Boolean,
        linksBlocked: Boolean,
        slowModeSeconds: Long,
        maxSpeakers: Long,
        onCreated: (String) -> Unit
    ) {
        if (_isCreatingRoom.value) return
        val caps = staffCapabilities.value
        val currentProfile = (userProfileState.value as? UiState.Success)?.data

        if (!caps.isAdmin && currentProfile != null) {
            if (currentProfile.globallyBanned) {
                _snackbarEvents.tryEmit("Your account is banned from creating rooms.")
                return
            }
            if (currentProfile.currentlySuspended()) {
                _snackbarEvents.tryEmit("You are suspended for ${currentProfile.remainingSuspensionLabel()}.")
                return
            }
            if (currentProfile.disableCreateRoom) {
                _snackbarEvents.tryEmit("Creating rooms has been disabled on your account by an Administrator.")
                return
            }
        }

        val hostName = currentProfile?.displayName ?: "Studio Host"

        viewModelScope.launch {
            _isCreatingRoom.value = true
            repository.createRoom(
                hostName = hostName,
                hostTier = caps.tier,
                title = title,
                topic = topic,
                category = category,
                coverKey = coverKey,
                visibility = visibility,
                videoEnabled = videoEnabled,
                autoModEnabled = autoModEnabled,
                autoModBlockMode = autoModBlockMode,
                linksBlocked = linksBlocked,
                slowModeSeconds = slowModeSeconds,
                maxSpeakers = maxSpeakers
            ).onSuccess { roomId ->
                _isCreatingRoom.value = false
                onCreated(roomId)
            }.onFailure { err ->
                _isCreatingRoom.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not create room")
            }
        }
    }

    fun editRoomDetails(room: ChatRoom, newTitle: String, newTopic: String) {
        viewModelScope.launch {
            repository.editRoomDetails(
                room = room,
                actorName = currentActorName(),
                actorTier = staffCapabilities.value.tier,
                newTitle = newTitle,
                newTopic = newTopic
            ).onSuccess {
                val trimmed = newTitle.trim()
                _snackbarEvents.tryEmit(if (trimmed.isEmpty()) "Room name deleted" else "Room renamed to \"$trimmed\"")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not edit room")
            }
        }
    }

    fun closeRoomFromLobby(room: ChatRoom) {
        viewModelScope.launch {
            repository.closeRoom(room.id, currentActorName())
                .onSuccess { _snackbarEvents.tryEmit("Closed room \"${room.displayTitle()}\"") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not close room") }
        }
    }

    fun updateProfile(
        displayName: String,
        handle: String,
        bio: String,
        avatarColorHex: String,
        pushToTalkPreferred: Boolean,
        darkThemePreferred: Boolean,
        invisibleModePreferred: Boolean
    ) {
        val allowedInvisible = if (staffCapabilities.value.effectiveCanEnterInvisible) {
            invisibleModePreferred
        } else {
            false
        }
        viewModelScope.launch {
            repository.updateUserProfile(
                displayName = displayName,
                handle = handle,
                bio = bio,
                avatarColorHex = avatarColorHex,
                pushToTalkPreferred = pushToTalkPreferred,
                darkThemePreferred = darkThemePreferred,
                invisibleModePreferred = allowedInvisible
            ).onSuccess {
                _snackbarEvents.tryEmit("Studio profile saved")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update profile")
            }
        }
    }

    fun toggleInvisiblePreference(enabled: Boolean) {
        if (enabled && !staffCapabilities.value.effectiveCanEnterInvisible) {
            _snackbarEvents.tryEmit("Invisible entry requires Admin authorization.")
            return
        }
        viewModelScope.launch {
            repository.setInvisibleModePreferred(enabled)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (enabled) "Ghost Entry ON — you will enter rooms invisibly"
                        else "Ghost Entry OFF — you will enter rooms visibly"
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Ghost Entry")
                }
        }
    }

    // --- ADMIN STAFF, USER & ALERT MANAGEMENT ---

    fun addSupportInvite(email: String, displayName: String, canEnterInvisible: Boolean) {
        viewModelScope.launch {
            repository.addSupportInviteByEmail(
                email = email,
                displayName = displayName,
                canEnterInvisible = canEnterInvisible,
                adminName = currentActorName()
            ).onSuccess {
                _snackbarEvents.tryEmit("Support invite added for $email")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not add support invite")
            }
        }
    }

    fun toggleSupportInviteInvisible(inviteId: String, allow: Boolean) {
        viewModelScope.launch {
            repository.updateSupportInviteInvisible(inviteId, allow)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (allow) "Invisible entry enabled for invite"
                        else "Invisible entry disabled for invite"
                    )
                }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Update failed") }
        }
    }

    fun removeSupportInvite(inviteId: String) {
        viewModelScope.launch {
            repository.removeSupportInvite(inviteId)
                .onSuccess { _snackbarEvents.tryEmit("Support invitation removed") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not remove invite") }
        }
    }

    fun toggleSupportRoleInvisible(targetUserId: String, displayName: String, allow: Boolean) {
        viewModelScope.launch {
            repository.updateSupportInvisiblePermission(targetUserId, allow)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (allow) "Allowed $displayName to enter rooms invisibly"
                        else "Revoked invisible entry for $displayName"
                    )
                }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Update failed") }
        }
    }

    fun setUserSupportRole(
        targetUserId: String,
        targetName: String,
        makeSupport: Boolean,
        canEnterInvisible: Boolean
    ) {
        viewModelScope.launch {
            repository.setUserStaffRole(
                targetUserId = targetUserId,
                targetName = targetName,
                targetEmail = "",
                tier = if (makeSupport) StaffTier.SUPPORT else StaffTier.USER,
                canEnterInvisible = canEnterInvisible,
                adminName = currentActorName()
            ).onSuccess {
                _snackbarEvents.tryEmit(
                    if (makeSupport) "$targetName is now Support Staff"
                    else "Removed Support Staff role from $targetName"
                )
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update staff role")
            }
        }
    }

    fun adminEditUser(
        targetUserId: String,
        displayName: String,
        handle: String,
        bio: String,
        globallyBanned: Boolean,
        disableCreateRoom: Boolean,
        disableEnterRoom: Boolean,
        disableMic: Boolean,
        disableVideo: Boolean,
        suspendedUntilMillis: Long,
        suspensionReason: String
    ) {
        viewModelScope.launch {
            repository.adminEditUserProfileAndRestrictions(
                targetUserId = targetUserId,
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
                actorName = currentActorName()
            ).onSuccess {
                _snackbarEvents.tryEmit("Saved changes for $displayName")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update user")
            }
        }
    }

    fun suspendUser(targetUserId: String, targetName: String, durationMinutes: Long, reason: String) {
        viewModelScope.launch {
            repository.suspendUserForMinutes(
                targetUserId = targetUserId,
                targetName = targetName,
                actorName = currentActorName(),
                actorTier = staffCapabilities.value.tier,
                durationMinutes = durationMinutes,
                reason = reason,
                room = null
            ).onSuccess {
                _snackbarEvents.tryEmit("Suspended $targetName for ${durationMinutes}m")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not suspend user")
            }
        }
    }

    fun sendDirectAlert(
        targetUserId: String,
        targetName: String,
        title: String,
        message: String,
        severity: AlertSeverity
    ) {
        viewModelScope.launch {
            repository.sendUserAlert(
                targetUserId = targetUserId,
                targetName = targetName,
                senderName = currentActorName(),
                senderTier = staffCapabilities.value.tier,
                roomId = "",
                title = title,
                message = message,
                severity = severity
            ).onSuccess {
                _snackbarEvents.tryEmit("Alert sent to $targetName")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send alert")
            }
        }
    }

    fun adminEditAlert(
        alertId: String,
        title: String,
        message: String,
        severity: AlertSeverity,
        reopenForUser: Boolean
    ) {
        viewModelScope.launch {
            repository.adminEditAlert(
                alertId = alertId,
                title = title,
                message = message,
                severity = severity,
                reopenForUser = reopenForUser
            ).onSuccess {
                _snackbarEvents.tryEmit("Alert updated")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not edit alert")
            }
        }
    }

    fun adminDeleteAlert(alertId: String) {
        viewModelScope.launch {
            repository.adminDeleteAlert(alertId)
                .onSuccess { _snackbarEvents.tryEmit("Alert deleted") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not delete alert") }
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            repository.acknowledgeAlert(alertId)
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not acknowledge alert") }
        }
    }

    fun unblockUser(targetUserId: String) {
        val currentBlocked = (userProfileState.value as? UiState.Success)?.data?.blockedUserIds.orEmpty()
        viewModelScope.launch {
            repository.setUserBlocked(
                targetUserId = targetUserId,
                block = false,
                currentBlocked = currentBlocked
            ).onSuccess {
                _snackbarEvents.tryEmit("User unblocked")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not unblock user")
            }
        }
    }

    fun resolveReport(reportId: String, status: ReportStatus, note: String) {
        viewModelScope.launch {
            repository.resolveSafetyReport(
                reportId = reportId,
                newStatus = status,
                resolutionNote = note
            ).onSuccess {
                _snackbarEvents.tryEmit("Report marked as ${status.label}")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update report")
            }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
