package se.apendo.flowmon.monitoring.v2;

import java.time.LocalTime;

public final class IntervalRule {
    public LocalTime start;
    public LocalTime end;
    public int expected;
    public Double warnAtPct;
    public Double tolerancePct;

    public double resolvedWarnAtPct() {
        return warnAtPct != null ? warnAtPct.doubleValue() : 1.0d;
    }

    public double resolvedTolerancePct() {
        return tolerancePct != null ? tolerancePct.doubleValue() : 0.0d;
    }
}
