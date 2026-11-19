"""Thin wrapper around the unofficial ``garminconnect`` library.

Everything that touches Garmin goes through ``GarminGateway`` so the rest of
the service, and the tests, never import the library directly. If Garmin
changes its login flow or the library is replaced, this file is the only one
that has to change.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date, timedelta
from typing import Any, Callable, Protocol

logger = logging.getLogger(__name__)

# Garmin's daily sleep and HRV range endpoints answer at most four weeks at a
# time, so longer windows are read in chunks of this many days.
HEALTH_CHUNK_DAYS = 28

# Laps and pool lengths of a swim need one extra request per swim. A first
# sync of four months can hold fifty swims, so each sync reads at most this
# many and the rest follow on the next syncs, to stay clear of Garmin's
# request limits.
MAX_SWIM_SPLITS_PER_SYNC = 25


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


@dataclass(frozen=True)
class HealthData:
    """Daily recovery data, exactly as Garmin returns it.

    - ``hrv``: one ``hrvSummaries`` row per night (``lastNightAvg``,
      ``weeklyAvg``, ``status``, ``baseline``).
    - ``resting_hr``: ``{"calendarDate", "value"}`` per day.
    - ``sleep``: one ``individualStats`` row per night from the sleep stats
      endpoint (``calendarDate`` plus a ``values`` map).
    """

    hrv: list[dict[str, Any]] = field(default_factory=list)
    resting_hr: list[dict[str, Any]] = field(default_factory=list)
    sleep: list[dict[str, Any]] = field(default_factory=list)


@dataclass(frozen=True)
class GarminData:
    activities: list[dict[str, Any]]
    health: HealthData
    session: Session
    # Garmin's ``get_activity_splits`` answer (``lapDTOs`` with their
    # ``lengthDTOs``) for the swims read this time, by activity id.
    swim_splits: dict[str, dict[str, Any]] = field(default_factory=dict)


def is_swim(raw: dict[str, Any]) -> bool:
    activity_type = raw.get("activityType")
    key = activity_type.get("typeKey") if isinstance(activity_type, dict) else None
    return "swimming" in (key or "").lower()


class GarminGateway(Protocol):
    def login(self, email: str, password: str) -> Session | MfaChallenge: ...

    def complete_mfa(self, state: Any, code: str) -> Session: ...

    def fetch(
        self,
        tokens: str,
        start: date,
        end: date,
        swims_with_laps: frozenset[str] = frozenset(),
    ) -> GarminData: ...


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

    def fetch(
        self,
        tokens: str,
        start: date,
        end: date,
        swims_with_laps: frozenset[str] = frozenset(),
    ) -> GarminData:
        """Activities and daily recovery data between ``start`` and ``end``.

        One login serves every call. Recovery data and swim laps are best
        effort: an endpoint that fails for any reason other than auth or rate
        limiting is skipped, so a Garmin change there never blocks the
        activity sync. ``swims_with_laps`` are the swims whose laps are
        already stored, so they are not read again.
        """
        from garminconnect import Garmin

        client = Garmin()
        # A JSON string is treated as inline token data, not as a path.
        self._call(client.login, tokens)
        activities = self._call(
            client.get_activities_by_date, start.isoformat(), end.isoformat()
        )
        health = HealthData(
            hrv=self._chunked(
                lambda a, b: (self._call(client.get_hrv_data_range, a, b) or {}).get(
                    "hrvSummaries"
                )
                or [],
                start,
                end,
            ),
            resting_hr=self._optional(
                lambda: self._call(
                    client.get_rhr_daily, start.isoformat(), end.isoformat()
                )
            ),
            # The library already splits this one into four-week requests.
            sleep=self._optional(
                lambda: self._call(
                    client.get_sleep_daily, start.isoformat(), end.isoformat()
                )
            ),
        )
        activities = list(activities or [])
        swim_splits: dict[str, dict[str, Any]] = {}
        swims = [
            raw
            for raw in activities
            if isinstance(raw, dict)
            and raw.get("activityId") is not None
            and is_swim(raw)
            and str(raw.get("activityId")) not in swims_with_laps
        ]
        # Newest first: the swims the athlete is about to open.
        swims.sort(key=lambda raw: str(raw.get("startTimeGMT") or ""), reverse=True)
        pending = [str(raw["activityId"]) for raw in swims]
        for activity_id in pending[:MAX_SWIM_SPLITS_PER_SYNC]:
            splits = self._optional_dict(
                lambda i=activity_id: self._call(client.get_activity_splits, i)
            )
            if splits is not None:
                swim_splits[activity_id] = splits
        # The library refreshes tokens while it works, so hand the current
        # ones back to be stored.
        return GarminData(activities, health, self._session(client), swim_splits)

    @classmethod
    def _chunked(
        cls, read: Callable[[str, str], list[dict[str, Any]]], start: date, end: date
    ) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        chunk_start = start
        while chunk_start <= end:
            chunk_end = min(chunk_start + timedelta(days=HEALTH_CHUNK_DAYS - 1), end)
            rows += cls._optional(
                lambda a=chunk_start, b=chunk_end: read(a.isoformat(), b.isoformat())
            )
            chunk_start = chunk_end + timedelta(days=1)
        return rows

    @staticmethod
    def _optional(read: Callable[[], Any]) -> list[dict[str, Any]]:
        try:
            rows = read()
        except (GarminAuthError, GarminRateLimitError):
            raise
        except Exception as err:  # noqa: BLE001 - recovery data is best effort
            logger.warning("health data skipped: %s", type(err).__name__)
            return []
        return [row for row in rows or [] if isinstance(row, dict)]

    @staticmethod
    def _optional_dict(read: Callable[[], Any]) -> dict[str, Any] | None:
        try:
            value = read()
        except (GarminAuthError, GarminRateLimitError):
            raise
        except Exception as err:  # noqa: BLE001 - swim laps are best effort
            logger.warning("swim laps skipped: %s", type(err).__name__)
            return None
        return value if isinstance(value, dict) else None

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
