"use strict";

const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");
const {setGlobalOptions} = require("firebase-functions/v2");
const {HttpsError, onCall} = require("firebase-functions/v2/https");
const {
  onDocumentCreated,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");

initializeApp();
setGlobalOptions({
  region: "asia-northeast3",
  maxInstances: 10,
  concurrency: 20,
  memory: "256MiB",
  timeoutSeconds: 60,
});

const db = getFirestore();
const messaging = getMessaging();
const adminAuth = getAuth();
const CHILDREN_COLLECTION = "screenrest_children";
const TOKEN_COLLECTION = "push_tokens";
const REQUEST_STATUS_PENDING = "Pending";
const RESOLVED_STATUSES = new Set(["Approved", "Rejected"]);
const INVALID_TOKEN_CODES = new Set([
  "messaging/invalid-registration-token",
  "messaging/registration-token-not-registered",
]);
const REQUEST_DOCUMENT_PATH =
  `${CHILDREN_COLLECTION}/{childDeviceId}/unlock_requests/{requestId}`;
const IMMEDIATE_BLOCK_DOCUMENT_PATH =
  `${CHILDREN_COLLECTION}/{childDeviceId}/immediate_block/current`;
const USAGE_REFRESH_DOCUMENT_PATH =
  `${CHILDREN_COLLECTION}/{childDeviceId}/usage_refresh/current`;

exports.processCreatedChildUsageRefreshPush = onDocumentCreated(
    USAGE_REFRESH_DOCUMENT_PATH,
    async (event) => {
      if (event.data) await processChildUsageRefreshChange(null, event.data.data(), event.params);
    },
);

exports.processUpdatedChildUsageRefreshPush = onDocumentUpdated(
    USAGE_REFRESH_DOCUMENT_PATH,
    async (event) => {
      if (event.data) {
        await processChildUsageRefreshChange(
            event.data.before.data(), event.data.after.data(), event.params,
        );
      }
    },
);

async function processChildUsageRefreshChange(before, after, params) {
  if (!after || after.childDeviceId !== params.childDeviceId ||
      typeof after.requestId !== "string" || !after.requestId ||
      Number(after.completedAtMillis || 0) !== 0 ||
      (before && before.requestId === after.requestId)) return;
  const remaining = Number(after.expiresAtMillis || 0) - Date.now();
  if (remaining <= 0) return;
  const childSnapshot = await db.collection(CHILDREN_COLLECTION)
      .doc(params.childDeviceId).get();
  if (!childSnapshot.exists || childSnapshot.get("status") === "unlinked") return;
  const parentUids = stringSet(childSnapshot.get("parentUids"));
  if (!parentUids.has(after.parentUid)) return;
  const childUid = childSnapshot.get("childUid");
  if (typeof childUid !== "string" || !childUid) return;
  await sendToLinkedDevices({
    childDeviceId: params.childDeviceId,
    role: "child",
    allowedUids: new Set([childUid]),
    data: {
      type: "child_usage_refresh_requested",
      childDeviceId: params.childDeviceId,
      requestId: after.requestId,
      eventKey: `usage-refresh:${params.childDeviceId}:${after.requestId}`,
    },
    collapseKey: `usage-refresh-${params.childDeviceId}`,
    ttlMillis: Math.min(remaining, 30 * 60 * 1000),
    androidPriority: "normal",
  });
}

exports.processCreatedImmediateBlockPush = onDocumentCreated(
    IMMEDIATE_BLOCK_DOCUMENT_PATH,
    async (event) => {
      if (event.data) await processImmediateBlockChange(null, event.data.data(), event.params);
    },
);

exports.processUpdatedImmediateBlockPush = onDocumentUpdated(
    IMMEDIATE_BLOCK_DOCUMENT_PATH,
    async (event) => {
      if (event.data) {
        await processImmediateBlockChange(
            event.data.before.data(), event.data.after.data(), event.params,
        );
      }
    },
);

async function processImmediateBlockChange(before, after, params) {
  if (!after || after.childDeviceId !== params.childDeviceId ||
      typeof after.requestId !== "string") return;
  if (before && before.requestId === after.requestId &&
      before.revokedAtMillis === after.revokedAtMillis) return;
  const childSnapshot = await db.collection(CHILDREN_COLLECTION)
      .doc(params.childDeviceId).get();
  if (!childSnapshot.exists || childSnapshot.get("status") === "unlinked") return;
  const childUid = childSnapshot.get("childUid");
  if (typeof childUid !== "string" || !childUid) return;
  const revoked = Number(after.revokedAtMillis || 0) > 0;
  const remaining = Number(after.expiresAtMillis || 0) - Date.now();
  if (!revoked && remaining <= 0) return;
  await sendToLinkedDevices({
    childDeviceId: params.childDeviceId,
    role: "child",
    allowedUids: new Set([childUid]),
    data: {
      type: "immediate_block_changed",
      childDeviceId: params.childDeviceId,
      requestId: after.requestId,
      eventKey: `immediate:${params.childDeviceId}:${after.requestId}:${after.revokedAtMillis}`,
    },
    collapseKey: `immediate-${params.childDeviceId}`,
    ttlMillis: revoked ? 60 * 60 * 1000 : Math.min(remaining, 24 * 60 * 60 * 1000),
  });
}

exports.processCreatedUnlockRequestPush = onDocumentCreated(
    REQUEST_DOCUMENT_PATH,
    async (event) => {
      if (!event.data) return;
      await processUnlockRequestChange(null, event.data.data(), event.params);
    },
);

exports.processUpdatedUnlockRequestPush = onDocumentUpdated(
    REQUEST_DOCUMENT_PATH,
    async (event) => {
      if (!event.data) return;
      await processUnlockRequestChange(
          event.data.before.data(),
          event.data.after.data(),
          event.params,
      );
    },
);

exports.cleanupDuplicateChildLinks = onCall(
    // RELEASE_BLOCKER(screenrest): Development-only exception. Before a production
    // release, set this and deleteCurrentUserData to enforceAppCheck: true, deploy
    // both functions, and verify valid Play Integrity requests in App Check metrics.
    {enforceAppCheck: false},
    async (request) => {
      const uid = request.auth?.uid;
      if (!uid) {
        throw new HttpsError("unauthenticated", "Authentication is required.");
      }

      const currentChildDeviceId =
        typeof request.data?.currentChildDeviceId === "string" ?
          request.data.currentChildDeviceId.trim() : "";
      if (!currentChildDeviceId || currentChildDeviceId.length > 128) {
        throw new HttpsError("invalid-argument", "A valid child device id is required.");
      }

      const currentReference = db.collection(CHILDREN_COLLECTION)
          .doc(currentChildDeviceId);
      const currentSnapshot = await currentReference.get();
      if (!currentSnapshot.exists) {
        throw new HttpsError("not-found", "The current child device was not found.");
      }
      const currentParentUids = stringSet(currentSnapshot.get("parentUids"));
      if (!currentParentUids.has(uid)) {
        throw new HttpsError("permission-denied", "The parent is not linked to this child.");
      }
      const stableDeviceKey = currentSnapshot.get("stableDeviceKey");
      if (typeof stableDeviceKey !== "string" ||
          !/^[0-9a-f]{64}$/.test(stableDeviceKey)) {
        return {deletedCount: 0, unlinkedCount: 0, skipped: "missing-device-key"};
      }

      const linkedChildren = await db.collection(CHILDREN_COLLECTION)
          .where("parentUids", "array-contains", uid)
          .get();
      const duplicates = linkedChildren.docs.filter((document) =>
        document.id !== currentChildDeviceId &&
        document.get("stableDeviceKey") === stableDeviceKey,
      );

      let deletedCount = 0;
      let unlinkedCount = 0;
      for (const duplicate of duplicates) {
        const duplicateParentUids = stringSet(duplicate.get("parentUids"));
        if (duplicateParentUids.size === 1 && duplicateParentUids.has(uid)) {
          const pairingCodes = await db.collection("screenrest_pairing_codes")
              .where("childDeviceId", "==", duplicate.id)
              .get();
          await deleteReferences(pairingCodes.docs.map((document) => document.ref));
          await db.recursiveDelete(duplicate.ref);
          deletedCount += 1;
        } else {
          await removeParentIdentityFromChild(duplicate.ref, uid);
          unlinkedCount += 1;
        }
      }

      return {deletedCount, unlinkedCount};
    },
);

exports.deleteCurrentUserData = onCall(
    // RELEASE_BLOCKER(screenrest): Do not ship while App Check enforcement is off.
    // Re-enable it together with cleanupDuplicateChildLinks and consider token
    // consumption after the release client requests limited-use App Check tokens.
    // Development builds are repeatedly reinstalled, which rotates App Check debug tokens.
    // Authentication remains mandatory and the function only deletes request.auth.uid.
    {enforceAppCheck: false},
    async (request) => {
      const uid = request.auth?.uid;
      if (!uid) {
        throw new HttpsError("unauthenticated", "Authentication is required.");
      }

      try {
        const [ownedChildren, linkedChildren, ownedPairingCodes, usedPairingCodes] =
          await Promise.all([
            db.collection(CHILDREN_COLLECTION).where("childUid", "==", uid).get(),
            db.collection(CHILDREN_COLLECTION)
                .where("parentUids", "array-contains", uid)
                .get(),
            db.collection("screenrest_pairing_codes").where("childUid", "==", uid).get(),
            db.collection("screenrest_pairing_codes").where("parentUid", "==", uid).get(),
          ]);

        const ownedChildIds = new Set(ownedChildren.docs.map((document) => document.id));
        for (const childDocument of linkedChildren.docs) {
          if (!ownedChildIds.has(childDocument.id)) {
            await removeParentIdentityFromChild(childDocument.ref, uid);
          }
        }

        for (const childDocument of ownedChildren.docs) {
          await db.recursiveDelete(childDocument.ref);
        }

        const pairingReferences = new Map();
        [...ownedPairingCodes.docs, ...usedPairingCodes.docs].forEach((document) => {
          pairingReferences.set(document.ref.path, document.ref);
        });
        await deleteReferences([...pairingReferences.values()]);

        await adminAuth.deleteUser(uid);
        return {
          deleted: true,
          ownedChildCount: ownedChildren.size,
          unlinkedChildCount: linkedChildren.docs
              .filter((document) => !ownedChildIds.has(document.id)).length,
        };
      } catch (error) {
        console.error("Account deletion failed", {
          code: error?.code || "unknown",
          name: error?.name || "Error",
        });
        throw new HttpsError("internal", "Account data could not be deleted.");
      }
    },
);

async function removeParentIdentityFromChild(childReference, uid) {
  const tokenSnapshot = await childReference.collection(TOKEN_COLLECTION)
      .where("uid", "==", uid)
      .get();
  await deleteReferences(tokenSnapshot.docs.map((document) => document.ref));

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(childReference);
    if (!snapshot.exists) return;
    const data = snapshot.data() || {};
    const parentUids = stringSet(data.parentUids);
    parentUids.delete(uid);
    const remainingParentUids = [...parentUids];
    const linkedParents = Array.isArray(data.linkedParents) ?
      data.linkedParents.filter((parent) => parent?.parentUid !== uid) : [];
    const now = Date.now();
    transaction.set(childReference, {
      parentUids: remainingParentUids,
      linkedParents,
      status: remainingParentUids.length === 0 ? "unlinked" : "active",
      lastUnlinkedParentUid: uid,
      remainingParentCount: remainingParentUids.length,
      unlinkedAtMillis: now,
      updatedAtMillis: now,
    }, {merge: true});
  });
}

