# Firestore Rules

ScreenRest remote parent features use Firebase Authentication and Cloud Firestore.
The current MVP uses anonymous authentication and a pairing code flow.

## Collections

- `screenrest_pairing_codes/{pairingCode}`
- `screenrest_children/{childDeviceId}/unlock_requests/{requestId}`
- `screenrest_children/{childDeviceId}/remote_commands/{commandId}`

## MVP Rule Draft

Use this only while testing with paired devices. It requires the client to be signed in,
but it does not yet fully prove that the signed-in user owns the parent/child relation.

```firestore
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() {
      return request.auth != null;
    }

    match /screenrest_pairing_codes/{pairingCode} {
      allow create, read: if signedIn();
      allow update, delete: if false;
    }

    match /screenrest_children/{childDeviceId} {
      allow create, read, update: if signedIn();
      allow delete: if false;
    }

    match /screenrest_children/{childDeviceId}/unlock_requests/{requestId} {
      allow create, read, update: if signedIn();
      allow delete: if false;
    }

    match /screenrest_children/{childDeviceId}/remote_commands/{commandId} {
      allow create, read, update: if signedIn();
      allow delete: if false;
    }
  }
}
```

## Release Target

Before release, add ownership fields and restrict access to linked devices only.

Required document fields:

- Pairing code doc: `childUid`, `childDeviceId`, `expiresAt`, `expiresAtMillis`, `status`, `used`
- Child root doc: `childUid`, `parentUids`, `activePairingCode`, `status`, `updatedAtMillis`
- Unlock request doc: `childUid`, `parentUid`
- Remote command doc: `parentUid`

```firestore
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() {
      return request.auth != null;
    }

    function childDoc(childDeviceId) {
      return get(/databases/$(database)/documents/screenrest_children/$(childDeviceId));
    }

    function isChildOwner(childDeviceId) {
      return signedIn() && childDoc(childDeviceId).data.childUid == request.auth.uid;
    }

    function isLinkedParent(childDeviceId) {
      return signedIn() &&
        childDoc(childDeviceId).data.status != "unlinked" &&
        request.auth.uid in childDoc(childDeviceId).data.parentUids;
    }

    function activePairingCode(childDeviceId) {
      return childDoc(childDeviceId).data.activePairingCode;
    }

    function canReadForActivePairing(childDeviceId) {
      let pairing = get(/databases/$(database)/documents/screenrest_pairing_codes/$(activePairingCode(childDeviceId)));
      return signedIn() &&
        pairing.data.childDeviceId == childDeviceId &&
        pairing.data.status == "active" &&
        pairing.data.used == false &&
        pairing.data.expiresAt > request.time;
    }

    function isAtomicPairingParent(childDeviceId) {
      let pairingAfter = getAfter(/databases/$(database)/documents/screenrest_pairing_codes/$(activePairingCode(childDeviceId)));
      return signedIn() &&
        pairingAfter.data.childDeviceId == childDeviceId &&
        pairingAfter.data.status == "used" &&
        pairingAfter.data.used == true &&
        pairingAfter.data.parentUid == request.auth.uid &&
        request.auth.uid in request.resource.data.parentUids;
    }

    function isActivePairingCode() {
      return resource.data.status == "active" &&
        resource.data.used == false &&
        resource.data.expiresAt > request.time;
    }

    match /screenrest_pairing_codes/{pairingCode} {
      allow create: if signedIn() &&
        request.resource.data.childUid == request.auth.uid &&
        request.resource.data.status == "active" &&
        request.resource.data.used == false;
      allow get: if signedIn() &&
        (isActivePairingCode() || resource.data.parentUid == request.auth.uid || resource.data.childUid == request.auth.uid);
      allow list: if false;
      allow update: if signedIn() &&
        (
          resource.data.childUid == request.auth.uid ||
          request.resource.data.parentUid == request.auth.uid
        ) &&
        request.resource.data.status in ["used", "expired"];
      allow delete: if false;
    }

    match /screenrest_children/{childDeviceId} {
      allow create: if signedIn() &&
        request.resource.data.childUid == request.auth.uid;
      allow read: if isChildOwner(childDeviceId) || isLinkedParent(childDeviceId) || canReadForActivePairing(childDeviceId);
      allow update: if isChildOwner(childDeviceId) || isLinkedParent(childDeviceId) || isAtomicPairingParent(childDeviceId);

      match /unlock_requests/{requestId} {
        allow create: if isChildOwner(childDeviceId) &&
          request.resource.data.childUid == request.auth.uid;
        allow read: if isChildOwner(childDeviceId) || isLinkedParent(childDeviceId);
        allow update: if isLinkedParent(childDeviceId);
        allow delete: if false;
      }

      match /remote_commands/{commandId} {
        allow create: if isLinkedParent(childDeviceId);
        allow read: if isChildOwner(childDeviceId) || isLinkedParent(childDeviceId);
        allow update: if isChildOwner(childDeviceId);
        allow delete: if false;
      }
    }
  }
}
```

## Notes

- The MVP rule is intentionally still limited to authenticated clients, but it is not
  enough for production.
- Release rules require the app to write UID ownership fields during pairing.
- Pairing codes are single-use. The app now marks them as `used`, or `expired`
  when a parent tries to resolve an expired code.
- A child device can have multiple linked parent UIDs in `parentUids`.
  Linked parents are equal authority in the current MVP. If needed later,
  add an owner/guardian role field for sensitive operations such as unlinking
  every parent or changing remote-management policy.
- A parent device can track multiple child devices locally. It syncs each
  linked child document and merges unlock requests into one parent request list.
- For multi-parent request conflicts, the app uses a first-decision-wins model:
  once an unlock request is no longer `Pending`, later parent approvals or
  rejections do not overwrite the existing decision.
- Disconnecting a parent/child link does not delete data immediately. The app
  removes only the current parent UID from `parentUids`. It marks the child
  document as `status = "unlinked"` only when no linked parent remains, and
  writes `unlinkedAtMillis`. A release policy can later delete or archive those
  records after 30 days.
- FCM or Firestore snapshot listeners can reduce latency later, but the current MVP
  keeps fast polling on the block screen to avoid extra background policy complexity.
