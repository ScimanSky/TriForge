"""Turn a Garmin Connect activity summary into a TriForge activity document.

The output is the shape stored in Firestore under
``users/{uid}/activities/{id}`` and read by the Android app. Field names are
camelCase because the app reads them directly.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

SWIM_TYPES = {"swimming", "lap_swimming", "open_water_swimming"}

# Garmin's typeKey has many variants per sport. Anything containing one of
# these markers belongs to that sport; the explicit sets cover the rest.
BIKE_TYPES = {
    "cycling",
    "bmx",
    "cyclocross",
    "virtual_ride",
    "recumbent_cycling",
}
RUN_TYPES = {"running", "virtual_run", "obstacle_run", "ultra_run"}
# The gym: the app shows it as a sport of its own, beside the three.
STRENGTH_TYPES = {"strength_training", "weight_training", "weightlifting", "calisthenics"}


def sport_for(type_key: str | None) -> str:
    """Map a Garmin activity typeKey to swim, bike, run, strength or other.

    Everything the watch records is synced. What is not one of the three
    disciplines or the gym is "other": the app reads what it was (a walk, a
    hike, yoga, cardio) from the ``type`` field, which keeps Garmin's typeKey.
    """
    key = (type_key or "").lower()
    if key in SWIM_TYPES or "swimming" in key:
        return "swim"
    if key in BIKE_TYPES or "cycling" in key or "biking" in key or "bike" in key:
        return "bike"
    if key in RUN_TYPES or key.endswith("_running"):
        return "run"
    if key in STRENGTH_TYPES:
        return "strength"
    return "other"


def _number(value: Any) -> float | None:
    if isinstance(value, bool) or value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None


def _rounded(value: Any) -> int | None:
    number = _number(value)
    return None if number is None else int(round(number))


# Garmin gives the pool length in centimetres (2500.0 for a 25 m pool, 2286.0
# for 25 yd), whatever unit the watch shows. Some payloads carry metres, so a
# value no pool reaches is taken as centimetres.
_LONGEST_POOL_METERS = 200.0


def _pool_length_meters(value: Any) -> float | None:
    length = _number(value)
    if length is None or length <= 0:
        return None
    return length / 100.0 if length > _LONGEST_POOL_METERS else length


def _parse_gmt(value: Any) -> datetime | None:
    """Parse Garmin's ``startTimeGMT`` ("2026-03-14 06:30:00") as UTC."""
    if not isinstance(value, str) or not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S.%f", "%Y-%m-%dT%H:%M:%S"):
        try:
            return datetime.strptime(value, fmt).replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    return None


def document_id(garmin_activity_id: Any) -> str:
    return f"garmin_{garmin_activity_id}"


def normalize_activity(
    raw: dict[str, Any], splits: dict[str, Any] | None = None
) -> dict[str, Any] | None:
    """Return the Firestore document for one Garmin activity.

    ``splits`` is Garmin's lap breakdown of a swim, when it was read. Without
    it the document carries no ``laps`` key, so laps stored by an earlier
    sync are kept (documents are merged, not replaced).

    Returns None when the activity has no id or no start time, because it
    could not be placed on a timeline or deduplicated.
    """
    activity_id = raw.get("activityId")
    start = _parse_gmt(raw.get("startTimeGMT"))
    if activity_id is None or start is None:
        return None

    activity_type = raw.get("activityType") or {}
    type_key = activity_type.get("typeKey") if isinstance(activity_type, dict) else None
    sport = sport_for(type_key)

    cadence = (
        raw.get("averageRunningCadenceInStepsPerMinute")
        if sport == "run"
        else raw.get("averageBikingCadenceInRevPerMinute")
        if sport == "bike"
        else raw.get("averageSwimCadenceInStrokesPerMinute")
    )

    doc: dict[str, Any] = {
        "source": "garmin",
        "externalId": str(activity_id),
        "sport": sport,
        "type": type_key or "unknown",
        "name": raw.get("activityName") or "",
        "startTime": start,
        "startTimeLocal": raw.get("startTimeLocal") or "",
        "durationSec": _rounded(raw.get("duration")) or 0,
        "movingDurationSec": _rounded(raw.get("movingDuration")),
        "distanceMeters": _number(raw.get("distance")) or 0.0,
        "elevationGainMeters": _number(raw.get("elevationGain")),
        "avgHr": _rounded(raw.get("averageHR")),
        "maxHr": _rounded(raw.get("maxHR")),
        "calories": _rounded(raw.get("calories")),
        "avgSpeedMps": _number(raw.get("averageSpeed")),
        "avgPowerWatts": _rounded(raw.get("avgPower")),
        "normPowerWatts": _rounded(raw.get("normPower")),
        "avgCadence": _rounded(cadence),
        "trainingLoad": _number(raw.get("activityTrainingLoad")),
        "aerobicTe": _number(raw.get("aerobicTrainingEffect")),
        "anaerobicTe": _number(raw.get("anaerobicTrainingEffect")),
        "vo2max": _number(raw.get("vO2MaxValue")),
    }

    if sport == "strength":
        doc.update(_strength_fields(raw))

    if sport == "swim":
        doc["openWater"] = type_key == "open_water_swimming"
        doc["poolLengthMeters"] = _pool_length_meters(raw.get("poolLength"))
        doc["swolf"] = _rounded(raw.get("averageSwolf"))
        doc["strokes"] = _rounded(raw.get("strokes"))
        doc["lengths"] = _rounded(raw.get("activeLengths"))
        doc["strokeDistanceMeters"] = _positive(raw.get("avgStrokeDistance"))
        if splits is not None:
            laps = normalize_swim_laps(splits)
            doc["laps"] = laps
            doc["hasLaps"] = bool(laps)

    return doc


