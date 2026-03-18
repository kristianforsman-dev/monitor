package se.apendo.flowmon.monitoring.v2;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class EvaluationResult {
    public Status status = Status.INFO;
    public int rank = 1;

    public String message = "";
    public String details = "";

    public ZonedDateTime nextDueAt;

    public List<OccurrenceResult> occurrences = new ArrayList<OccurrenceResult>();
}
