import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The JSON that Claude Code sends a hook on stdin, read without any library.
 *
 * <p>Hooks only need a few top-level string fields of {@code tool_input} ({@code file_path},
 * {@code command}…). A field name is matched only where it starts a key, never inside another
 * string value, because a quote inside a JSON string is always escaped.
 */
final class HookInput {

  private final String json;

  private HookInput(String json) {
    this.json = json;
  }

  static HookInput fromStdin() throws IOException {
    return new HookInput(new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
  }

  /** The value of the first string field with this name, unescaped. */
  Optional<String> string(String name) {
    String key = "\"" + name + "\"";
    for (int at = json.indexOf(key); at >= 0; at = json.indexOf(key, at + 1)) {
      if (at > 0 && json.charAt(at - 1) == '\\') {
        continue;
      }
      int i = skipSpaces(at + key.length());
      if (i >= json.length() || json.charAt(i) != ':') {
        continue;
      }
      i = skipSpaces(i + 1);
      if (i < json.length() && json.charAt(i) == '"') {
        return Optional.of(unescape(i + 1));
      }
    }
    return Optional.empty();
  }

  private int skipSpaces(int i) {
    while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
      i++;
    }
    return i;
  }

  private String unescape(int start) {
    StringBuilder out = new StringBuilder();
    for (int i = start; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '"') {
        break;
      }
      if (c != '\\' || i + 1 >= json.length()) {
        out.append(c);
        continue;
      }
      char escaped = json.charAt(++i);
      switch (escaped) {
        case 'n' -> out.append('\n');
        case 't' -> out.append('\t');
        case 'r' -> out.append('\r');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'u' -> {
          out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
          i += 4;
        }
        default -> out.append(escaped);
      }
    }
    return out.toString();
  }

  /** A path with forward slashes, so the same checks work on Windows and Linux. */
  static String normalize(String path) {
    return path.replace('\\', '/');
  }
}
