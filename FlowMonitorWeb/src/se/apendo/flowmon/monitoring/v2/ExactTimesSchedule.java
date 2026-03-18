package se.apendo.flowmon.monitoring.v2;

import java.util.ArrayList;
import java.util.List;

public final class ExactTimesSchedule extends Schedule {
    public List<ExactTimeRule> times = new ArrayList<ExactTimeRule>();

    public ExactTimesSchedule() {
        super(ScheduleType.EXACT_TIMES);
    }
}
