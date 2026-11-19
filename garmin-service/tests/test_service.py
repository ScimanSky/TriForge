from datetime import date, timedelta

import pytest

from app.gateway import (
    GarminAuthError,
    GarminRateLimitError,
    GarminUnavailableError,
    MfaChallenge,
)
from app.service import CoolingDownError, MfaExpiredError, NotConnectedError

from .conftest import activity

UID = "user-1"


def test_connect_stores_encrypted_tokens_and_never_the_password(
    service, store, gateway, cipher
):
    needs_mfa = service.connect(UID, "ada@example.com", "hunter2")

    assert needs_mfa is False
    assert store.tokens[UID] != '{"t": 1}'
    assert cipher.decrypt(store.tokens[UID]) == '{"t": 1}'
    assert store.status[UID]["connected"] is True
    assert store.status[UID]["displayName"] == "Ada Rossi"
    assert "hunter2" not in repr(store.__dict__)


def test_connect_with_mfa_keeps_state_encrypted_until_code_arrives(
    service, store, gateway, cipher
):
    gateway.login_result = MfaChallenge(state={"cookies": "abc"})

    assert service.connect(UID, "ada@example.com", "hunter2") is True
    assert UID not in store.tokens
    assert "abc" not in store.mfa[UID]["stateCipher"]

    service.complete_mfa(UID, "123456")

    assert gateway.mfa_calls == [({"cookies": "abc"}, "123456")]
    assert cipher.decrypt(store.tokens[UID]) == '{"t": 2}'
    assert UID not in store.mfa
    assert store.status[UID]["connected"] is True


def test_mfa_without_pending_challenge_is_rejected(service):
    with pytest.raises(MfaExpiredError):
        service.complete_mfa(UID, "123456")


def test_mfa_challenge_expires(service, gateway, store, clock):
    gateway.login_result = MfaChallenge(state={"cookies": "abc"})
    service.connect(UID, "ada@example.com", "hunter2")
    clock.now += timedelta(minutes=11)

    with pytest.raises(MfaExpiredError):
        service.complete_mfa(UID, "123456")
    assert UID not in store.mfa
    assert gateway.mfa_calls == []


def test_wrong_mfa_code_keeps_the_challenge_for_a_retry(service, gateway, store):
    gateway.login_result = MfaChallenge(state={"cookies": "abc"})
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.mfa_result = GarminAuthError("bad code")

    with pytest.raises(GarminAuthError):
        service.complete_mfa(UID, "000000")
    assert UID in store.mfa


def test_bad_credentials_leave_no_connection(service, gateway, store):
    gateway.login_result = GarminAuthError("401")

    with pytest.raises(GarminAuthError):
        service.connect(UID, "ada@example.com", "wrong")
    assert UID not in store.tokens
    assert not store.status.get(UID, {}).get("connected")


def test_first_sync_reaches_back_the_initial_window(service, gateway, store, clock):
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.activities = [activity(1), activity(2, "lap_swimming"), activity(3, "cycling")]

    result = service.sync(UID)

    assert result.synced == 3
    tokens, start, end = gateway.fetch_calls[0]
    assert tokens == '{"t": 1}'
    assert start == date(2026, 7, 21)
    assert end == date(2026, 11, 18)
    assert set(store.activities[UID]) == {"garmin_1", "garmin_2", "garmin_3"}
    assert store.activities[UID]["garmin_2"]["sport"] == "swim"
    assert store.status[UID]["lastSyncStatus"] == "ok"
    assert store.status[UID]["lastSyncCount"] == 3


def test_later_sync_only_rereads_a_short_overlap(service, gateway, clock):
    service.connect(UID, "ada@example.com", "hunter2")
    service.sync(UID)
    clock.now += timedelta(days=10)

    service.sync(UID)

    _, start, end = gateway.fetch_calls[1]
    assert start == date(2026, 11, 15)
    assert end == date(2026, 11, 28)


def test_sync_stores_refreshed_tokens(service, gateway, store, cipher):
    service.connect(UID, "ada@example.com", "hunter2")

    service.sync(UID)

    assert cipher.decrypt(store.tokens[UID]) == '{"t": "refreshed"}'


