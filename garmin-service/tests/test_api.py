import pytest
from fastapi.testclient import TestClient

from app.gateway import GarminAuthError, GarminRateLimitError, MfaChallenge
from app.main import create_app

from .conftest import activity

AUTH = {"Authorization": "Bearer token-for-user-1"}


def verify(token: str) -> str:
    if not token.startswith("token-for-"):
        raise ValueError("bad token")
    return token.removeprefix("token-for-")


@pytest.fixture
def client(settings, store, gateway) -> TestClient:
    return TestClient(
        create_app(settings=settings, store=store, gateway=gateway, verify_token=verify)
    )


def test_health(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@pytest.mark.parametrize(
    "headers",
    [{}, {"Authorization": "Bearer nope"}, {"Authorization": "Basic abc"}],
)
def test_requests_without_a_valid_firebase_token_are_rejected(client, headers):
    response = client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=headers,
    )
    assert response.status_code == 401


def test_connect_then_sync(client, gateway, store):
    gateway.activities = [activity(1), activity(2, "cycling")]

    connect = client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )
    sync = client.post("/garmin/sync", headers=AUTH)

    assert connect.json() == {"status": "connected"}
    assert sync.json() == {"synced": 2, "skipped": 0}
    assert set(store.activities["user-1"]) == {"garmin_1", "garmin_2"}


def test_sync_accepts_days_back(client, gateway):
    client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )

    response = client.post("/garmin/sync", json={"daysBack": 7}, headers=AUTH)

    assert response.status_code == 200
    _, start, end = gateway.fetch_calls[0]
    assert (end - start).days == 7


def test_mfa_flow(client, gateway):
    gateway.login_result = MfaChallenge(state={"s": 1})

    connect = client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )
    mfa = client.post("/garmin/mfa", json={"code": "123456"}, headers=AUTH)

    assert connect.json() == {"status": "mfa_required"}
    assert mfa.json() == {"status": "connected"}


def test_mfa_without_challenge_is_a_conflict(client):
    response = client.post("/garmin/mfa", json={"code": "123456"}, headers=AUTH)
    assert response.status_code == 409
    assert response.json()["detail"] == "mfa_expired"


def test_bad_garmin_credentials(client, gateway):
    gateway.login_result = GarminAuthError("401")

    response = client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "garmin_bad_credentials"


def test_rate_limit_reports_retry_after(client, gateway):
    gateway.login_result = GarminRateLimitError("429")

    response = client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )

    assert response.status_code == 429
    assert response.headers["Retry-After"] == "900"


def test_sync_without_connection(client):
    response = client.post("/garmin/sync", headers=AUTH)
    assert response.status_code == 409
    assert response.json()["detail"] == "garmin_not_connected"


def test_users_only_touch_their_own_data(client, gateway, store):
    client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )

    other = client.post("/garmin/sync", headers={"Authorization": "Bearer token-for-user-2"})

    assert other.status_code == 409
    assert "user-2" not in store.activities


def test_disconnect(client, store):
    client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )

    response = client.delete("/garmin/connection", headers=AUTH)

    assert response.json() == {"status": "disconnected"}
    assert "user-1" not in store.tokens


@pytest.mark.parametrize("secret", [None, "", "wrong"])
def test_sync_all_requires_the_scheduler_secret(client, secret):
    headers = {} if secret is None else {"X-Scheduler-Secret": secret}
    assert client.post("/internal/sync-all", headers=headers).status_code == 403


def test_sync_all_with_secret(client):
    client.post(
        "/garmin/connect",
        json={"email": "ada@example.com", "password": "pw"},
        headers=AUTH,
    )

    response = client.post(
        "/internal/sync-all", headers={"X-Scheduler-Secret": "scheduler-secret"}
    )

    assert response.json() == {"ok": 1, "failed": 0}


def test_sync_all_is_disabled_without_a_configured_secret(store, gateway, settings):
    from dataclasses import replace

    app = create_app(
        settings=replace(settings, scheduler_secret=""),
        store=store,
        gateway=gateway,
        verify_token=verify,
    )

    response = TestClient(app).post("/internal/sync-all", headers={"X-Scheduler-Secret": ""})

    assert response.status_code == 403
