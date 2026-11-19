from datetime import datetime, timezone

import pytest

from app.normalize import document_id, normalize_activity, sport_for

RUN = {
    "activityId": 12345678901,
    "activityName": "Corsa a Milano",
    "startTimeLocal": "2026-03-14 07:30:00",
    "startTimeGMT": "2026-03-14 06:30:00",
    "activityType": {"typeKey": "running"},
    "distance": 10012.4,
    "duration": 3001.6,
    "movingDuration": 2990.0,
    "elevationGain": 54.0,
    "averageSpeed": 3.336,
    "averageHR": 152.0,
    "maxHR": 171.0,
    "calories": 702.0,
    "averageRunningCadenceInStepsPerMinute": 172.4,
    "activityTrainingLoad": 131.7,
    "aerobicTrainingEffect": 3.4,
    "vO2MaxValue": 52.0,
}


@pytest.mark.parametrize(
    ("type_key", "sport"),
    [
        ("lap_swimming", "swim"),
        ("open_water_swimming", "swim"),
        ("cycling", "bike"),
        ("road_biking", "bike"),
        ("indoor_cycling", "bike"),
        ("virtual_ride", "bike"),
        ("gravel_cycling", "bike"),
        ("mountain_biking", "bike"),
        ("running", "run"),
        ("trail_running", "run"),
        ("treadmill_running", "run"),
        ("strength_training", "other"),
        ("walking", "other"),
        ("multi_sport", "other"),
        (None, "other"),
    ],
)
def test_sport_mapping(type_key, sport):
    assert sport_for(type_key) == sport


def test_run_is_normalized():
    doc = normalize_activity(RUN)
    assert doc["source"] == "garmin"
    assert doc["externalId"] == "12345678901"
    assert doc["sport"] == "run"
    assert doc["startTime"] == datetime(2026, 3, 14, 6, 30, tzinfo=timezone.utc)
    assert doc["startTimeLocal"] == "2026-03-14 07:30:00"
    assert doc["durationSec"] == 3002
    assert doc["distanceMeters"] == pytest.approx(10012.4)
    assert doc["avgHr"] == 152
    assert doc["avgCadence"] == 172
    assert doc["trainingLoad"] == pytest.approx(131.7)
    assert "swolf" not in doc


def test_pool_swim_keeps_swim_fields():
    doc = normalize_activity(
        {
            "activityId": 1,
            "startTimeGMT": "2026-03-15 18:00:00",
            "activityType": {"typeKey": "lap_swimming"},
            "distance": 2000.0,
            "duration": 3900.0,
            "movingDuration": 2400.4,
            "poolLength": 2500.0,
            "averageSwolf": 38.6,
            "strokes": 1480.0,
            "activeLengths": 80,
        }
    )
    assert doc["sport"] == "swim"
    assert doc["openWater"] is False
    assert doc["poolLengthMeters"] == 25.0
    assert doc["durationSec"] == 3900
    # The time spent swimming: the app reads the pace on this one.
    assert doc["movingDurationSec"] == 2400
    assert doc["swolf"] == 39
    assert doc["lengths"] == 80


@pytest.mark.parametrize(
    ("raw", "meters"),
    [
        (2500.0, 25.0),  # centimetres, as Garmin sends it
        (5000.0, 50.0),
        (2286.0, 22.86),  # 25 yd
        (25.0, 25.0),  # already metres
        (0.0, None),
        (None, None),
    ],
)
def test_pool_length_is_stored_in_metres(raw, meters):
    doc = normalize_activity(
        {
            "activityId": 1,
            "startTimeGMT": "2026-03-15 18:00:00",
            "activityType": {"typeKey": "lap_swimming"},
            "poolLength": raw,
        }
    )
    assert doc["poolLengthMeters"] == meters


def test_missing_optional_values_become_none_not_zero():
    doc = normalize_activity(
        {
            "activityId": 2,
            "startTimeGMT": "2026-03-15 18:00:00",
            "activityType": {"typeKey": "cycling"},
        }
    )
    assert doc["avgHr"] is None
    assert doc["avgPowerWatts"] is None
    assert doc["distanceMeters"] == 0.0
    assert doc["durationSec"] == 0


@pytest.mark.parametrize(
    "raw",
    [
        {"startTimeGMT": "2026-03-15 18:00:00"},
        {"activityId": 3},
        {"activityId": 3, "startTimeGMT": "not a date"},
    ],
)
def test_unplaceable_activities_are_dropped(raw):
    assert normalize_activity(raw) is None


def test_document_id_is_stable():
    assert document_id(987) == "garmin_987"
