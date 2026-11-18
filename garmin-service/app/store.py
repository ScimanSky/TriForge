"""Persistence for the Garmin connection of each athlete.

Firestore layout, all under ``users/{uid}``:

- ``private/garmin``       encrypted tokens. Never readable by the app.
- ``private/garminMfa``    encrypted pending MFA state. Never readable by the app.
- ``integrations/garmin``  connection status. Read-only for the app.
- ``activities/{id}``      normalized activities. Read by the app.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Iterable, Protocol

BATCH_LIMIT = 400


class Store(Protocol):
    def get_tokens(self, uid: str) -> str | None: ...

    def set_tokens(self, uid: str, cipher: str) -> None: ...

    def get_mfa(self, uid: str) -> dict[str, Any] | None: ...

    def set_mfa(self, uid: str, cipher: str, created_at: datetime) -> None: ...

    def clear_mfa(self, uid: str) -> None: ...

    def get_status(self, uid: str) -> dict[str, Any]: ...

    def update_status(self, uid: str, fields: dict[str, Any]) -> None: ...

    def upsert_activities(self, uid: str, docs: dict[str, dict[str, Any]]) -> None: ...

    def disconnect(self, uid: str) -> None: ...

    def connected_uids(self) -> Iterable[str]: ...


class MemoryStore:
    """In-memory store, used by the tests and for local runs without Firebase."""

    def __init__(self) -> None:
        self.tokens: dict[str, str] = {}
        self.mfa: dict[str, dict[str, Any]] = {}
        self.status: dict[str, dict[str, Any]] = {}
        self.activities: dict[str, dict[str, dict[str, Any]]] = {}

    def get_tokens(self, uid: str) -> str | None:
        return self.tokens.get(uid)

    def set_tokens(self, uid: str, cipher: str) -> None:
        self.tokens[uid] = cipher

    def get_mfa(self, uid: str) -> dict[str, Any] | None:
        return self.mfa.get(uid)

    def set_mfa(self, uid: str, cipher: str, created_at: datetime) -> None:
        self.mfa[uid] = {"stateCipher": cipher, "createdAt": created_at}

    def clear_mfa(self, uid: str) -> None:
        self.mfa.pop(uid, None)

    def get_status(self, uid: str) -> dict[str, Any]:
        return dict(self.status.get(uid, {}))

    def update_status(self, uid: str, fields: dict[str, Any]) -> None:
        self.status.setdefault(uid, {}).update(fields)

    def upsert_activities(self, uid: str, docs: dict[str, dict[str, Any]]) -> None:
        bucket = self.activities.setdefault(uid, {})
        for doc_id, doc in docs.items():
            bucket.setdefault(doc_id, {}).update(doc)

    def disconnect(self, uid: str) -> None:
        self.tokens.pop(uid, None)
        self.mfa.pop(uid, None)
        self.status[uid] = {"connected": False}

    def connected_uids(self) -> Iterable[str]:
        return [uid for uid, s in self.status.items() if s.get("connected")]


class FirestoreStore:
    def __init__(self, client: Any) -> None:
        self._db = client

    def _user(self, uid: str) -> Any:
        return self._db.collection("users").document(uid)

    def _tokens_ref(self, uid: str) -> Any:
        return self._user(uid).collection("private").document("garmin")

    def _mfa_ref(self, uid: str) -> Any:
        return self._user(uid).collection("private").document("garminMfa")

    def _status_ref(self, uid: str) -> Any:
        return self._user(uid).collection("integrations").document("garmin")

    def get_tokens(self, uid: str) -> str | None:
        snapshot = self._tokens_ref(uid).get()
        return (snapshot.to_dict() or {}).get("tokenCipher") if snapshot.exists else None

    def set_tokens(self, uid: str, cipher: str) -> None:
        self._tokens_ref(uid).set({"tokenCipher": cipher})

    def get_mfa(self, uid: str) -> dict[str, Any] | None:
        snapshot = self._mfa_ref(uid).get()
        return snapshot.to_dict() if snapshot.exists else None

    def set_mfa(self, uid: str, cipher: str, created_at: datetime) -> None:
        self._mfa_ref(uid).set({"stateCipher": cipher, "createdAt": created_at})

    def clear_mfa(self, uid: str) -> None:
        self._mfa_ref(uid).delete()

    def get_status(self, uid: str) -> dict[str, Any]:
        snapshot = self._status_ref(uid).get()
        return (snapshot.to_dict() or {}) if snapshot.exists else {}

    def update_status(self, uid: str, fields: dict[str, Any]) -> None:
        self._status_ref(uid).set(fields, merge=True)

    def upsert_activities(self, uid: str, docs: dict[str, dict[str, Any]]) -> None:
        collection = self._user(uid).collection("activities")
        items = list(docs.items())
        for offset in range(0, len(items), BATCH_LIMIT):
            batch = self._db.batch()
            for doc_id, doc in items[offset : offset + BATCH_LIMIT]:
                batch.set(collection.document(doc_id), doc, merge=True)
            batch.commit()

    def disconnect(self, uid: str) -> None:
        self._tokens_ref(uid).delete()
        self._mfa_ref(uid).delete()
        # Replace the whole status so nothing about the old account lingers.
        self._status_ref(uid).set({"connected": False})

    def connected_uids(self) -> Iterable[str]:
        from google.cloud.firestore_v1.base_query import FieldFilter

        query = self._db.collection_group("integrations").where(
            filter=FieldFilter("connected", "==", True)
        )
        for snapshot in query.stream():
            # Path is users/{uid}/integrations/{provider}.
            if snapshot.id == "garmin":
                yield snapshot.reference.parent.parent.id
