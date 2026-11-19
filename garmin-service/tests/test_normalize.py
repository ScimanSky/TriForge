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


# -- swim laps --------------------------------------------------------------

POOL_SWIM = {
    "activityId": 555,
    "activityName": "Soglia 5 x 200",
    "startTimeGMT": "2026-11-12 06:10:00",
    "activityType": {"typeKey": "lap_swimming"},
    "distance": 400.0,
    "duration": 500.0,
    "movingDuration": 440.0,
    "poolLength": 2500.0,
    "averageSwolf": 37.0,
    "strokes": 260.0,
    "activeLengths": 16,
    "avgStrokeDistance": 1.71,
}


def _length(seconds, strokes, stroke="FREESTYLE"):
    return {
        "distance": 25.0,
        "duration": seconds,
        "totalNumberOfStrokes": strokes,
        "averageSWOLF": seconds + strokes,
        "swimStroke": stroke,
    }


SPLITS = {
    "lapDTOs": [
        {
            "distance": 200.0,
            "duration": 214.3,
            "totalNumberOfStrokes": 120,
            "averageSWOLF": 42,
            "averageHR": 140.0,
            "maxHR": 151.0,
            "lengthDTOs": [_length(26.8, 15) for _ in range(7)]
            + [_length(26.7, 16, "BACKSTROKE")],
        },
        {"distance": 0.0, "duration": 30.2},
        {"distance": 200.0, "duration": 225.7, "lengthDTOs": [{"duration": None}]},
    ]
}


def test_swim_without_splits_has_no_laps_key():
    doc = normalize_activity(POOL_SWIM)
    assert doc["strokeDistanceMeters"] == pytest.approx(1.71)
    assert "laps" not in doc
    assert "hasLaps" not in doc


def test_swim_laps_are_normalized():
    doc = normalize_activity(POOL_SWIM, SPLITS)
    assert doc["hasLaps"] is True
    first, rest, third = doc["laps"]
    assert first["distanceMeters"] == 200.0
    assert first["durationSec"] == 214.3
    assert first["strokes"] == 120
    assert first["swolf"] == 42
    assert first["avgHr"] == 140
    assert first["stroke"] == "free"
    assert len(first["lengths"]) == 8
    assert first["lengths"][-1] == {
        "distanceMeters": 25.0,
        "durationSec": 26.7,
        "strokes": 16,
        "swolf": 43,
        "stroke": "back",
    }
    assert rest == {"distanceMeters": 0.0, "durationSec": 30.2}
    # Lengths without a duration are dropped, and with them the list.
    assert "lengths" not in third


def test_swim_with_empty_splits_is_marked_as_read():
    doc = normalize_activity(POOL_SWIM, {"lapDTOs": []})
    assert doc["laps"] == []
    assert doc["hasLaps"] is False


def test_splits_on_a_run_are_ignored():
    doc = normalize_activity(RUN, SPLITS)
    assert "laps" not in doc
