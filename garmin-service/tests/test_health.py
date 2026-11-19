from __future__ import annotations

from datetime import date

import pytest

from app.gateway import (
    GarminConnectGateway,
    GarminRateLimitError,
    HealthData,
)
from app.normalize import normalize_health


def hrv_row(day: str, last_night: float = 62.0, **extra):
    row = {
        "calendarDate": day,
        "weeklyAvg": 60.0,
        "lastNightAvg": last_night,
        "status": "BALANCED",
        "baseline": {"lowUpper": 48, "balancedLow": 52, "balancedUpper": 70},
    }
    row.update(extra)
    return row


def test_merges_hrv_resting_hr_and_sleep_into_one_document_per_day():
    docs = normalize_health(
        hrv=[hrv_row("2026-11-17")],
        resting_hr=[{"calendarDate": "2026-11-17", "value": 47}],
        sleep=[
            {
                "calendarDate": "2026-11-17",
                "values": {"totalSleepTimeInSeconds": 27000, "sleepScore": 81},
            }
        ],
    )
    assert docs == {
        "garmin_2026-11-17": {
            "source": "garmin",
            "date": "2026-11-17",
            "hrvRmssd": 62.0,
            "hrvWeeklyAvg": 60.0,
            "hrvStatus": "BALANCED",
            "hrvBaselineLow": 52.0,
            "hrvBaselineHigh": 70.0,
            "restingHr": 47,
            "sleepSec": 27000,
            "sleepScore": 81,
        }
    }


def test_sleep_values_fill_gaps_but_do_not_override_dedicated_endpoints():
    docs = normalize_health(
        hrv=[],
        resting_hr=[{"calendarDate": "2026-11-17", "value": 47}],
        sleep=[
            {
                "calendarDate": "2026-11-17",
                "values": {"restingHeartRate": 50, "avgOvernightHrv": 58.5},
            }
        ],
    )
    doc = docs["garmin_2026-11-17"]
    assert doc["restingHr"] == 47
    assert doc["hrvRmssd"] == 58.5


def test_skips_days_without_values_and_rows_without_a_valid_date():
    docs = normalize_health(
        hrv=[hrv_row("not-a-date"), {"calendarDate": "2026-11-16", "lastNightAvg": None}],
        resting_hr=[{"calendarDate": "2026-11-15", "value": 0}],
        sleep=[],
    )
    assert docs == {}


def test_sync_writes_wellness_documents(service, gateway, store):
    service.connect("u1", "a@b.it", "pw")
    gateway.health = HealthData(
        hrv=[hrv_row("2026-11-18")],
        resting_hr=[{"calendarDate": "2026-11-18", "value": 46}],
    )
    service.sync("u1")
    doc = store.wellness["u1"]["garmin_2026-11-18"]
    assert doc["hrvRmssd"] == 62.0
    assert doc["restingHr"] == 46
    assert "syncedAt" in doc
    assert store.status["u1"]["lastSyncHealthDays"] == 1


def test_health_ranges_are_read_in_four_week_chunks():
    calls: list[tuple[str, str]] = []

    def read(start: str, end: str):
        calls.append((start, end))
        return [{"calendarDate": start}]

    rows = GarminConnectGateway._chunked(read, date(2026, 1, 1), date(2026, 3, 1))
    assert calls == [
        ("2026-01-01", "2026-01-28"),
        ("2026-01-29", "2026-02-25"),
        ("2026-02-26", "2026-03-01"),
    ]
    assert len(rows) == 3


def test_a_failing_health_endpoint_is_skipped_but_rate_limits_are_not():
    def broken():
        raise KeyError("shape changed")

    assert GarminConnectGateway._optional(broken) == []

    def limited():
        raise GarminRateLimitError("429")

    with pytest.raises(GarminRateLimitError):
        GarminConnectGateway._optional(limited)