# -- gym --------------------------------------------------------------------

# Garmin gives the weight moved in grams (and times in milliseconds).
_HEAVIEST_REP_KG = 600.0


def _volume_kg(volume: Any, reps: int | None) -> float | None:
    """The weight moved in an exercise, in kilograms, or None when unknown."""
    grams = _positive(volume)
    if grams is None or not reps:
        return None
    kilograms = grams / 1000.0
    # A number nobody lifts is a payload this code does not understand.
    if kilograms / reps > _HEAVIEST_REP_KG:
        return None
    return round(kilograms, 1)


def _strength_fields(raw: dict[str, Any]) -> dict[str, Any]:
    """Sets, repetitions and exercises of a gym session, when the watch counted them.

    They come with the activity summary, so they cost no extra request:

    - ``sets``: the working sets (Garmin ``activeSets``, else ``totalSets``).
    - ``reps``: all the repetitions.
    - ``exercises``: one entry per exercise category, in Garmin's order, with
      ``category`` (lower case, as Garmin names it: "squat", "bench_press"),
      ``sets``, ``reps`` and ``volumeKg``, the weight moved in all its sets.

    A session recorded without counting sets carries none of them.
    """
    fields: dict[str, Any] = {
        "sets": _rounded(_positive(raw.get("activeSets")) or _positive(raw.get("totalSets"))),
        "reps": _rounded(_positive(raw.get("totalReps"))),
    }
    exercises: list[dict[str, Any]] = []
    summarized = raw.get("summarizedExerciseSets")
    for item in summarized if isinstance(summarized, list) else []:
        if not isinstance(item, dict):
            continue
        category = item.get("category")
        sets = _rounded(_positive(item.get("sets")))
        if not isinstance(category, str) or not category.strip() or not sets:
            continue
        reps = _rounded(_positive(item.get("reps")))
        exercises.append(
            _compact(
                {
                    "category": category.strip().lower(),
                    "sets": sets,
                    "reps": reps,
                    "volumeKg": _volume_kg(item.get("volume"), reps),
                }
            )
        )
    if exercises:
        fields["exercises"] = exercises
    return fields


# -- swim laps --------------------------------------------------------------

# Garmin's swimStroke values, shortened. Anything else is kept lower-cased.
_STROKES = {
    "FREESTYLE": "free",
    "BACKSTROKE": "back",
    "BREASTSTROKE": "breast",
    "BUTTERFLY": "fly",
    "DRILL": "drill",
    "MIXED": "mixed",
    "IM": "mixed",
}


def _stroke(value: Any) -> str | None:
    if not isinstance(value, str) or not value:
        return None
    return _STROKES.get(value.upper(), value.lower())


def _compact(fields: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in fields.items() if value is not None}


