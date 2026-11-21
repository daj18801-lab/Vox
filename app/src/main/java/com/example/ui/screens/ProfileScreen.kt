package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.parseHexColor
import com.example.ui.components.usernameColor
import com.example.ui.theme.AvatarAccentHexPalette
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@Composable
fun ProfileScreen(
    userProfile: UserProfile?,
    userEmail: String,
    staffCapabilities: StaffCapabilities,
    onSaveProfile: (
        displayName: String,
        handle: String,
        bio: String,
        avatarColorHex: String,
        pushToTalkPreferred: Boolean,
        darkThemePreferred: Boolean,
        invisibleModePreferred: Boolean
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    var displayName by remember { mutableStateOf(userProfile?.displayName ?: "") }
    var handle by remember { mutableStateOf(userProfile?.handle ?: "") }
    var bio by remember { mutableStateOf(userProfile?.bio ?: "") }
    var avatarColorHex by remember { mutableStateOf(userProfile?.avatarColorHex ?: "#6C63FF") }
    var pushToTalkPreferred by remember { mutableStateOf(userProfile?.pushToTalkPreferred ?: true) }
    var darkThemePreferred by remember { mutableStateOf(userProfile?.darkThemePreferred ?: true) }
    var invisibleModePreferred by remember { mutableStateOf(userProfile?.invisibleModePreferred ?: false) }

    LaunchedEffect(userProfile?.userId, userProfile?.updatedAt) {
        if (userProfile != null) {
            displayName = userProfile.displayName
            handle = userProfile.handle
            bio = userProfile.bio
            avatarColorHex = userProfile.avatarColorHex
            pushToTalkPreferred = userProfile.pushToTalkPreferred
            darkThemePreferred = userProfile.darkThemePreferred
            invisibleModePreferred = userProfile.invisibleModePreferred
        }
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var camGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> camGranted = granted }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Broadcast Studio",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Identity Preview Header with Role Color (Blue / Green / Red)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeakingAvatarCircle(
                        displayName = displayName.ifBlank { "Host" },
                        colorHex = avatarColorHex,
                        speakingLevel = 0.45f,
                        micActive = true,
                        size = 64.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = displayName.ifBlank { "Studio Host" },
                                style = MaterialTheme.typography.titleLarge,
                                color = usernameColor(staffCapabilities.tier)
                            )
                            StaffTierBadge(tier = staffCapabilities.tier)
                        }
                        Text(
                            text = "@${handle.ifBlank { "studio" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = VoiceCyan
                        )
                        if (userEmail.isNotBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Account Restrictions Notice (if any)
            if (!staffCapabilities.isAdmin && userProfile != null) {
                val activeRestrictions = buildList {
                    if (userProfile.globallyBanned) add("Account Banned")
                    if (userProfile.currentlySuspended()) add("Suspended (${userProfile.remainingSuspensionLabel()} left)")
                    if (userProfile.disableCreateRoom) add("Creating Rooms Disabled")
                    if (userProfile.disableEnterRoom) add("Entering Rooms Disabled")
                    if (userProfile.disableMic) add("Microphone Disabled")
                    if (userProfile.disableVideo) add("Video Streaming Disabled")
                }
                if (activeRestrictions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LiveCoral.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Active Account Restrictions",
                                style = MaterialTheme.typography.titleSmall,
                                color = LiveCoral
                            )
                            Text(
                                text = activeRestrictions.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Support Staff Invisible Permission Indicator
            if (staffCapabilities.isSupport && !staffCapabilities.canEnterInvisible) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = usernameColor(StaffTier.SUPPORT).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, usernameColor(StaffTier.SUPPORT).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Invisible mode status",
                            tint = usernameColor(StaffTier.SUPPORT)
                        )
                        Text(
                            text = "Invisible room entry is currently off for your Support account. An Administrator can enable it for you anytime.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Hardware Readiness Check
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Audio & Video Readiness",
                        style = MaterialTheme.typography.titleMedium
                    )
                    HardwarePermissionRow(
                        icon = Icons.Rounded.Mic,
                        title = "Microphone (Voice Stage)",
                        granted = micGranted,
                        onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                    HardwarePermissionRow(
                        icon = Icons.Rounded.Videocam,
                        title = "Camera (Live Video Stream)",
                        granted = camGranted,
                        onRequest = { camLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }

            // Editable Profile Settings
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Stage Profile & Preferences",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(40) },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it.replace(" ", "_").take(30) },
                        label = { Text("Handle") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_handle_input")
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(200) },
                        label = { Text("Bio") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_bio_input")
                    )

                    Text("Avatar Ring Color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AvatarAccentHexPalette.forEach { hex ->
                            val swatch = parseHexColor(hex)
                            val selected = avatarColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { avatarColorHex = hex }
                            )
                        }
                    }

                    if (staffCapabilities.effectiveCanEnterInvisible) {
                        ToggleSettingRow(
                            title = "Default to Ghost Entry (Invisible Mode)",
                            subtitle = if (staffCapabilities.isAdmin) {
                                "Enter rooms unseen — Main Admin can still use the mic while invisible"
                            } else {
                                "Enter rooms unseen — switch to Visible inside a room to use the mic"
                            },
                            checked = invisibleModePreferred,
                            onCheckedChange = { invisibleModePreferred = it }
                        )
                    }

                    ToggleSettingRow(
                        title = "Default to Push-to-Talk",
                        subtitle = "Hold button to transmit voice on stage (classic Paltalk style)",
                        checked = pushToTalkPreferred,
                        onCheckedChange = { pushToTalkPreferred = it }
                    )

                    ToggleSettingRow(
                        title = "Studio Dark Theme",
                        subtitle = "High-contrast broadcast lounge palette",
                        checked = darkThemePreferred,
                        onCheckedChange = { darkThemePreferred = it }
                    )

                    Button(
                        onClick = {
                            onSaveProfile(
                                displayName,
                                handle,
                                bio,
                                avatarColorHex,
                                pushToTalkPreferred,
                                darkThemePreferred,
                                invisibleModePreferred
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_profile_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Studio Preferences")
                    }
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Sign out",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun HardwarePermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) SafetyEmerald else ModAmber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (granted) "Ready for live stage" else "Permission needed before going live",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = SafetyEmerald,
                modifier = Modifier.size(22.dp)
            )
        } else {
            OutlinedButton(onClick = onRequest) {
                Text("Enable")
            }
        }
    }
}
