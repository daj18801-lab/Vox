package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutFromVoxStage
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.VoxStageRepository
import com.example.ui.screens.IncomingAlertModalDialog
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoomScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.theme.VoxStageTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RoomViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DISCOVER = "discover"
const val ROUTE_SAFETY = "safety"
const val ROUTE_PROFILE = "profile"
const val ROUTE_ROOM = "room/{roomId}"

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(ROUTE_DISCOVER, "Rooms", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq, "nav_discover"),
    TopDestination(ROUTE_SAFETY, "Safety", Icons.Rounded.Shield, Icons.Outlined.Shield, "nav_safety"),
    TopDestination(ROUTE_PROFILE, "Studio", Icons.Rounded.Tune, Icons.Outlined.Tune, "nav_profile")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxStageRoot()
        }
    }
}

@Composable
fun VoxStageRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        VoxStageTheme(darkTheme = true) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedVoxStageApp(
                currentUserId = uid,
                initialDisplayName = user.displayName ?: "Studio Host",
                initialEmail = user.email ?: "",
                initialEmailVerified = user.isEmailVerified
            )
        }
    }
}

@Composable
private fun AuthenticatedVoxStageApp(
    currentUserId: String,
    initialDisplayName: String,
    initialEmail: String,
    initialEmailVerified: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(
                    repository = VoxStageRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                    initialEmail = initialEmail,
                    initialEmailVerified = initialEmailVerified
                )
            }
        }
    )

    val staffCapabilities by mainViewModel.staffCapabilities.collectAsStateWithLifecycle()
    val userProfileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val publicRoomsState by mainViewModel.publicRoomsState.collectAsStateWithLifecycle()
    val myRoomsState by mainViewModel.myRoomsState.collectAsStateWithLifecycle()
    val safetyReportsState by mainViewModel.safetyReportsState.collectAsStateWithLifecycle()
    val unreadAlerts by mainViewModel.unreadAlertsState.collectAsStateWithLifecycle()
    val staffRoles by mainViewModel.staffRolesState.collectAsStateWithLifecycle()
    val staffInvites by mainViewModel.staffInvitesState.collectAsStateWithLifecycle()
    val allUsers by mainViewModel.allUsersState.collectAsStateWithLifecycle()
    val allAlerts by mainViewModel.allAlertsState.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by mainViewModel.selectedCategory.collectAsStateWithLifecycle()
    val showOnlyMyRooms by mainViewModel.showOnlyMyRooms.collectAsStateWithLifecycle()
    val isCreatingRoom by mainViewModel.isCreatingRoom.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val darkTheme = userProfile?.darkThemePreferred ?: true

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopNav = currentRoute in listOf(ROUTE_DISCOVER, ROUTE_SAFETY, ROUTE_PROFILE)

    LaunchedEffect(Unit) {
        mainViewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    VoxStageTheme(darkTheme = darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showTopNav && !useRail) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(dest.route) {
                                                popUpTo(ROUTE_DISCOVER) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if (showTopNav) innerPadding.calculateTopPadding() else 0.dp,
                            bottom = if (showTopNav && !useRail) innerPadding.calculateBottomPadding() else 0.dp
                        )
                ) {
                    if (showTopNav && useRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(dest.route) {
                                                popUpTo(ROUTE_DISCOVER) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DISCOVER
                        ) {
                            composable(ROUTE_DISCOVER) {
                                LobbyScreen(
                                    publicRoomsState = publicRoomsState,
                                    myRoomsState = myRoomsState,
                                    userProfile = userProfile,
                                    staffCapabilities = staffCapabilities,
                                    searchQuery = searchQuery,
                                    selectedCategory = selectedCategory,
                                    showOnlyMyRooms = showOnlyMyRooms,
                                    isCreatingRoom = isCreatingRoom,
                                    currentUserId = currentUserId,
                                    onSearchChange = mainViewModel::updateSearchQuery,
                                    onCategorySelected = mainViewModel::selectCategory,
                                    onToggleMyRooms = mainViewModel::setShowOnlyMyRooms,
                                    onToggleInvisiblePreference = mainViewModel::toggleInvisiblePreference,
                                    onOpenRoom = { roomId ->
                                        if (mainViewModel.canEnterRoomOrNotify()) {
                                            navController.navigate("room/$roomId")
                                        }
                                    },
                                    onEditRoom = mainViewModel::editRoomDetails,
                                    onCloseRoom = mainViewModel::closeRoomFromLobby,
                                    onCreateRoom = { title, topic, category, coverKey, visibility, videoEnabled, autoModEnabled, autoModBlockMode, linksBlocked, slowModeSeconds, maxSpeakers ->
                                        mainViewModel.createRoom(
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
                                            maxSpeakers = maxSpeakers,
                                            onCreated = { newRoomId ->
                                                navController.navigate("room/$newRoomId")
                                            }
                                        )
                                    }
                                )
                            }

                            composable(ROUTE_SAFETY) {
                                SafetyScreen(
                                    reportsState = safetyReportsState,
                                    userProfile = userProfile,
                                    currentUserId = currentUserId,
                                    staffCapabilities = staffCapabilities,
                                    staffRoles = staffRoles,
                                    staffInvites = staffInvites,
                                    allUsers = allUsers,
                                    allAlerts = allAlerts,
                                    onResolveReport = mainViewModel::resolveReport,
                                    onUnblockUser = mainViewModel::unblockUser,
                                    onAddSupportInvite = mainViewModel::addSupportInvite,
                                    onToggleSupportInviteInvisible = mainViewModel::toggleSupportInviteInvisible,
                                    onRemoveSupportInvite = mainViewModel::removeSupportInvite,
                                    onToggleSupportRoleInvisible = mainViewModel::toggleSupportRoleInvisible,
                                    onSetUserSupportRole = mainViewModel::setUserSupportRole,
                                    onAdminEditUser = mainViewModel::adminEditUser,
                                    onSuspendUser = mainViewModel::suspendUser,
                                    onSendDirectAlert = mainViewModel::sendDirectAlert,
                                    onAdminEditAlert = mainViewModel::adminEditAlert,
                                    onAdminDeleteAlert = mainViewModel::adminDeleteAlert
                                )
                            }

                            composable(ROUTE_PROFILE) {
                                ProfileScreen(
                                    userProfile = userProfile,
                                    userEmail = initialEmail,
                                    staffCapabilities = staffCapabilities,
                                    onSaveProfile = mainViewModel::updateProfile,
                                    onSignOut = {
                                        signOutFromVoxStage(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    }
                                )
                            }

                            composable(
                                route = ROUTE_ROOM,
                                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                            ) { entry ->
                                val roomId = entry.arguments?.getString("roomId") ?: return@composable
                                val roomViewModel: RoomViewModel = viewModel(
                                    key = "${currentUserId}_$roomId",
                                    factory = viewModelFactory {
                                        initializer {
                                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                                "APPLICATION_KEY missing from CreationExtras"
                                            }
                                            val databaseId = app.getString(R.string.firestore_database_id)
                                            val db = FirebaseFirestore.getInstance(databaseId)
                                            RoomViewModel(
                                                repository = VoxStageRepository(db),
                                                currentUserId = currentUserId,
                                                roomId = roomId,
                                                initialStaffCapabilities = staffCapabilities
                                            )
                                        }
                                    }
                                )
                                val effectiveProfile = userProfile ?: UserProfile(
                                    userId = currentUserId,
                                    displayName = initialDisplayName.ifBlank { "Studio Host" },
                                    handle = "studio"
                                )
                                RoomScreen(
                                    viewModel = roomViewModel,
                                    userProfile = effectiveProfile,
                                    staffCapabilities = staffCapabilities,
                                    onExitRoom = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }

            // Global real-time Admin / Support Alert Modal
            unreadAlerts.firstOrNull()?.let { latestAlert ->
                IncomingAlertModalDialog(
                    alert = latestAlert,
                    onAcknowledge = { mainViewModel.acknowledgeAlert(latestAlert.id) }
                )
            }
        }
    }
}
