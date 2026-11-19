from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Any

import pytest
from cryptography.fernet import Fernet

from app.config import Settings
from app.crypto import TokenCipher
from app.gateway import GarminData, HealthData, MfaChallenge, Session
from app.service import GarminService
from app.store import MemoryStore


class FakeGateway:
    """Stands in for Garmin. Tests set the attributes to script its answers."""

    def __init__(self) -> None:
        self.login_result: Any = Session(tokens='{"t": 1}', full_name="Ada Rossi")
        self.mfa_result: Any = Session(tokens='{"t": 2}', full_name="Ada Rossi")
        self.activities: list[dict[str, Any]] = []
        self.health = HealthData()
        self.fetch_error: Exception | None = None
        self.refreshed_tokens = '{"t": "refreshed"}'
        self.login_calls: list[tuple[str, str]] = []
        self.mfa_calls: list[tuple[Any, str]] = []
        self.fetch_calls: list[tuple[str, date, date]] = []

    def login(self, email: str, password: str) -> Session | MfaChallenge:
        self.login_calls.append((email, password))
        if isinstance(self.login_result, Exception):
            raise self.login_result
        return self.login_result

    def complete_mfa(self, state: Any, code: str) -> Session:
        self.mfa_calls.append((state, code))
        if isinstance(self.mfa_result, Exception):
            raise self.mfa_result
        return self.mfa_result

    def fetch(self, tokens: str, start: date, end: date) -> GarminData:
        self.fetch_calls.append((tokens, start, end))
        if self.fetch_error:
            raise self.fetch_error
        return GarminData(self.activities, self.health, Session(tokens=self.refreshed_tokens))


class Clock:
    def __init__(self) -> None:
        self.now = datetime(2026, 11, 18, 12, 0, tzinfo=timezone.utc)

    def __call__(self) -> datetime:
        return self.now


@pytest.fixture
def settings() -> Settings:
    return Settings(
        token_encryption_key=Fernet.generate_key().decode(),
        scheduler_secret="scheduler-secret",
    )


@pytest.fixture
def store() -> MemoryStore:
    return MemoryStore()


@pytest.fixture
def gateway() -> FakeGateway:
    return FakeGateway()


@pytest.fixture
def clock() -> Clock:
    return Clock()


@pytest.fixture
def cipher(settings) -> TokenCipher:
    return TokenCipher(settings.token_encryption_key)


@pytest.fixture
def service(store, gateway, cipher, settings, clock) -> GarminService:
    return GarminService(store, gateway, cipher, settings, now=clock)


def activity(activity_id: int, type_key: str = "running", start: str = "2026-11-17 06:30:00"):
    return {
        "activityId": activity_id,
        "activityName": f"Activity {activity_id}",
        "startTimeGMT": start,
        "startTimeLocal": start,
        "activityType": {"typeKey": type_key},
        "distance": 5000.0,
        "duration": 1500.0,
    }