def test_sync_is_idempotent(service, gateway, store):
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.activities = [activity(1)]

    service.sync(UID)
    service.sync(UID, days_back=30)

    assert list(store.activities[UID]) == ["garmin_1"]


def test_sync_counts_activities_it_cannot_place(service, gateway):
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.activities = [activity(1), {"activityId": 2}]

    result = service.sync(UID)

    assert (result.synced, result.skipped) == (1, 1)


def test_sync_without_connection_is_rejected(service):
    with pytest.raises(NotConnectedError):
        service.sync(UID)


def test_revoked_tokens_mark_the_connection_for_reconnect(service, gateway, store):
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.fetch_error = GarminAuthError("401")

    with pytest.raises(NotConnectedError):
        service.sync(UID)
    assert store.status[UID]["connected"] is False
    assert store.status[UID]["lastSyncStatus"] == "reconnect_required"


def test_tokens_from_a_rotated_key_require_reconnect(service, store, gateway):
    service.connect(UID, "ada@example.com", "hunter2")
    store.tokens[UID] = "not-a-valid-ciphertext"

    with pytest.raises(NotConnectedError):
        service.sync(UID)
    assert gateway.fetch_calls == []
    assert store.status[UID]["lastSyncStatus"] == "reconnect_required"


def test_garmin_outage_is_recorded_and_keeps_the_connection(service, gateway, store):
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.fetch_error = GarminUnavailableError("503")

    with pytest.raises(GarminUnavailableError):
        service.sync(UID)
    assert store.status[UID]["connected"] is True
    assert store.status[UID]["lastSyncStatus"] == "error"


def test_rate_limit_starts_a_cooldown_that_blocks_further_calls(
    service, gateway, clock
):
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.fetch_error = GarminRateLimitError("429")

    with pytest.raises(CoolingDownError) as first:
        service.sync(UID)
    assert first.value.retry_after == 900

    gateway.fetch_error = None
    clock.now += timedelta(minutes=5)
    with pytest.raises(CoolingDownError) as second:
        service.sync(UID)
    assert second.value.retry_after == 600
    assert len(gateway.fetch_calls) == 1

    clock.now += timedelta(minutes=11)
    assert service.sync(UID).synced == 0


def test_reconnecting_clears_an_old_cooldown(service, gateway, store, clock):
    service.connect(UID, "ada@example.com", "hunter2")
    gateway.fetch_error = GarminRateLimitError("429")
    with pytest.raises(CoolingDownError):
        service.sync(UID)
    clock.now += timedelta(minutes=16)

    service.connect(UID, "ada@example.com", "hunter2")

    assert store.status[UID]["rateLimitedUntil"] is None


def test_disconnect_forgets_tokens(service, store):
    service.connect(UID, "ada@example.com", "hunter2")

    service.disconnect(UID)

    assert UID not in store.tokens
    assert store.status[UID] == {"connected": False}


def test_sync_all_continues_past_a_failing_user(service, gateway, store):
    service.connect("a", "a@example.com", "pw")
    service.connect("b", "b@example.com", "pw")
    store.tokens["a"] = "broken"

    assert service.sync_all() == {"ok": 1, "failed": 1}
    assert store.status["b"]["lastSyncStatus"] == "ok"


def test_swim_laps_are_stored_once_and_kept_on_later_syncs(service, gateway, store):
    service.connect(UID, "ada@example.com", "hunter2")
    swim = activity(7)
    swim["activityType"] = {"typeKey": "lap_swimming"}
    gateway.activities = [swim]
    gateway.swim_splits = {"7": {"lapDTOs": [{"distance": 100.0, "duration": 110.0}]}}

    service.sync(UID)
    assert store.activities[UID]["garmin_7"]["laps"] == [
        {"distanceMeters": 100.0, "durationSec": 110.0}
    ]

    # The next sync does not ask for the laps again, and the merge keeps them.
    service.sync(UID, days_back=30)
    assert gateway.swims_with_laps_seen[-1] == frozenset({"7"})
    assert store.activities[UID]["garmin_7"]["hasLaps"] is True
    assert len(store.activities[UID]["garmin_7"]["laps"]) == 1