async function processUnlockRequestChange(before, after, params) {
  const childDeviceId = params.childDeviceId;
  const requestId = params.requestId;
  if (
    after.childDeviceId !== childDeviceId ||
    after.id !== requestId ||
    typeof after.childUid !== "string" ||
    after.childUid.length === 0
  ) {
    console.warn("Ignored malformed unlock request", {childDeviceId, requestId});
    return;
  }

  const pendingWasCreatedOrRefreshed =
    after.status === REQUEST_STATUS_PENDING &&
    Number(after.expiresAtMillis || 0) > Date.now() &&
    (
      before === null ||
      before.status !== REQUEST_STATUS_PENDING ||
      Number(after.createdAtMillis || 0) > Number(before.createdAtMillis || 0)
    );
  const wasResolved =
    before !== null &&
    before.status === REQUEST_STATUS_PENDING &&
    RESOLVED_STATUSES.has(after.status);
  if (!pendingWasCreatedOrRefreshed && !wasResolved) return;

  const childSnapshot = await db.collection(CHILDREN_COLLECTION)
      .doc(childDeviceId)
      .get();
  if (!childSnapshot.exists || childSnapshot.get("childUid") !== after.childUid) return;

  if (pendingWasCreatedOrRefreshed) {
    if (childSnapshot.get("status") === "unlinked") return;
    const parentUids = stringSet(childSnapshot.get("parentUids"));
    if (parentUids.size === 0) return;
    await sendToLinkedDevices({
      childDeviceId,
      role: "parent",
      allowedUids: parentUids,
      data: {
        type: "unlock_request_created",
        childDeviceId,
        requestId,
        eventKey: `request-created:${childDeviceId}:${requestId}:${after.createdAtMillis}`,
      },
      collapseKey: `request-${requestId}`,
      ttlMillis: 10 * 60 * 1000,
    });
    return;
  }

  const childUid = childSnapshot.get("childUid");
  await sendToLinkedDevices({
    childDeviceId,
    role: "child",
    allowedUids: new Set([childUid]),
    data: {
      type: "unlock_request_resolved",
      childDeviceId,
      requestId,
      status: after.status,
      eventKey: `request-resolved:${childDeviceId}:${requestId}:${after.status}`,
    },
    collapseKey: `request-${requestId}`,
    ttlMillis: 60 * 60 * 1000,
  });

  const parentUids = stringSet(childSnapshot.get("parentUids"));
  if (parentUids.size > 0) {
    await sendToLinkedDevices({
      childDeviceId,
      role: "parent",
      allowedUids: parentUids,
      data: {
        type: "unlock_request_resolved",
        childDeviceId,
        requestId,
        status: after.status,
        eventKey: `request-parent-cleanup:${childDeviceId}:${requestId}:${after.status}`,
      },
      collapseKey: `request-${requestId}`,
      ttlMillis: 60 * 60 * 1000,
    });
  }
}

