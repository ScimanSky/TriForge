"""Persistence for the Garmin connection of each athlete.

Firestore layout, all under ``users/{uid}``:

- ``private/garmin``       encrypted tokens. Never readable by the app.
- ``private/garminMfa``    encrypted pending MFA state. Never readable by the app.
- ``integrations/garmin``  connection status. Read-only for the app.
- ``activities/{id}``      normalized activities. Read by the app.
- ``wellness/{id}``        one document per day: HRV, resting HR, sleep.

Account deletion and the withdrawal of the health consent also go through
here, because only the server may delete ``private/*`` and the user document.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Iterable, Protocol

BATCH_LIMIT = 400

# What the athlete's health consent covers: deleted when it is withdrawn.
# "coaching" holds the AI coaches' plans, chats and the eating profile, all
# built on health data.
HEALTH_COLLECTIONS = ("activities", "wellness", "checkIns", "coaching")


class Store(Protocol):
    def get_tokens(self, uid: str) -> str | None: ...

    def set_tokens(self, uid: str, cipher: str) -> None: ...

    def get_mfa(self, uid: str) -> dict[str, Any] | None: ...

    def set_mfa(self, uid: str, cipher: str, created_at: datetime) -> None: ...

    def clear_mfa(self, uid: str) -> None: ...

    def get_status(self, uid: str) -> dict[str, Any]: ...

    def update_status(self, uid: str, fields: dict[str, Any]) -> None: ...

    def upsert_activities(self, uid: str, docs: dict[str, dict[str, Any]]) -> None: ...

    def upsert_wellness(self, uid: str, docs: dict[str, dict[str, Any]]) -> None: ...

    def swims_with_laps(self, uid: str) -> Iterable[str]:
        """Garmin ids of the swims whose laps are already stored."""
        ...

    def swims_without_laps(self, uid: str, since: datetime) -> Iterable[str]:
        """Garmin ids of the swims from ``since`` on whose laps were never
        read, newest first: the swims stored before the service read laps,
        or older than the window of the syncs that came after."""
        ...

    def disconnect(self, uid: str) -> None: ...

    def connected_uids(self) -> Iterable[str]: ...

    def delete_health_data(self, uid: str) -> None: ...

    def delete_user(self, uid: str) -> None: ...


def _newest_first_without_laps(docs: Iterable[dict[str, Any]]) -> list[str]:
    """Garmin ids of the swims among ``docs`` that have no ``hasLaps`` field.

    A swim whose laps were read carries the field even when Garmin had none
    to give, so it is not asked for again.
    """
    swims = [
        doc
        for doc in docs
        if doc.get("source") == "garmin"
        and doc.get("sport") == "swim"
        and doc.get("externalId")
        and isinstance(doc.get("startTime"), datetime)
        and "hasLaps" not in doc
    ]
    swims.sort(key=lambda doc: doc["startTime"], reverse=True)
    return [str(doc["externalId"]) for doc in swims]


class MemoryStore:
    """In-memory store, used by the tests and for local runs without Firebase."""

    def __init__(self) -> None:
        self.tokens: dict[str, str] = {}
        self.mfa: dict[str, dict[str, Any]] = {}
        self.status: dict[str, dict[str, Any]] = {}
        self.activities: dict[str, dict[str, dict[str, Any]]] = {}
        self.wellness: dict[str, dict[str, dict[str, Any]]] = {}
        # Collections the app writes itself (profile, check-ins, consents),
        # so the tests can check that deletion reaches them too.
        self.app_data: dict[str, dict[str, Any]] = {}

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

    def swims_with_laps(self, uid: str) -> Iterable[str]:
        return [
            doc["externalId"]
            for doc in self.activities.get(uid, {}).values()
            if doc.get("hasLaps") and doc.get("externalId")
        ]

    def swims_without_laps(self, uid: str, since: datetime) -> Iterable[str]:
        return _newest_first_without_laps(
            doc
            for doc in self.activities.get(uid, {}).values()
            if isinstance(doc.get("startTime"), datetime) and doc["startTime"] >= since
        )

    def upsert_wellness(self, uid: str, docs: dict[str, dict[str, Any]]) -> None:
        bucket = self.wellness.setdefault(uid, {})
        for doc_id, doc in docs.items():
            bucket.setdefault(doc_id, {}).update(doc)

    def disconnect(self, uid: str) -> None:
        self.tokens.pop(uid, None)
        self.mfa.pop(uid, None)
        self.status[uid] = {"connected": False}

    def connected_uids(self) -> Iterable[str]:
        return [uid for uid, s in self.status.items() if s.get("connected")]

    def delete_health_data(self, uid: str) -> None:
        self.activities.pop(uid, None)
        self.wellness.pop(uid, None)
        for name in ("checkIns", "coaching"):
            self.app_data.get(uid, {}).pop(name, None)

    def delete_user(self, uid: str) -> None:
        for bucket in (self.tokens, self.mfa, self.status, self.activities, self.wellness, self.app_data):
            bucket.pop(uid, None)


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
        self._upsert(uid, "activities", docs)

    def upsert_wellness(self, uid: str, docs: dict[str, dict[str, Any]]) -> None:
        self._upsert(uid, "wellness", docs)

    def swims_with_laps(self, uid: str) -> Iterable[str]:
        from google.cloud.firestore_v1.base_query import FieldFilter

        query = (
            self._user(uid)
            .collection("activities")
            .where(filter=FieldFilter("hasLaps", "==", True))
            .select(["externalId"])
        )
        for snapshot in query.stream():
            external_id = (snapshot.to_dict() or {}).get("externalId")
            if external_id:
                yield str(external_id)

    def swims_without_laps(self, uid: str, since: datetime) -> Iterable[str]:
        from google.cloud.firestore_v1.base_query import FieldFilter

        # Firestore cannot ask for a missing field, and filtering on the
        # sport too would need a composite index: the few months of
        # activities are read with the fields that decide, and chosen here.
        query = (
            self._user(uid)
            .collection("activities")
            .where(filter=FieldFilter("startTime", ">=", since))
            .select(["externalId", "source", "sport", "startTime", "hasLaps"])
        )
        return _newest_first_without_laps(
            snapshot.to_dict() or {} for snapshot in query.stream()
        )

    def _upsert(self, uid: str, name: str, docs: dict[str, dict[str, Any]]) -> None:
        collection = self._user(uid).collection(name)
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

    def delete_health_data(self, uid: str) -> None:
        for name in HEALTH_COLLECTIONS:
            self._db.recursive_delete(self._user(uid).collection(name))

    def delete_user(self, uid: str) -> None:
        # The user document and every subcollection under it, tokens included.
        self._db.recursive_delete(self._user(uid))

    def connected_uids(self) -> Iterable[str]:
        from google.cloud.firestore_v1.base_query import FieldFilter

        query = self._db.collection_group("integrations").where(
            filter=FieldFilter("connected", "==", True)
        )
        for snapshot in query.stream():
            # Path is users/{uid}/integrations/{provider}.
            if snapshot.id == "garmin":
                yield snapshot.reference.parent.parent.id
