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

exports.deleteCurrentUserData = onCall(
    {enforceAppCheck: true},
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
        priority: "high",
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