def normalize_swim_laps(splits: dict[str, Any]) -> list[dict[str, Any]]:
    """Garmin's ``lapDTOs`` as the ``laps`` list of a swim document.

    In a pool swim each lap is one interval between presses of the lap button
    and a rest at the wall is a lap with no distance; each lap holds its pool
    lengths. In open water the laps are the watch's auto laps and carry no
    lengths. Per lap:

    - ``distanceMeters``, ``durationSec``: the rest of a rest lap is its time.
    - ``strokes``, ``swolf``, ``avgHr``, ``maxHr``, ``stroke`` when measured;
      ``stroke`` is the one swum over most lengths.
    - ``lengths``: ``distanceMeters``, ``durationSec``, ``strokes``,
      ``swolf``, ``stroke`` per length.
    """
    raw_laps = splits.get("lapDTOs") if isinstance(splits, dict) else None
    if not isinstance(raw_laps, list):
        return []
    laps: list[dict[str, Any]] = []
    for raw in raw_laps:
        if not isinstance(raw, dict):
            continue
        duration = _positive(raw.get("duration"))
        if duration is None:
            continue
        lengths = []
        for length in raw.get("lengthDTOs") or []:
            if not isinstance(length, dict):
                continue
            length_duration = _positive(length.get("duration"))
            if length_duration is None:
                continue
            lengths.append(
                _compact(
                    {
                        "distanceMeters": _number(length.get("distance")) or 0.0,
                        "durationSec": round(length_duration, 1),
                        "strokes": _rounded(_positive(length.get("totalNumberOfStrokes"))),
                        "swolf": _rounded(
                            _positive(length.get("averageSWOLF", length.get("averageSwolf")))
                        ),
                        "stroke": _stroke(length.get("swimStroke")),
                    }
                )
            )
        strokes_by_type: dict[str, float] = {}
        for length in lengths:
            if "stroke" in length:
                strokes_by_type[length["stroke"]] = (
                    strokes_by_type.get(length["stroke"], 0.0) + length["distanceMeters"]
                )
        stroke = (
            max(strokes_by_type, key=strokes_by_type.get)
            if strokes_by_type
            else _stroke(raw.get("swimStroke"))
        )
        laps.append(
            _compact(
                {
                    "distanceMeters": _number(raw.get("distance")) or 0.0,
                    "durationSec": round(duration, 1),
                    "strokes": _rounded(_positive(raw.get("totalNumberOfStrokes"))),
                    "swolf": _rounded(
                        _positive(raw.get("averageSWOLF", raw.get("averageSwolf")))
                    ),
                    "avgHr": _rounded(_positive(raw.get("averageHR"))),
                    "maxHr": _rounded(_positive(raw.get("maxHR"))),
                    "stroke": stroke,
                    "lengths": lengths or None,
                }
            )
        )
    return laps


# -- daily recovery data ----------------------------------------------------


def _positive(value: Any) -> float | None:
    number = _number(value)
    return number if number is not None and number > 0 else None


def _valid_date(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    try:
        return datetime.strptime(value[:10], "%Y-%m-%d").date().isoformat()
    except ValueError:
        return None


def health_document_id(calendar_date: str) -> str:
    return f"garmin_{calendar_date}"


def normalize_health(
    hrv: list[dict[str, Any]],
    resting_hr: list[dict[str, Any]],
    sleep: list[dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    """Merge Garmin's daily recovery rows into one document per day.

    The result maps a document id to the fields stored under
    ``users/{uid}/wellness/{id}``. ``date`` is the morning the values belong
    to: Garmin files a night's sleep and HRV under the day you wake up.

    Fields, all optional except ``source`` and ``date``:

    - ``hrvRmssd``: average overnight RMSSD in ms (Garmin ``lastNightAvg``).
    - ``hrvWeeklyAvg``, ``hrvStatus``, ``hrvBaselineLow``, ``hrvBaselineHigh``:
      Garmin's own 7-day average, status and personal normal range.
    - ``restingHr``: resting heart rate in bpm.
    - ``sleepSec``: time asleep. ``sleepScore``: Garmin's 0-100 score.
    """
    days: dict[str, dict[str, Any]] = {}

    def day(value: Any) -> dict[str, Any] | None:
        calendar_date = _valid_date(value)
        if calendar_date is None:
            return None
        return days.setdefault(
            calendar_date, {"source": "garmin", "date": calendar_date}
        )

    for row in hrv:
        doc = day(row.get("calendarDate"))
        if doc is None:
            continue
        baseline = row.get("baseline") if isinstance(row.get("baseline"), dict) else {}
        status = row.get("status")
        doc.update(
            {
                "hrvRmssd": _positive(row.get("lastNightAvg")),
                "hrvWeeklyAvg": _positive(row.get("weeklyAvg")),
                "hrvStatus": status if isinstance(status, str) else None,
                "hrvBaselineLow": _positive(baseline.get("balancedLow")),
                "hrvBaselineHigh": _positive(baseline.get("balancedUpper")),
            }
        )

    for row in resting_hr:
        doc = day(row.get("calendarDate"))
        if doc is not None:
            doc["restingHr"] = _rounded(_positive(row.get("value")))

    for row in sleep:
        doc = day(row.get("calendarDate"))
        if doc is None:
            continue
        values = row.get("values") if isinstance(row.get("values"), dict) else row
        sleep_sec = _positive(
            values.get("totalSleepTimeInSeconds", values.get("sleepTimeSeconds"))
        )
        doc["sleepSec"] = _rounded(sleep_sec)
        doc["sleepScore"] = _rounded(_positive(values.get("sleepScore")))
        # The sleep endpoint repeats resting HR and HRV; use them only to fill
        # a gap, because the dedicated endpoints are the reference.
        if doc.get("restingHr") is None:
            doc["restingHr"] = _rounded(_positive(values.get("restingHeartRate")))
        if doc.get("hrvRmssd") is None:
            doc["hrvRmssd"] = _positive(values.get("avgOvernightHrv"))

    result: dict[str, dict[str, Any]] = {}
    for calendar_date, doc in days.items():
        fields = {key: value for key, value in doc.items() if value is not None}
        if len(fields) > 2:  # more than source and date
            result[health_document_id(calendar_date)] = fields
    return result
