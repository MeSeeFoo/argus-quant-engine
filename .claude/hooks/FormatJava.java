import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PostToolUse hook: formats the Java file that was just written or edited.
 *
 * <p>It runs {@code spotless:apply} on that file only, through the Maven Wrapper of the repository
 * that contains it, so the formatter and its settings are exactly the ones {@code ./mvnw verify}
 * checks. The repository is found from the file, not from the session, so it also works in git
 * worktrees. A file that does not parse is reported back (exit code 2) and left as it is.
 */
public final class FormatJava {

  public static void main(String[] args) throws Exception {
    String filePath = HookInput.fromStdin().string("file_path").orElse("");
    if (!filePath.endsWith(".java")
        || ProtectGeneratedCode.isGenerated(HookInput.normalize(filePath))) {
      return;
    }
    Path file = Path.of(filePath).toAbsolutePath().normalize();
    Path root = repositoryRoot(file);
    if (root == null || !Files.isRegularFile(file)) {
      return;
    }

    Process maven =
        new ProcessBuilder(mavenCommand(root, file))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    maven.getOutputStream().close();
    String output = new String(maven.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (maven.waitFor() != 0) {
      System.err.println("Could not format " + root.relativize(file) + " with Spotless:");
      boolean hasErrors = output.contains("[ERROR]");
      output
          .lines()
          .filter(line -> !hasErrors || line.contains("[ERROR]"))
          .limit(30)
          .forEach(System.err::println);
      System.exit(2);
    }
  }

  /** The closest directory above the file with the Maven Wrapper, or null outside a repository. */
  private static Path repositoryRoot(Path file) {
    for (Path dir = file.getParent(); dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve("mvnw")) && Files.isDirectory(dir.resolve(".mvn"))) {
        return dir;
      }
    }
    return null;
  }

  private static List<String> mavenCommand(Path root, Path file) {
    List<String> command = new ArrayList<>();
    if (File.separatorChar == '\\') {
      command.addAll(List.of("cmd.exe", "/c", root.resolve("mvnw.cmd").toString()));
    } else {
      command.addAll(List.of("sh", root.resolve("mvnw").toString()));
    }
    command.addAll(List.of("-q", "-B", "spotless:apply", "-DspotlessFiles=" + pattern(root, file)));
    return command;
  }

  /**
   * A regular expression for the file's absolute path. Every character other than a letter, a
   * digit, {@code _} or {@code -} becomes {@code .}, which matches both path separators and needs
   * no quoting in any shell.
   */
  private static String pattern(Path root, Path file) {
    return ".*" + root.relativize(file).toString().replaceAll("[^A-Za-z0-9_-]", ".");
  }
}
