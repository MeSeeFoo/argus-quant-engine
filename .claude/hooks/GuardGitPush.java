import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PreToolUse hook for Bash and PowerShell: blocks the pushes that CONTRIBUTING.md forbids.
 *
 * <p>A force-push rewrites commits that are already pushed, and {@code main} only changes through
 * pull requests. The deny rules in {@code settings.json} catch the usual spellings; this hook also
 * reads the arguments, so it catches {@code -fu}, {@code +branch} refspecs and a bare {@code git
 * push} while {@code main} is checked out. The ruleset that protects {@code main} on GitHub
 * remains the real boundary.
 */
public final class GuardGitPush {

  private static final Set<String> GIT_OPTIONS_WITH_VALUE =
      Set.of("-C", "-c", "--git-dir", "--work-tree", "--namespace");
  private static final Set<String> PUSH_OPTIONS_WITH_VALUE =
      Set.of("-o", "--push-option", "--repo", "--receive-pack", "--exec");
  private static final Set<String> MAIN = Set.of("main", "refs/heads/main");

  public static void main(String[] args) throws Exception {
    HookInput input = HookInput.fromStdin();
    String command = input.string("command").orElse("");
    Path cwd = Path.of(input.string("cwd").orElse("."));
    for (String segment : command.split("&&|\\|\\||[;|\\n]")) {
      Optional<String> reason = check(tokens(segment), cwd);
      if (reason.isPresent()) {
        System.err.println("Blocked: " + reason.get() + ".");
        System.err.println(
            "Pushed commits are never rewritten and main only changes through a pull request:"
                + " see CONTRIBUTING.md (Branches). To update a branch with main, merge main into"
                + " it.");
        System.exit(2);
      }
    }
  }

  /** Why this command must not run, or empty if it is not a forbidden push. */
  static Optional<String> check(List<String> tokens, Path cwd) throws Exception {
    int i = tokens.indexOf("git");
    if (i < 0) {
      return Optional.empty();
    }
    Path dir = cwd;
    for (i++; i < tokens.size() && tokens.get(i).startsWith("-"); i++) {
      String option = tokens.get(i);
      if (GIT_OPTIONS_WITH_VALUE.contains(option) && i + 1 < tokens.size()) {
        if (option.equals("-C")) {
          dir = cwd.resolve(tokens.get(i + 1));
        }
        i++;
      }
    }
    if (i >= tokens.size() || !tokens.get(i).equals("push")) {
      return Optional.empty();
    }

    List<String> positional = new ArrayList<>();
    for (i++; i < tokens.size(); i++) {
      String arg = tokens.get(i);
      if (arg.startsWith("--force") || arg.equals("--mirror")) {
        return Optional.of("force-pushes are not allowed (" + arg + ")");
      }
      if (arg.equals("--all") || arg.equals("--branches")) {
        return Optional.of("pushing every branch would push main too (" + arg + ")");
      }
      if (arg.startsWith("-") && !arg.startsWith("--") && arg.indexOf('f') > 0) {
        return Optional.of("force-pushes are not allowed (" + arg + ")");
      }
      if (PUSH_OPTIONS_WITH_VALUE.contains(arg)) {
        i++;
      } else if (!arg.startsWith("-")) {
        positional.add(arg);
      }
    }

    List<String> refspecs =
        positional.isEmpty() ? List.of() : positional.subList(1, positional.size());
    for (String refspec : refspecs) {
      if (refspec.startsWith("+")) {
        return Optional.of("force-pushes are not allowed (" + refspec + ")");
      }
      String destination = refspec.substring(refspec.indexOf(':') + 1);
      if (destination.equals("HEAD") || destination.equals("@")) {
        destination = currentBranch(dir);
      }
      if (MAIN.contains(destination)) {
        return Optional.of("direct pushes to main are not allowed (" + refspec + ")");
      }
    }
    if (refspecs.isEmpty() && currentBranch(dir).equals("main")) {
      return Optional.of("this would push main, which is checked out");
    }
    return Optional.empty();
  }

  /** Splits a command into words, dropping the quotes around them. */
  static List<String> tokens(String segment) {
    List<String> tokens = new ArrayList<>();
    for (String word : segment.trim().split("\\s+")) {
      String unquoted = word.replaceAll("^[\"']|[\"']$", "");
      if (!unquoted.isEmpty()) {
        tokens.add(unquoted);
      }
    }
    return tokens;
  }

  private static String currentBranch(Path dir) throws Exception {
    Process git =
        new ProcessBuilder("git", "-C", dir.toString(), "branch", "--show-current")
            .redirectErrorStream(true)
            .start();
    String branch = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    return git.waitFor() == 0 ? branch : "";
  }
}
