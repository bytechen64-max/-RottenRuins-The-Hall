import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极小的 JSON 读取器（只做本项目 GUI 预览需要的事）。
 *
 * <p>为什么不用 Gson：这套工具的 classpath 里只有 LWJGL。多一个外部 jar 就多一条
 * "在别人机器上跑不起来"的理由，而着色器 json / 字体 json 的语法在这两个地方都
 * 只有对象、数组、字符串、数字、布尔，一个 150 行的解析器足够。</p>
 *
 * <p>解析结果是：{@code Map<String,Object>} / {@code List<Object>} / {@code String} /
 * {@code Double} / {@code Boolean} / {@code null}。数字统一按 double 存。</p>
 */
final class MiniJson {

    private MiniJson() {}

    static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.value();
        p.skipWs();
        if (!p.eof()) {
            throw p.err("trailing data");
        }
        return value;
    }

    static Map<String, Object> load(Path file) throws java.io.IOException {
        return obj(parse(Files.readString(file)));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> arr(Object value) {
        return (List<Object>) value;
    }

    static int i(Object value) {
        return (int) Math.round(((Number) value).doubleValue());
    }

    static float f(Object value) {
        return (float) ((Number) value).doubleValue();
    }

    static String str(Object value) {
        return (String) value;
    }

    static Object get(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    static Map<String, Object> getObj(Map<String, Object> map, String key) {
        Object value = get(map, key);
        return value instanceof Map ? obj(value) : null;
    }

    static List<Object> getArr(Map<String, Object> map, String key) {
        Object value = get(map, key);
        return value instanceof List ? arr(value) : null;
    }

    static String getStr(Map<String, Object> map, String key, String fallback) {
        Object value = get(map, key);
        return value instanceof String ? (String) value : fallback;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return pos >= s.length();
        }

        void skipWs() {
            while (!eof() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            skipWs();
            if (eof()) {
                throw err("unexpected end");
            }
            char c = s.charAt(pos);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't' && s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (c == 'f' && s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (c == 'n' && s.startsWith("null", pos)) { pos += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (eof()) throw err("eof in object");
            if (s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                if (eof() || s.charAt(pos) != ':') throw err("expected ':'");
                pos++;
                map.put(key, value());
                skipWs();
                if (eof()) throw err("eof in object");
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return map;
                }
                throw err("expected ',' or '}'");
            }
        }

        List<Object> array() {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWs();
            if (eof()) throw err("eof in array");
            if (s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWs();
                if (eof()) throw err("eof in array");
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return list;
                }
                throw err("expected ',' or ']'");
            }
        }

        String string() {
            if (eof() || s.charAt(pos) != '"') throw err("expected string");
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (eof()) throw err("eof in string");
                char c = s.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (eof()) throw err("eof in escape");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw err("bad \\u escape");
                        out.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw err("bad escape \\" + e);
                }
            }
        }

        Double number() {
            int start = pos;
            while (!eof()) {
                char c = s.charAt(pos);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'
                        || (c >= '0' && c <= '9')) {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) throw err("expected value");
            return Double.parseDouble(s.substring(start, pos));
        }

        IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON " + msg + " at " + pos);
        }
    }
}
