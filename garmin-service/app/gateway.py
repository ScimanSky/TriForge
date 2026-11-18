"""Thin wrapper around the unofficial ``garminconnect`` library.

Everything that touches Garmin goes through ``GarminGateway`` so the rest of
the service, and the tests, never import the library directly. If Garmin
changes its login flow or the library is replaced, this file is the only one
that has to change.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Any, Protocol


class GarminAuthError(Exception):
    """Credentials or tokens were rejected."""


class GarminRateLimitError(Exception):
    """Garmin answered 429."""


class GarminUnavailableError(Exception):
    """Garmin could not be reached or answered with an unexpected error."""


@dataclass(frozen=True)
class Session:
    """A logged-in Garmin session, as it is persisted between requests."""

    tokens: str
    display_name: str | None = None
    full_name: str | None = None


@dataclass(frozen=True)
class MfaChallenge:
    """Garmin asked for a second factor. ``state`` resumes the login."""

    state: Any


class GarminGateway(Protocol):
    def login(self, email: str, password: str) -> Session | MfaChallenge: ...

    def complete_mfa(self, state: Any, code: str) -> Session: ...

    def fetch_activities(
        self, tokens: str, start: date, end: date
    ) -> tuple[list[dict[str, Any]], Session]: ...


class GarminConnectGateway:
    """Production gateway backed by python-garminconnect."""

    def login(self, email: str, password: str) -> Session | MfaChallenge:
        from garminconnect import Garmin

        client = Garmin(email=email, password=password, return_on_mfa=True)
        status, state = self._call(client.login)
        if status == "needs_mfa":
            return MfaChallenge(state=state)
        return self._session(client)

    def complete_mfa(self, state: Any, code: str) -> Session:
        from garminconnect import Garmin

        client = Garmin(return_on_mfa=True)
        self._call(client.resume_login, state, code)
        return self._session(client)

    def fetch_activities(
        self, tokens: str, start: date, end: date
    ) -> tuple[list[dict[str, Any]], Session]:
        from garminconnect import Garmin

        client = Garmin()
        # A JSON string is treated as inline token data, not as a path.
        self._call(client.login, tokens)
        activities = self._call(
            client.get_activities_by_date, start.isoformat(), end.isoformat()
        )
        # The library refreshes tokens while it works, so hand the current
        # ones back to be stored.
        return list(activities or []), self._session(client)

    @staticmethod
    def _session(client: Any) -> Session:
        return Session(
            tokens=client.client.dumps(),
            display_name=getattr(client, "display_name", None),
            full_name=getattr(client, "full_name", None),
        )

    @staticmethod
    def _call(method: Any, *args: Any) -> Any:
        from garminconnect import (
            GarminConnectAuthenticationError,
            GarminConnectConnectionError,
            GarminConnectTooManyRequestsError,
        )

        try:
            return method(*args)
        except GarminConnectTooManyRequestsError as err:
            raise GarminRateLimitError(str(err)) from err
        except GarminConnectAuthenticationError as err:
            raise GarminAuthError(str(err)) from err
        except GarminConnectConnectionError as err:
            # The library reports a rejected token as a connection error
            # carrying the HTTP response.
            status = getattr(getattr(err, "response", None), "status_code", None)
            if status in (401, 403):
                raise GarminAuthError(str(err)) from err
            if status == 429:
                raise GarminRateLimitError(str(err)) from err
            raise GarminUnavailableError(str(err)) from err
