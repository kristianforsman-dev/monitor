package se.apendo.flowmon.core;

public final class FlowIds {
    private FlowIds() {}

    public static String build(String sender, String receiver, String msgType) {
        return safe(sender) + "||" + safe(receiver) + "||" + safe(msgType);
    }

    public static String[] split(String flowId) {
        String s = flowId == null ? "" : flowId.trim();
        String[] p = s.split("\\|\\|", 3);
        String[] out = new String[] { "", "", "" };
        for (int i = 0; i < p.length && i < 3; i++) {
            out[i] = p[i] == null ? "" : p[i].trim();
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
