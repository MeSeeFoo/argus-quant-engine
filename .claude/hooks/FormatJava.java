import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stop hook: formats every Java file changed in the working tree when Claude finishes a turn.
 *
 * <p>It runs {@code spotless:apply} once, on the changed and untracked {@code .java} files only,
 * through the Maven Wrapper of the repository, so the formatter and its settings are exactly the
 * ones {@code ./mvnw verify} checks. Formatting once per turn instead of after every edit avoids
 * starting Maven for each change. The repository is found from the session's working directory,
 * so it also works in git worktrees.
 *
 * <p>A file that does not parse is reported back to Claude (exit code 2), which keeps working to
 * fix it. If it still fails on the turn that fix produced, the error is only shown, so a file that
 * cannot be fixed never keeps Claude in a loop.
 */
public final class FormatJava {

  public static void main(String[] args) throws Exception {
    HookInput input = HookInput.fromStdin();
    Path root = repositoryRoot(Path.of(input.string("cwd").orElse("")).toAbsolutePath());
    if (root == null) {
      return;
    }
    List<String> files = changedJavaFiles(root);
    if (files.isEmpty()) {
      return;
    }

    Result maven = run(root, mavenCommand(root, files));
    if (maven.exitCode() != 0) {
      System.err.println("Could not format the changed Java files with Spotless:");
      boolean hasErrors = maven.output().contains("[ERROR]");
      maven
          .output()
          .lines()
          .filter(line -> !hasErrors || line.contains("[ERROR]"))
          .limit(30)
          .forEach(System.err::println);
      System.exit(input.flag("stop_hook_active") ? 1 : 2);
    }
  }

  /** The closest directory with the Maven Wrapper, or null outside a repository. */
  private static Path repositoryRoot(Path cwd) {
    for (Path dir = cwd; dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve("mvnw")) && Files.isDirectory(dir.resolve(".mvn"))) {
        return dir;
      }
    }
    return null;
  }

  /** Modified, added and untracked Java sources, relative to the root; deleted ones are skipped. */
  static List<String> changedJavaFiles(Path root) throws Exception {
    Result git = run(root, List.of("git", "status", "--porcelain", "-z", "--untracked-files=all"));
    List<String> files = new ArrayList<>();
    if (git.exitCode() != 0) {
      return files;
    }
    String[] entries = git.output().split("\0");
    for (int i = 0; i < entries.length; i++) {
      if (entries[i].length() < 4) {
        continue;
      }
      String status = entries[i].substring(0, 2);
      String path = entries[i].substring(3);
      if (status.contains("R") || status.contains("C")) {
        i++; // the entry after a rename or copy is its original path
      }
      if (path.endsWith(".java")
          && !ProtectGeneratedCode.isGenerated("/" + path)
          && Files.isRegularFile(root.resolve(path))) {
        files.add(path);
      }
    }
    return files;
  }

  private static List<String> mavenCommand(Path root, List<String> files) {
    List<String> command = new ArrayList<>();
    if (File.separatorChar == '\\') {
      command.addAll(List.of("cmd.exe", "/c", root.resolve("mvnw.cmd").toString()));
    } else {
      command.addAll(List.of("sh", root.resolve("mvnw").toString()));
    }
    List<String> patterns = files.stream().map(FormatJava::pattern).toList();
    command.addAll(
        List.of("-q", "-B", "spotless:apply", "-DspotlessFiles=" + String.join(",", patterns)));
    return command;
  }

  /**
   * A regular expression for the file's absolute path. Every character other than a letter, a
   * digit, {@code _} or {@code -} becomes {@code .}, which matches both path separators and needs
   * no quoting in any shell.
   */
  private static String pattern(String relativePath) {
    return ".*" + relativePath.replaceAll("[^A-Za-z0-9_-]", ".");
  }

  private static Result run(Path dir, List<String> command) throws Exception {
    Process process =
        new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
    process.getOutputStream().close();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.waitFor(), output);
  }

  private record Result(int exitCode, String output) {}
}
