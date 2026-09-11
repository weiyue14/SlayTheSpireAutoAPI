package stsapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal dependency-free JSON reader for the POST /api/action body.
 * Supports objects, arrays, strings (with escapes), numbers, booleans, null.
 */
final class JsonReader {

    private final String s;
    private int i;

    private JsonReader(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        JsonReader r = new JsonReader(text);
        r.skipWs();
        Object v = r.value();
        return v;
    }

    static String getStr(Map<?, ?> m, String key, String dflt) {
        Object v = m.get(key);
        return v == null ? dflt : String.valueOf(v);
    }

    static int getInt(Map<?, ?> m, String key, int dflt) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return dflt;
    }

    private Object value() {
        char c = peek();
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        expectChar('{');
        skipWs();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            expectChar(':');
            skipWs();
            m.put(key, value());
            skipWs();
            char c = next();
            if (c == '}') return m;
            if (c != ',') throw err("expected ',' or '}' in object");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        expectChar('[');
        skipWs();
        if (peek() == ']') {
            i++;
            return l;
        }
        while (true) {
            skipWs();
            l.add(value());
            skipWs();
            char c = next();
            if (c == ']') return l;
            if (c != ',') throw err("expected ',' or ']' in array");
        }
    }

    private String string() {
        expectChar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw err("bad escape \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        String t = s.substring(start, i);
        if (t.isEmpty()) throw err("unexpected character");
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw err("bad number: " + t);
        }
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        if (i >= s.length()) throw err("unexpected end of input");
        return s.charAt(i);
    }

    private char next() {
        if (i >= s.length()) throw err("unexpected end of input");
        return s.charAt(i++);
    }

    private void expectChar(char c) {
        if (next() != c) throw err("expected '" + c + "'");
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw err("expected '" + word + "'");
        i += word.length();
    }

    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException("JSON parse error at " + i + ": " + msg);
    }
}
