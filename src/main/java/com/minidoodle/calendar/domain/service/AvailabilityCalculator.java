package com.minidoodle.calendar.domain.service;

import com.minidoodle.calendar.domain.model.Availability;
import com.minidoodle.calendar.domain.model.AvailabilityInterval;
import com.minidoodle.calendar.domain.model.AvailabilityKind;
import com.minidoodle.calendar.domain.model.SlotOccupancy;
import com.minidoodle.calendar.domain.model.TimeFrame;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Framework-free domain service that turns the caller's slot occupancies into a single,
 * chronologically ordered list of disjoint intervals covering the requested frame exactly.
 *
 * <p>Each occupancy becomes a {@code FREE} or {@code BUSY} block clipped to the frame; same-kind
 * blocks that touch or overlap are coalesced; and the remaining time inside the frame is filled
 * with {@code UNALLOCATED} intervals. A frame with no blocks yields a single {@code UNALLOCATED}
 * interval spanning the whole frame.
 */
public final class AvailabilityCalculator {

    public Availability calculate(TimeFrame frame, List<SlotOccupancy> occupancies) {
        List<Block> blocks = clipToFrame(frame, occupancies);
        blocks.sort(Comparator.comparing(Block::start).thenComparing(Block::end));
        return new Availability(frame, fillGaps(frame, coalesce(blocks)));
    }

    private List<Block> clipToFrame(TimeFrame frame, List<SlotOccupancy> occupancies) {
        List<Block> blocks = new ArrayList<>(occupancies.size());
        for (SlotOccupancy occupancy : occupancies) {
            OffsetDateTime start = later(occupancy.range().start(), frame.start());
            OffsetDateTime end = earlier(occupancy.range().end(), frame.end());
            if (end.isAfter(start)) {
                blocks.add(
                        new Block(
                                start,
                                end,
                                occupancy.isBusy()
                                        ? AvailabilityKind.BUSY
                                        : AvailabilityKind.FREE));
            }
        }
        return blocks;
    }

    private List<Block> coalesce(List<Block> sortedBlocks) {
        List<Block> merged = new ArrayList<>();
        for (Block block : sortedBlocks) {
            Block last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.kind() == block.kind() && !block.start().isAfter(last.end())) {
                merged.set(
                        merged.size() - 1,
                        new Block(last.start(), later(last.end(), block.end()), last.kind()));
            } else {
                merged.add(block);
            }
        }
        return merged;
    }

    private List<AvailabilityInterval> fillGaps(TimeFrame frame, List<Block> blocks) {
        List<AvailabilityInterval> intervals = new ArrayList<>();
        OffsetDateTime cursor = frame.start();
        for (Block block : blocks) {
            if (block.start().isAfter(cursor)) {
                intervals.add(
                        new AvailabilityInterval(
                                cursor, block.start(), AvailabilityKind.UNALLOCATED));
            }
            intervals.add(new AvailabilityInterval(block.start(), block.end(), block.kind()));
            cursor = block.end();
        }
        if (cursor.isBefore(frame.end())) {
            intervals.add(
                    new AvailabilityInterval(cursor, frame.end(), AvailabilityKind.UNALLOCATED));
        }
        return intervals;
    }

    private static OffsetDateTime later(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static OffsetDateTime earlier(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private record Block(OffsetDateTime start, OffsetDateTime end, AvailabilityKind kind) {}
}
