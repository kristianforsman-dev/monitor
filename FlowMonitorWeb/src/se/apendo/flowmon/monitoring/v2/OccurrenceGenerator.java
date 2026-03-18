package se.apendo.flowmon.monitoring.v2;

import java.time.ZonedDateTime;
import java.util.List;

public interface OccurrenceGenerator {
    List<ExpectedOccurrence> generate(
        ZonedDateTime now,
        FlowMonitoringRule rule,
        MonitoringDefaults defaults
    );
}
