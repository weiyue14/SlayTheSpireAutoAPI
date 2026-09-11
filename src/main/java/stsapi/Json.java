package stsapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal dependency-free JSON writer with stable field order.
 * Supports null, String, Number, Boolean, Enum, Map, Iterable and arrays.
 */
final class Json {

    private Json() {
    }

    /** Builds an ordered object from key/value pairs: obj("hp", 10, "name", "x"). */
    static Map<String, Object> obj(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    static List<Object> arr() {
        return new ArrayList<>();
    }

    /** Builds a list from items: arr("a", "b", "c"). */
    static List<Object> arr(Object... items) {
        List<Object> l = new ArrayList<>(items.length);
        for (Object o : items) l.add(o);
        return l;
    }

    static String write(Object o, boolean pretty) {
        StringBuilder sb = new StringBuilder(8192);
        writeValue(sb, o, pretty, 0);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object o, boolean pretty, int indent) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            writeString(sb, (String) o);
        } else if (o instanceof Boolean
                || o instanceof Integer || o instanceof Long
                || o instanceof Short || o instanceof Byte) {
            sb.append(o.toString());
        } else if (o instanceof Float) {
            writeFloating(sb, (Float) o);
        } else if (o instanceof Double) {
            writeFloating(sb, (Double) o);
        } else if (o instanceof Enum) {
            writeString(sb, ((Enum<?>) o).name());
        } else if (o instanceof Map) {
            writeMap(sb, (Map<?, ?>) o, pretty, indent);
        } else if (o instanceof Iterable) {
            writeIterable(sb, (Iterable<?>) o, pretty, indent);
        } else if (o.getClass().isArray()) {
            writeIterable(sb, java.util.Arrays.asList((Object[]) o), pretty, indent);
        } else {
            writeString(sb, String.valueOf(o));
        }
    }

    private static void writeFloating(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (!Double.isNaN(d) && !Double.isInfinite(d) && d == Math.floor(d) && Math.abs(d) < 1e15d) {
            sb.append(Long.toString((long) d));
        } else {
            sb.append(n.toString());
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> m, boolean pretty, int indent) {
        if (m.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            newlineIndent(sb, pretty, indent + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(pretty ? ": " : ":");
            writeValue(sb, e.getValue(), pretty, indent + 1);
        }
        newlineIndent(sb, pretty, indent);
        sb.append('}');
    }

    private static void writeIterable(StringBuilder sb, Iterable<?> it, boolean pretty, int indent) {
        List<Object> items = new ArrayList<>();
        for (Object o : it) items.add(o);
        if (items.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        boolean first = true;
        for (Object o : items) {
            if (!first) sb.append(',');
            first = false;
            newlineIndent(sb, pretty, indent + 1);
            writeValue(sb, o, pretty, indent + 1);
        }
        newlineIndent(sb, pretty, indent);
        sb.append(']');
    }

    private static void newlineIndent(StringBuilder sb, boolean pretty, int indent) {
        if (!pretty) return;
        sb.append('\n');
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
