"""TriForge Garmin sync service.

Runs on Cloud Run. The Android app calls it with the athlete's Firebase ID
token; the service logs in to Garmin Connect through the unofficial
``garminconnect`` library and copies activities into Firestore, where the app
reads them.

Endpoints:

- ``GET    /health``
- ``POST   /garmin/connect``     start a login with Garmin email and password
- ``POST   /garmin/mfa``         finish a login that asked for a code
- ``POST   /garmin/sync``        import recent activities
- ``DELETE /garmin/connection``  forget the Garmin tokens
- ``DELETE /account/health-data`` withdraw the health consent: disconnect
  Garmin and erase activities, recovery data and check-ins
- ``DELETE /account``            erase every stored datum and the login itself
- ``POST   /internal/sync-all``  scheduled sync for every connected athlete
"""

# No ``from __future__ import annotations`` here: FastAPI resolves handler
# annotations at runtime, and the ``Uid`` dependency is local to create_app.
import hmac
import logging
from typing import Annotated, Callable

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, EmailStr, Field

from .config import Settings, load_settings
from .crypto import TokenCipher
from .gateway import (
    GarminAuthError,
    GarminConnectGateway,
    GarminGateway,
    GarminUnavailableError,
)
from .service import (
    CoolingDownError,
    GarminService,
    MfaExpiredError,
    NotConnectedError,
)
from .store import FirestoreStore, Store

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("triforge.garmin")

TokenVerifier = Callable[[str], str]
UserDeleter = Callable[[str], None]


class ConnectRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=256)


class MfaRequest(BaseModel):
    code: str = Field(min_length=4, max_length=12, pattern=r"^[0-9A-Za-z]+$")


class SyncRequest(BaseModel):
    days_back: int | None = Field(default=None, ge=1, le=730, alias="daysBack")


def _firebase_verifier() -> TokenVerifier:
    import firebase_admin
    from firebase_admin import auth

    if not firebase_admin._apps:
        firebase_admin.initialize_app()

    def verify(token: str) -> str:
        return auth.verify_id_token(token)["uid"]

    return verify


def _firebase_user_deleter() -> UserDeleter:
    import firebase_admin
    from firebase_admin import auth

    if not firebase_admin._apps:
        firebase_admin.initialize_app()

    def delete(uid: str) -> None:
        try:
            auth.delete_user(uid)
        except auth.UserNotFoundError:
            pass

    return delete


def _firestore_store() -> Store:
    import firebase_admin
    from firebase_admin import firestore

    if not firebase_admin._apps:
        firebase_admin.initialize_app()
    return FirestoreStore(firestore.client())


def create_app(
    settings: Settings | None = None,
    store: Store | None = None,
    gateway: GarminGateway | None = None,
    verify_token: TokenVerifier | None = None,
    delete_user: UserDeleter | None = None,
) -> FastAPI:
    """Build the app. Arguments are injected by the tests."""
    settings = settings or load_settings()
    store = store or _firestore_store()
    verify_token = verify_token or _firebase_verifier()
    delete_user = delete_user or _firebase_user_deleter()
    service = GarminService(
        store=store,
        gateway=gateway or GarminConnectGateway(),
        cipher=TokenCipher(settings.token_encryption_key),
        settings=settings,
    )

    app = FastAPI(title="TriForge Garmin Service", version="1.0.0")

    def current_uid(authorization: Annotated[str | None, Header()] = None) -> str:
        scheme, _, token = (authorization or "").partition(" ")
        if scheme.lower() != "bearer" or not token:
            raise HTTPException(status_code=401, detail="missing_token")
        try:
            return verify_token(token)
        except Exception:  # noqa: BLE001 - any verification failure is a 401
            raise HTTPException(status_code=401, detail="invalid_token") from None

    Uid = Annotated[str, Depends(current_uid)]

    def cooling_down(err: CoolingDownError) -> HTTPException:
        return HTTPException(
            status_code=429,
            detail="garmin_rate_limited",
            headers={"Retry-After": str(err.retry_after)},
        )

    # Handlers are plain ``def`` on purpose: the Garmin library is blocking,
    # and FastAPI runs sync handlers in a thread pool.

    # Not /healthz: Cloud Run keeps that path for itself on run.app addresses
    # and answers 404 before the request reaches the app.
    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/garmin/connect")
    def connect(body: ConnectRequest, uid: Uid) -> dict[str, str]:
        try:
            needs_mfa = service.connect(uid, body.email, body.password)
        except CoolingDownError as err:
            raise cooling_down(err) from None
        except GarminAuthError:
            raise HTTPException(status_code=401, detail="garmin_bad_credentials") from None
        except GarminUnavailableError:
            raise HTTPException(status_code=502, detail="garmin_unavailable") from None
        return {"status": "mfa_required" if needs_mfa else "connected"}

    @app.post("/garmin/mfa")
    def mfa(body: MfaRequest, uid: Uid) -> dict[str, str]:
        try:
            service.complete_mfa(uid, body.code)
        except MfaExpiredError:
            raise HTTPException(status_code=409, detail="mfa_expired") from None
        except CoolingDownError as err:
            raise cooling_down(err) from None
        except GarminAuthError:
            raise HTTPException(status_code=401, detail="garmin_bad_mfa_code") from None
        except GarminUnavailableError:
            raise HTTPException(status_code=502, detail="garmin_unavailable") from None
        return {"status": "connected"}

    @app.post("/garmin/sync")
    def sync(uid: Uid, body: SyncRequest | None = None) -> dict[str, int]:
        try:
            result = service.sync(uid, body.days_back if body else None)
        except NotConnectedError:
            raise HTTPException(status_code=409, detail="garmin_not_connected") from None
        except CoolingDownError as err:
            raise cooling_down(err) from None
        except GarminUnavailableError:
            raise HTTPException(status_code=502, detail="garmin_unavailable") from None
        return {"synced": result.synced, "skipped": result.skipped}

    @app.delete("/garmin/connection")
    def disconnect(uid: Uid) -> dict[str, str]:
        service.disconnect(uid)
        return {"status": "disconnected"}

    @app.delete("/account/health-data")
    def withdraw_health_consent(uid: Uid) -> dict[str, str]:
        service.withdraw_health_consent(uid)
        logger.info("health data erased after consent withdrawal")
        return {"status": "deleted"}

    @app.delete("/account")
    def delete_account(uid: Uid) -> dict[str, bool]:
        # Data first: if deleting the login fails, the app can retry it
        # itself, but data left behind by a deleted login would be orphaned.
        service.delete_account_data(uid)
        try:
            delete_user(uid)
            auth_deleted = True
        except Exception as err:  # noqa: BLE001 - the app falls back to deleting the login itself
            logger.warning("account data erased, login not deleted: %s", type(err).__name__)
            auth_deleted = False
        return {"dataDeleted": True, "authDeleted": auth_deleted}

    @app.post("/internal/sync-all")
    def sync_all(
        x_scheduler_secret: Annotated[str | None, Header()] = None,
    ) -> dict[str, int]:
        expected = settings.scheduler_secret
        provided = x_scheduler_secret or ""
        if not expected or not hmac.compare_digest(
            provided.encode("utf-8"), expected.encode("utf-8")
        ):
            raise HTTPException(status_code=403, detail="forbidden")
        return service.sync_all()

    return app

