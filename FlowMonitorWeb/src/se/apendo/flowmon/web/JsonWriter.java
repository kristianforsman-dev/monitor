package se.apendo.flowmon.web;

import java.io.Writer;

public final class JsonWriter {
    private final Writer out;
    private boolean needComma = false;

    public JsonWriter(Writer out) { this.out = out; }

    public JsonWriter beginObject() { write("{"); needComma = false; return this; }
    public JsonWriter endObject() { write("}"); needComma = true; return this; }
    public JsonWriter beginArray() { write("["); needComma = false; return this; }
    public JsonWriter endArray() { write("]"); needComma = true; return this; }

    public JsonWriter name(String n) {
        commaIfNeeded();
        write("\"" + esc(n) + "\":");
        needComma = false;
        return this;
    }

    public JsonWriter value(String v) {
        commaIfNeeded();
        if (v == null) write("null");
        else write("\"" + esc(v) + "\"");
        needComma = true;
        return this;
    }

    public JsonWriter value(long v) {
        commaIfNeeded();
        write(Long.toString(v));
        needComma = true;
        return this;
    }

    public JsonWriter value(boolean v) {
        commaIfNeeded();
        write(v ? "true" : "false");
        needComma = true;
        return this;
    }

    public void flush() {
        try { out.flush(); } catch (Exception ignore) {}
    }

    private void commaIfNeeded() { if (needComma) write(","); }

    private void write(String s) {
        try { out.write(s); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) sb.append(' ');
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
