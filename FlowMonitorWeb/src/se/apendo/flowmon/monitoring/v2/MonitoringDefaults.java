package se.apendo.flowmon.monitoring.v2;

import java.time.LocalTime;
import java.time.ZoneId;

public final class MonitoringDefaults {
    public int warningLeadMinutes = 15;
    public int errorGraceMinutes = 0;
    public LocalTime businessEnd = LocalTime.of(17, 0);
    public ZoneId zoneId = ZoneId.of("Europe/Stockholm");
}
