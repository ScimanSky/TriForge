"""Connect, sync and disconnect an athlete's Garmin account."""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Callable

from .config import Settings
from .crypto import TokenCipher
from .gateway import (
    GarminAuthError,
    GarminGateway,
    GarminRateLimitError,
    MfaChallenge,
    Session,
)
from .normalize import document_id, normalize_activity, normalize_health
from .store import Store

logger = logging.getLogger(__name__)


class NotConnectedError(Exception):
    """The athlete has no usable Garmin connection."""


class MfaExpiredError(Exception):
    """There is no pending MFA challenge, or it is too old."""


class CoolingDownError(Exception):
    """Garmin rate-limited us recently; retry after ``retry_after`` seconds."""

    def __init__(self, retry_after: int) -> None:
        super().__init__(f"retry after {retry_after}s")
        self.retry_after = retry_after


@dataclass(frozen=True)
class SyncResult:
    synced: int
    skipped: int


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class GarminService:
    def __init__(
        self,
        store: Store,
        gateway: GarminGateway,
        cipher: TokenCipher,
        settings: Settings,
        now: Callable[[], datetime] = _utcnow,
    ) -> None:
        self._store = store
        self._gateway = gateway
        self._cipher = cipher
        self._settings = settings
        self._now = now

    # -- connection -------------------------------------------------------

    def connect(self, uid: str, email: str, password: str) -> bool:
        """Start a Garmin login. Returns True when an MFA code is needed.

        The password is used for this one call and never stored.
        """
        self._check_cooldown(uid)
        self._store.clear_mfa(uid)
        try:
            result = self._gateway.login(email, password)
        except GarminRateLimitError:
            raise self._start_cooldown(uid) from None

        if isinstance(result, MfaChallenge):
            state = self._cipher.encrypt(json.dumps(result.state))
            self._store.set_mfa(uid, state, self._now())
            return True

        self._save_connection(uid, result)
        return False

    def complete_mfa(self, uid: str, code: str) -> None:
        pending = self._store.get_mfa(uid)
        if not pending:
            raise MfaExpiredError()
        age = (self._now() - pending["createdAt"]).total_seconds()
        state_json = self._cipher.decrypt(pending["stateCipher"])
        if age > self._settings.mfa_ttl_seconds or state_json is None:
            self._store.clear_mfa(uid)
            raise MfaExpiredError()

        try:
            session = self._gateway.complete_mfa(json.loads(state_json), code)
        except GarminRateLimitError:
            raise self._start_cooldown(uid) from None
        self._store.clear_mfa(uid)
        self._save_connection(uid, session)

    def disconnect(self, uid: str) -> None:
        self._store.disconnect(uid)

    # -- privacy ----------------------------------------------------------

    def withdraw_health_consent(self, uid: str) -> None:
        """Stop collecting health data and erase what was collected.

        The Garmin tokens go first, so the scheduled sync cannot bring the
        data back. The account, profile and consent history stay.
        """
        self._store.disconnect(uid)
        self._store.delete_health_data(uid)

    def delete_account_data(self, uid: str) -> None:
        """Erase everything stored for the athlete, Garmin tokens included."""
        self._store.delete_user(uid)

    # -- sync -------------------------------------------------------------

    def sync(self, uid: str, days_back: int | None = None) -> SyncResult:
        """Copy recent Garmin activities and daily recovery data into Firestore."""
        self._check_cooldown(uid)
        cipher = self._store.get_tokens(uid)
        tokens = self._cipher.decrypt(cipher) if cipher else None
        if tokens is None:
            if cipher:
                self._mark_disconnected(uid, "reconnect_required")
            raise NotConnectedError()

        now = self._now()
        status = self._store.get_status(uid)
        last_sync = status.get("lastSyncAt")
        if days_back is not None:
            start = now - timedelta(days=days_back)
        elif isinstance(last_sync, datetime):
            start = last_sync - timedelta(days=self._settings.resync_overlap_days)
        else:
            start = now - timedelta(days=self._settings.initial_sync_days)

        try:
            data = self._gateway.fetch(tokens, start.date(), now.date())
        except GarminAuthError:
            # Tokens were revoked or expired beyond refresh.
            self._mark_disconnected(uid, "reconnect_required")
            raise NotConnectedError() from None
        except GarminRateLimitError:
            raise self._start_cooldown(uid) from None
        except Exception:
            self._store.update_status(
                uid, {"lastSyncStatus": "error", "lastSyncAttemptAt": now}
            )
            raise

        docs = {}
        skipped = 0
        for item in data.activities:
            doc = normalize_activity(item)
            if doc is None:
                skipped += 1
                continue
            doc["syncedAt"] = now
            docs[document_id(item["activityId"])] = doc

        health = normalize_health(
            data.health.hrv, data.health.resting_hr, data.health.sleep
        )
        for doc in health.values():
            doc["syncedAt"] = now

        self._store.upsert_activities(uid, docs)
        self._store.upsert_wellness(uid, health)
        self._store.set_tokens(uid, self._cipher.encrypt(data.session.tokens))
        self._store.update_status(
            uid,
            {
                "connected": True,
                "lastSyncAt": now,
                "lastSyncAttemptAt": now,
                "lastSyncStatus": "ok",
                "lastSyncCount": len(docs),
                "lastSyncHealthDays": len(health),
            },
        )
        return SyncResult(synced=len(docs), skipped=skipped)

    def sync_all(self) -> dict[str, int]:
        """Sync every connected athlete. One failure does not stop the rest."""
        ok = failed = 0
        for uid in list(self._store.connected_uids()):
            try:
                self.sync(uid)
                ok += 1
            except CoolingDownError:
                # Garmin limits by caller, so the remaining users would be
                # refused as well. Stop and let the next run pick them up.
                failed += 1
                logger.warning("sync-all stopped early: rate limited by Garmin")
                break
            except Exception as err:  # noqa: BLE001 - keep going for other users
                failed += 1
                logger.warning("sync failed for one user: %s", type(err).__name__)
        return {"ok": ok, "failed": failed}

    # -- helpers ----------------------------------------------------------

    def _save_connection(self, uid: str, session: Session) -> None:
        self._store.set_tokens(uid, self._cipher.encrypt(session.tokens))
        self._store.update_status(
            uid,
            {
                "connected": True,
                "displayName": session.full_name or session.display_name or "",
                "connectedAt": self._now(),
                "lastSyncStatus": "pending",
                "rateLimitedUntil": None,
            },
        )

    def _mark_disconnected(self, uid: str, reason: str) -> None:
        self._store.update_status(
            uid, {"connected": False, "lastSyncStatus": reason}
        )

    def _check_cooldown(self, uid: str) -> None:
        until = self._store.get_status(uid).get("rateLimitedUntil")
        if isinstance(until, datetime):
            remaining = int((until - self._now()).total_seconds())
            if remaining > 0:
                raise CoolingDownError(remaining)

    def _start_cooldown(self, uid: str) -> CoolingDownError:
        seconds = self._settings.rate_limit_cooldown_seconds
        self._store.update_status(
            uid, {"rateLimitedUntil": self._now() + timedelta(seconds=seconds)}
        )
        return CoolingDownError(seconds)
