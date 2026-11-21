const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_owner_001";
const ADMIN_EMAIL = "daj18801@gmail.com";
const ALICE_UID = "alice_host_123";
const BOB_UID = "bob_guest_456";
const SUPPORT_UID = "support_staff_789";
const SUPPORT_EMAIL = "support@voxstage.test";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
}

function futureDate(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

function validProfilePayload(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    displayName: "Studio Host",
    handle: "studio_host",
    bio: "Live on VoxStage",
    avatarColorHex: "#6C63FF",
    pushToTalkPreferred: true,
    darkThemePreferred: true,
    invisibleModePreferred: false,
    globallyBanned: false,
    disableCreateRoom: false,
    disableEnterRoom: false,
    disableMic: false,
    disableVideo: false,
    suspendedUntilMillis: 0,
    suspensionReason: "",
    blockedUserIds: [],
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validRoomPayload(hostId, overrides = {}) {
  const ts = pastDate(5000);
  return {
    hostId,
    hostName: "Alice",
    hostTier: "USER",
    title: "Late Night Acoustic Jam",
    topic: "Respect everyone on stage.",
    category: "MUSIC",
    coverKey: "MUSIC",
    visibility: "PUBLIC",
    status: "LIVE",
    videoEnabled: true,
    autoModEnabled: true,
    autoModBlockMode: false,
    linksBlocked: true,
    roomLocked: false,
    stageMicLocked: false,
    slowModeSeconds: 3,
    maxSpeakers: 6,
    participantCount: 0,
    moderatorIds: [hostId],
    bannedUserIds: [],
    mutedUserIds: [],
    blockedWords: [],
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validParticipantPayload(uid, roomId, overrides = {}) {
  const ts = pastDate(3000);
  return {
    userId: uid,
    roomId,
    displayName: "Member",
    avatarColorHex: "#00C4B4",
    role: "SPEAKER",
    staffTier: "USER",
    invisible: false,
    micActive: false,
    camActive: false,
    handRaised: false,
    mutedByMod: false,
    speakingLevel: 0.0,
    joinedAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validMessagePayload(roomId, senderId, overrides = {}) {
  const ts = pastDate(2000);
  return {
    roomId,
    senderId,
    senderName: "Bob",
    senderRole: "SPEAKER",
    senderTier: "USER",
    text: "Hello everyone on the stage!",
    messageType: "TEXT",
    flaggedByAutoMod: false,
    deletedByMod: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// 1. UNAUTHENTICATED DENIAL
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauthDb.collection("staffRoles").get());
  await assertFails(unauthDb.collection("userAlerts").get());
});

// 2. USER PROFILES, ADMIN EDIT USER, AND USER CAPABILITY RESTRICTIONS
test("Admin edits users and disables createRoom, enterRoom, mic, video; regular users cannot bypass", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Regular user cannot self-enable invisible mode or lift restrictions
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      invisibleModePreferred: true,
      updatedAt: pastDate(1000),
    })
  );

  // Admin can list users, edit Bob's displayName, and disable room creation / mic / video
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      displayName: "Bob Edited",
      disableCreateRoom: true,
      disableMic: true,
      disableVideo: true,
      updatedAt: pastDate(1000),
    })
  );

  // Bob CANNOT lift his own disableCreateRoom restriction
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      disableCreateRoom: false,
      updatedAt: pastDate(500),
    })
  );

  // Bob CANNOT create a room while disableCreateRoom is true
  await assertFails(
    bobDb.collection("rooms").doc("bob_room").set(validRoomPayload(BOB_UID))
  );

  // Alice creates a room; Bob joins with mic/cam off (succeeds), then tries to enable mic or video (fails!)
  await assertSucceeds(aliceDb.collection("rooms").doc("room_res").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_res").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_res", { micActive: false, camActive: false })
    )
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_res").collection("participants").doc(BOB_UID).update({
      micActive: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_res").collection("participants").doc(BOB_UID).update({
      camActive: true,
      updatedAt: pastDate(500),
    })
  );

  // Admin disables enterRoom for Bob -> Bob cannot join another room
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      disableEnterRoom: true,
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(aliceDb.collection("rooms").doc("room_res_2").set(validRoomPayload(ALICE_UID)));
  await assertFails(
    bobDb.collection("rooms").doc("room_res_2").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_res_2")
    )
  );
});

