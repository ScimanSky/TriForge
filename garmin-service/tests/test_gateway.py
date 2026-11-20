from __future__ import annotations

from app.gateway import MAX_SWIM_SPLITS_PER_SYNC, swims_to_read

from .conftest import activity


def test_recent_swims_come_first_then_the_older_ones_without_laps():
    activities = [
        activity(1, "lap_swimming", start="2026-11-16 07:00:00"),
        activity(2, "running", start="2026-11-17 07:00:00"),
        activity(3, "lap_swimming", start="2026-11-17 18:00:00"),
        activity(4, "lap_swimming", start="2026-11-15 07:00:00"),
    ]

    read = swims_to_read(
        activities,
        swims_with_laps=frozenset({"4"}),
        # "1" is among the recent ones already, "4" has its laps.
        swims_without_laps=("9", "1", "4", "7"),
    )

    assert read == ["3", "1", "9", "7"]


def test_one_sync_never_reads_more_than_its_share_of_swims():
    older = tuple(str(i) for i in range(100, 160))
    recent = [activity(1, "lap_swimming")]

    read = swims_to_read(recent, swims_without_laps=older)

    assert len(read) == MAX_SWIM_SPLITS_PER_SYNC
    assert read[0] == "1"
    assert read[1:] == list(older[: MAX_SWIM_SPLITS_PER_SYNC - 1])
