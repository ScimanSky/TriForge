"""Runtime configuration, read once from the environment."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    # Fernet key used to encrypt Garmin tokens before they reach Firestore.
    token_encryption_key: str
    # Shared secret that Cloud Scheduler sends to /internal/sync-all. Empty
    # disables the endpoint.
    scheduler_secret: str = ""
    # How far back the first sync of a new connection reaches.
    initial_sync_days: int = 120
    # Overlap re-read on every later sync, so edits made on Garmin are picked up.
    resync_overlap_days: int = 3
    # Pause after Garmin answers 429, before the service contacts it again.
    rate_limit_cooldown_seconds: int = 900
    # How long a pending MFA challenge stays valid.
    mfa_ttl_seconds: int = 600


def load_settings() -> Settings:
    key = os.environ.get("TOKEN_ENCRYPTION_KEY", "")
    if not key:
        raise RuntimeError("TOKEN_ENCRYPTION_KEY is required")
    return Settings(
        token_encryption_key=key,
        scheduler_secret=os.environ.get("SCHEDULER_SECRET", ""),
        initial_sync_days=int(os.environ.get("INITIAL_SYNC_DAYS", "120")),
        resync_overlap_days=int(os.environ.get("RESYNC_OVERLAP_DAYS", "3")),
        rate_limit_cooldown_seconds=int(
            os.environ.get("RATE_LIMIT_COOLDOWN_SECONDS", "900")
        ),
        mfa_ttl_seconds=int(os.environ.get("MFA_TTL_SECONDS", "600")),
    )