// 3. SUPPORT STAFF PERMISSIONS: TIMED SUSPENSION, CONDITIONAL INVISIBLE ENTRY, NO BAN/RENAME/CLOSE
test("Support staff can bounce/kick/mute/suspend and enter invisible ONLY when Admin allows it", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();

  const ts = pastDate(4000);

  // Spoofed unverified admin email rejected
  await assertFails(
    spoofedAdminDb.collection("staffInvites").doc("inv_bad").set({
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: true,
      addedByUid: "spoof_uid",
      addedByName: "Spoofer",
      createdAt: ts,
    })
  );

  // Admin adds Support WITHOUT invisible permission initially
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: false,
      addedByUid: ADMIN_UID,
      addedByName: "Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("rooms").doc("room_sup").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_sup").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_sup")
    )
  );

  // Support CANNOT enter invisible while canEnterInvisible is false
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").collection("participants").doc(SUPPORT_UID).set(
      validParticipantPayload(SUPPORT_UID, "room_sup", {
        role: "LISTENER",
        staffTier: "SUPPORT",
        invisible: true,
      })
    )
  );

  // Admin grants canEnterInvisible = true to Support -> NOW Support enters invisibly!
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).update({
      canEnterInvisible: true,
      updatedAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_sup").collection("participants").doc(SUPPORT_UID).set(
      validParticipantPayload(SUPPORT_UID, "room_sup", {
        role: "LISTENER",
        staffTier: "SUPPORT",
        invisible: true,
      })
    )
  );

  // Support CAN mute, bounce/kick Bob, and suspend Bob for 15 minutes
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_sup").update({
      mutedUserIds: [BOB_UID],
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_sup").collection("participants").doc(BOB_UID).delete()
  );
  await assertSucceeds(
    supportDb.collection("users").doc(BOB_UID).update({
      suspendedUntilMillis: Date.now() + 15 * 60 * 1000,
      suspensionReason: "15m cooldown by Support",
      updatedAt: pastDate(900),
    })
  );

  // Suspended Bob CANNOT re-enter room or send messages
  await assertFails(
    bobDb.collection("rooms").doc("room_sup").collection("participants").doc(BOB_UID).set(
      validParticipantPayload(BOB_UID, "room_sup")
    )
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_sup").collection("messages").doc("msg_susp").set(
      validMessagePayload("room_sup", BOB_UID)
    )
  );

  // Support CANNOT rename room, close room, or ban user in Alice's room (Admin-only)
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").update({
      title: "Support Renamed",
      updatedAt: pastDate(800),
    })
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").update({
      status: "CLOSED",
      updatedAt: pastDate(800),
    })
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_sup").update({
      bannedUserIds: [BOB_UID],
      updatedAt: pastDate(800),
    })
  );
});

// 4. ADMIN RENAMES ROOMS, BANS USERS, CLOSES ROOMS, AND ENTERS LOCKED ROOMS INVISIBLY
test("Admin renames room, bans user, closes room, and enters locked room invisibly", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_admin").set(
      validRoomPayload(ALICE_UID, { roomLocked: true })
    )
  );

  // Admin enters locked room invisibly
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_admin").collection("participants").doc(ADMIN_UID).set(
      validParticipantPayload(ADMIN_UID, "room_admin", {
        role: "LISTENER",
        staffTier: "ADMIN",
        invisible: true,
      })
    )
  );

  // Admin edits room name & bans Bob
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_admin").update({
      title: "Renamed By Administrator",
      bannedUserIds: [BOB_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Admin closes room
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_admin").update({
      status: "CLOSED",
      updatedAt: pastDate(500),
    })
  );

  // Nobody can send messages into closed room
  await assertFails(
    aliceDb.collection("rooms").doc("room_admin").collection("messages").doc("msg_closed").set(
      validMessagePayload("room_admin", ALICE_UID)
    )
  );
});

