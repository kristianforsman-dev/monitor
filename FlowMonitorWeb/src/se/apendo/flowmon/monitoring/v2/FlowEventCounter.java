package se.apendo.flowmon.monitoring.v2;

import java.time.ZonedDateTime;

public interface FlowEventCounter {
    int countFinishedBetween(String flowId, ZonedDateTime from, ZonedDateTime to) throws Exception;
    ZonedDateTime getLastFinishedCompletedBetween(String flowId, ZonedDateTime from, ZonedDateTime to) throws Exception;
    ZonedDateTime getLastFinishedCompleted(String flowId) throws Exception;
}