async function sendToLinkedDevices({
  childDeviceId,
  role,
  allowedUids,
  data,
  collapseKey,
  ttlMillis,
  androidPriority = "high",
}) {
  const tokenSnapshot = await db.collection(CHILDREN_COLLECTION)
      .doc(childDeviceId)
      .collection(TOKEN_COLLECTION)
      .where("role", "==", role)
      .limit(500)
      .get();
  if (tokenSnapshot.empty) return;

  const validRegistrations = [];
  const staleReferences = [];
  const seenTokens = new Set();
  tokenSnapshot.docs.forEach((document) => {
    const registration = document.data();
    const token = typeof registration.token === "string" ? registration.token : "";
    const uid = typeof registration.uid === "string" ? registration.uid : "";
    if (!token || !allowedUids.has(uid) || seenTokens.has(token)) {
      staleReferences.push(document.ref);
      return;
    }
    seenTokens.add(token);
    validRegistrations.push({token, ref: document.ref});
  });
  await deleteReferences(staleReferences);
  if (validRegistrations.length === 0) return;

  for (let start = 0; start < validRegistrations.length; start += 500) {
    const chunk = validRegistrations.slice(start, start + 500);
    const response = await messaging.sendEachForMulticast({
      tokens: chunk.map((registration) => registration.token),
      data,
      android: {
        priority: androidPriority,
        ttl: ttlMillis,
        collapseKey,
      },
    });
    const invalidReferences = [];
    response.responses.forEach((sendResponse, index) => {
      if (!sendResponse.success && INVALID_TOKEN_CODES.has(sendResponse.error?.code)) {
        invalidReferences.push(chunk[index].ref);
      }
    });
    await deleteReferences(invalidReferences);
  }
}

function stringSet(value) {
  return new Set(
      Array.isArray(value) ? value.filter((item) => typeof item === "string" && item) : [],
  );
}

async function deleteReferences(references) {
  if (references.length === 0) return;
  for (let start = 0; start < references.length; start += 450) {
    const batch = db.batch();
    references.slice(start, start + 450)
        .forEach((reference) => batch.delete(reference));
    await batch.commit();
  }
}
