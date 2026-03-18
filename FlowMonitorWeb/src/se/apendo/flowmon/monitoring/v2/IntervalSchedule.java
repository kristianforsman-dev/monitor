package se.apendo.flowmon.monitoring.v2;

import java.util.ArrayList;
import java.util.List;

public final class IntervalSchedule extends Schedule {
    public List<IntervalRule> intervals = new ArrayList<IntervalRule>();

    public IntervalSchedule() {
        super(ScheduleType.INTERVAL);
    }
}
