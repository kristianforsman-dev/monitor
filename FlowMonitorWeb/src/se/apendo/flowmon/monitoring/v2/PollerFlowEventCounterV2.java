package se.apendo.flowmon.monitoring.v2;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import se.apendo.flowmon.core.PollerService;

public final class PollerFlowEventCounterV2 implements FlowEventCounter {

    private final PollerService poller;
    private final ZoneId zoneId;

    public PollerFlowEventCounterV2(PollerService poller, ZoneId zoneId) {
        this.poller = poller;
        this.zoneId = zoneId;
    }

    @Override
    public int countFinishedBetween(String flowId, ZonedDateTime from, ZonedDateTime to) throws Exception {
        if (poller == null || flowId == null) return 0;
        if (from == null || to == null) return 0;

        // Current PollerService only supports today's local-time buckets.
        LocalTime start = from.withZoneSameInstant(zoneId).toLocalTime();
        LocalTime end = to.withZoneSameInstant(zoneId).toLocalTime();

        if (!start.isBefore(end)) return 0;

        long v = poller.getFinishedBetween(flowId, start, end);
        if (v < 0L) return 0;
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) v;
    }

    @Override
    public ZonedDateTime getLastFinishedCompletedBetween(String flowId, ZonedDateTime from, ZonedDateTime to) throws Exception {
        // Not available yet in current PollerService.
        // First V2 integration uses getLastFinishedCompleted(flowId) only.
        return null;
    }

    @Override
    public ZonedDateTime getLastFinishedCompleted(String flowId) throws Exception {
        if (poller == null || flowId == null) return null;
        Timestamp ts = poller.getLastFinishedCompleted(flowId);
        if (ts == null) return null;
        return ZonedDateTime.ofInstant(ts.toInstant(), zoneId);
    }
}
