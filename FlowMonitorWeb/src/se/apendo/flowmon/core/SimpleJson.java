package se.apendo.flowmon.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON-parser (ingen extern lib). Stöder:
 * - object { "k": v }
 * - array [ v, ... ]
 * - string, number, true/false, null
 *
 * Returnerar Java-typer:
 * - Map<String,Object>, List<Object>, String, Long/Double, Boolean, null
 */
public final class SimpleJson {

    private final String s;
    private int i = 0;

    private SimpleJson(String s) {
        this.s = (s == null) ? "" : s;
    }

    public static Object parse(String s) {
        SimpleJson p = new SimpleJson(s);
        Object v = p.readValue();
        p.skipWs();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : new HashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        return (o instanceof List) ? (List<Object>) o : new ArrayList<Object>();
    }

    public static String asString(Object o, String def) {
        return (o instanceof String) ? (String) o : def;
    }

    public static long asLong(Object o, long def) {
        if (o instanceof Number) return ((Number) o).longValue();
        return def;
    }

    public static boolean asBool(Object o, boolean def) {
        return (o instanceof Boolean) ? ((Boolean) o).booleanValue() : def;
    }

    private Object readValue() {
        skipWs();
        if (eof()) return null;

        char c = ch();
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == '"') return readString();
        if (c == 't') { expect("true"); return Boolean.TRUE; }
        if (c == 'f') { expect("false"); return Boolean.FALSE; }
        if (c == 'n') { expect("null"); return null; }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        Map<String, Object> m = new HashMap<String, Object>();
        expect('{');
        skipWs();
        if (peek('}')) { i++; return m; }

        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            expect(':');
            Object val = readValue();
            m.put(key, val);

            skipWs();
            if (peek('}')) { i++; break; }
            expect(',');
        }
        return m;
    }

    private List<Object> readArray() {
        List<Object> a = new ArrayList<Object>();
        expect('[');
        skipWs();
        if (peek(']')) { i++; return a; }

        while (true) {
            Object v = readValue();
            a.add(v);

            skipWs();
            if (peek(']')) { i++; break; }
            expect(',');
        }
        return a;
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (!eof()) {
            char c = ch();
            i++;
            if (c == '"') break;

            if (c == '\\') {
                if (eof()) break;
                char e = ch(); i++;
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        int code = 0;
                        for (int k = 0; k < 4; k++) {
                            if (eof()) break;
                            char h = ch(); i++;
                            code = (code << 4) + hex(h);
                        }
                        sb.append((char) code);
                        break;
                    default:
                        sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Number readNumber() {
        int start = i;
        if (peek('-')) i++;

        while (!eof() && isDigit(ch())) i++;

        boolean isFloat = false;
        if (!eof() && peek('.')) {
            isFloat = true; i++;
            while (!eof() && isDigit(ch())) i++;
        }
        if (!eof() && (peek('e') || peek('E'))) {
            isFloat = true; i++;
            if (!eof() && (peek('+') || peek('-'))) i++;
            while (!eof() && isDigit(ch())) i++;
        }

        String num = s.substring(start, i);
        try {
            return isFloat ? Double.valueOf(num) : Long.valueOf(num);
        } catch (Exception ex) {
            return Long.valueOf(0L);
        }
    }

    private void skipWs() {
        while (!eof()) {
            char c = ch();
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
            else break;
        }
    }

    private boolean eof() { return i >= s.length(); }
    private char ch() { return s.charAt(i); }
    private boolean peek(char c) { return !eof() && ch() == c; }

    private void expect(char c) {
        skipWs();
        if (eof() || ch() != c) throw new IllegalArgumentException("JSON: expected '" + c + "' at pos " + i);
        i++;
    }

    private void expect(String lit) {
        skipWs();
        for (int k = 0; k < lit.length(); k++) {
            if (eof() || ch() != lit.charAt(k))
                throw new IllegalArgumentException("JSON: expected \"" + lit + "\" at pos " + i);
            i++;
        }
    }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
        if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
        return 0;
    }
}
