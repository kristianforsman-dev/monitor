package se.apendo.flowmon.monitoring.v2;

public final class FlowMonitoringRule {
    public String flowId;
    public String name;
    public boolean enabled = true;

    public Integer warningLeadMinutes;
    public Integer errorGraceMinutes;
    public String businessEnd;

    public Schedule schedule;

    public int resolveWarningLeadMinutes(MonitoringDefaults dflt) {
        return warningLeadMinutes != null ? warningLeadMinutes.intValue() : dflt.warningLeadMinutes;
    }

    public int resolveErrorGraceMinutes(MonitoringDefaults dflt) {
        return errorGraceMinutes != null ? errorGraceMinutes.intValue() : dflt.errorGraceMinutes;
    }
}
