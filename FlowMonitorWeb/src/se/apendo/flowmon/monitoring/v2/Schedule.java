package se.apendo.flowmon.monitoring.v2;

public abstract class Schedule {
    public final ScheduleType type;

    protected Schedule(ScheduleType type) {
        this.type = type;
    }
}
