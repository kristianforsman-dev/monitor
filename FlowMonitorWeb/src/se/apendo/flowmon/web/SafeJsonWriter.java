package se.apendo.flowmon.web;


import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal, robust JSON-writer utan externa bibliotek.
 * - Hanterar kommatecken korrekt (ingen trailing comma)
 * - Escapar strängar korrekt
 *
 * Användning:
 *   SafeJsonWriter jw = new SafeJsonWriter(resp.getWriter());
 *   jw.beginObject().name("x").value("y").name("arr").beginArray()...endArray().endObject().flush();
 */
public final class SafeJsonWriter {
    private final Writer out;

    private static final int CTX_OBJECT = 1;
    private static final int CTX_ARRAY = 2;

    private static final class Ctx {
        final int type;
        boolean first = true;
        boolean expectingValue = false; // endast för object efter name()
        Ctx(int type) { this.type = type; }
    }

    private final Deque<Ctx> stack = new ArrayDeque<Ctx>();

    public SafeJsonWriter(Writer out) { this.out = out; }

    public SafeJsonWriter beginObject() throws IOException {
        beforeValue();
        out.write('{');
        stack.push(new Ctx(CTX_OBJECT));
        return this;
    }

    public SafeJsonWriter endObject() throws IOException {
        if (stack.isEmpty() || stack.peek().type != CTX_OBJECT) throw new IllegalStateException("endObject utan beginObject");
        Ctx c = stack.pop();
        if (c.expectingValue) throw new IllegalStateException("Object saknar value efter name()");
        out.write('}');
        afterValue();
        return this;
    }

    public SafeJsonWriter beginArray() throws IOException {
        beforeValue();
        out.write('[');
        stack.push(new Ctx(CTX_ARRAY));
        return this;
    }

    public SafeJsonWriter endArray() throws IOException {
        if (stack.isEmpty() || stack.peek().type != CTX_ARRAY) throw new IllegalStateException("endArray utan beginArray");
        stack.pop();
        out.write(']');
        afterValue();
        return this;
    }

    public SafeJsonWriter name(String name) throws IOException {
        if (stack.isEmpty() || stack.peek().type != CTX_OBJECT) throw new IllegalStateException("name() endast i object");
        Ctx c = stack.peek();
        if (c.expectingValue) throw new IllegalStateException("name() kallad två gånger utan value()");
        if (!c.first) out.write(',');
        c.first = false;
        writeString(name);
        out.write(':');
        c.expectingValue = true;
        return this;
    }

    public SafeJsonWriter value(String s) throws IOException {
        beforeValue();
        if (s == null) out.write("null");
        else writeString(s);
        afterValue();
        return this;
    }

    public SafeJsonWriter value(long n) throws IOException {
        beforeValue();
        out.write(Long.toString(n));
        afterValue();
        return this;
    }

    public SafeJsonWriter value(boolean b) throws IOException {
        beforeValue();
        out.write(b ? "true" : "false");
        afterValue();
        return this;
    }

    public SafeJsonWriter nullValue() throws IOException {
        beforeValue();
        out.write("null");
        afterValue();
        return this;
    }

    public void flush() throws IOException { out.flush(); }

    private void beforeValue() throws IOException {
        if (stack.isEmpty()) return;

        Ctx c = stack.peek();
        if (c.type == CTX_OBJECT) {
            if (!c.expectingValue) throw new IllegalStateException("value() i object utan name()");
            // kommatecken för object hanteras i name()
        } else { // array
            if (!c.first) out.write(',');
            c.first = false;
        }
    }

    private void afterValue() {
        if (stack.isEmpty()) return;
        Ctx c = stack.peek();
        if (c.type == CTX_OBJECT) c.expectingValue = false;
    }

    private void writeString(String s) throws IOException {
        out.write('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': out.write("\\\""); break;
                case '\\': out.write("\\\\"); break;
                case '\b': out.write("\\b"); break;
                case '\f': out.write("\\f"); break;
                case '\n': out.write("\\n"); break;
                case '\r': out.write("\\r"); break;
                case '\t': out.write("\\t"); break;
                default:
                    if (ch < 0x20) {
                        out.write("\\u");
                        String hex = Integer.toHexString(ch);
                        for (int k = hex.length(); k < 4; k++) out.write('0');
                        out.write(hex);
                    } else {
                        out.write(ch);
                    }
            }
        }
        out.write('"');
    }
}