// 5. USER ALERTS — SEND, ADMIN EDIT, ADMIN DELETE, RECIPIENT ACKNOWLEDGE
test("UserAlerts: Admin sends, edits, and deletes alerts; recipient acknowledges; regular user blocked", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const ts = pastDate(3000);
  const alertPayload = {
    targetUserId: BOB_UID,
    targetName: "Bob",
    senderId: ADMIN_UID,
    senderName: "Administrator",
    senderTier: "ADMIN",
    roomId: "room_1",
    title: "Official Conduct Warning",
    message: "Please keep voice chat respectful.",
    severity: "WARNING",
    acknowledged: false,
    createdAt: ts,
    updatedAt: ts,
  };

  // Regular user cannot send alerts
  await assertFails(
    aliceDb.collection("userAlerts").doc("alert_bad").set({
      ...alertPayload,
      senderId: ALICE_UID,
      senderName: "Alice",
    })
  );

  // Admin sends alert, then EDITS alert title/message/severity
  await assertSucceeds(adminDb.collection("userAlerts").doc("alert_1").set(alertPayload));
  await assertSucceeds(
    adminDb.collection("userAlerts").doc("alert_1").update({
      title: "Final Urgent Notice",
      message: "Updated warning from Admin.",
      severity: "URGENT",
      updatedAt: pastDate(1000),
    })
  );

  // Bob reads and acknowledges, cannot edit alert text
  await assertSucceeds(bobDb.collection("userAlerts").where("targetUserId", "==", BOB_UID).get());
  await assertFails(
    bobDb.collection("userAlerts").doc("alert_1").update({
      message: "Tampered",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    bobDb.collection("userAlerts").doc("alert_1").update({
      acknowledged: true,
      updatedAt: pastDate(500),
    })
  );

  // Admin can delete alert
  await assertSucceeds(adminDb.collection("userAlerts").doc("alert_1").delete());
});

// 6. ROOM ADMIN, CO-ADMIN, EXCLUSIVE APP ADMIN MIC PRIORITY & INVISIBLE MIC RULES
test("Room Admin & Co-Admin permissions, exclusive Owner Admin mic override, and Support visible-only mic rule", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const supportDb = testEnv
    .authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true })
    .firestore();
  const guestDb = testEnv.authenticatedContext("guest_user_999").firestore();

  const ts = pastDate(4000);
  // Grant Support with canEnterInvisible = true
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "Sam Support",
      staffTier: "SUPPORT",
      canEnterInvisible: true,
      addedByUid: ADMIN_UID,
      addedByName: "Owner Admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice creates room -> Alice is Room Admin; Alice adds Bob as Co-Admin, clears & edits room name
  await assertSucceeds(aliceDb.collection("rooms").doc("room_hier").set(validRoomPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").update({
      moderatorIds: [ALICE_UID, BOB_UID],
      title: "",
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").update({
      title: "Alice's New Room Name",
      updatedAt: pastDate(1800),
    })
  );

  // Guest joins room; Co-Admin Bob CAN mute and kick Guest, but CANNOT ban Guest or rename room!
  await assertSucceeds(
    guestDb.collection("rooms").doc("room_hier").collection("participants").doc("guest_user_999").set(
      validParticipantPayload("guest_user_999", "room_hier")
    )
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_hier").update({
      mutedUserIds: ["guest_user_999"],
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_hier").update({
      bannedUserIds: ["guest_user_999"],
      updatedAt: pastDate(1400),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_hier").update({
      title: "Co-Admin Rename Blocked",
      updatedAt: pastDate(1400),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_hier").collection("participants").doc("guest_user_999").delete()
  );

  // Support enters invisible -> CANNOT turn on mic while invisible; CAN turn on mic once visible!
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).set(
      validParticipantPayload(SUPPORT_UID, "room_hier", {
        role: "LISTENER",
        staffTier: "SUPPORT",
        invisible: true,
        micActive: false,
      })
    )
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).update({
      micActive: true,
      updatedAt: pastDate(1200),
    })
  );
  await assertSucceeds(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).update({
      invisible: false,
      micActive: true,
      updatedAt: pastDate(1100),
    })
  );

  // Owner App Admin enters INVISIBLY and takes EXCLUSIVE mic control (micActive = true while invisible = true!)
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_hier").collection("participants").doc(ADMIN_UID).set(
      validParticipantPayload(ADMIN_UID, "room_hier", {
        role: "LISTENER",
        staffTier: "ADMIN",
        invisible: true,
        micActive: true,
      })
    )
  );
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_hier").update({
      adminMicPriorityActive: true,
      updatedAt: pastDate(900),
    })
  );

  // While Owner Admin is on the mic, NOBODY ELSE (not even Room Admin Alice or Support) can enable their mic!
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").collection("participants").doc(ALICE_UID).set(
      validParticipantPayload(ALICE_UID, "room_hier", { role: "HOST", micActive: false })
    )
  );
  await assertFails(
    aliceDb.collection("rooms").doc("room_hier").collection("participants").doc(ALICE_UID).update({
      micActive: true,
      updatedAt: pastDate(700),
    })
  );
  await assertFails(
    supportDb.collection("rooms").doc("room_hier").collection("participants").doc(SUPPORT_UID).update({
      micActive: true,
      updatedAt: pastDate(700),
    })
  );

  // When Owner Admin releases mic priority, Alice can turn on her mic again!
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_hier").update({
      adminMicPriorityActive: false,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_hier").collection("participants").doc(ALICE_UID).update({
      micActive: true,
      updatedAt: pastDate(300),
    })
  );
});

