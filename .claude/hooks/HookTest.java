import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every hook the way Claude Code does (a new JVM, the JSON input on stdin) and checks its
 * verdict. Run it from the repository root with {@code java .claude/hooks/HookTest.java}; CI runs
 * it on Linux and Windows.
 */
public final class HookTest {

  private static final Path HOOKS = Path.of(".claude", "hooks").toAbsolutePath();
  private static final List<String> failures = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    protectGeneratedCode();
    guardGitPush();
    formatJava();
    if (failures.isEmpty()) {
      System.out.println("Every hook behaves as expected.");
    } else {
      failures.forEach(failure -> System.err.println("FAIL " + failure));
      System.exit(1);
    }
  }

  private static void protectGeneratedCode() throws Exception {
    Path contracts = Path.of("contracts").toAbsolutePath();
    Path sbe = contracts.resolve("target/generated-sources/sbe/com/argus/contracts/sbe/Trade.java");
    Path proto = contracts.resolve("target/generated-sources/protobuf/com/argus/Trade.java");
    Path source = contracts.resolve("src/main/resources/sbe/argus-fastpath.xml");

    expect("edit SBE codec", run("ProtectGeneratedCode", edit(sbe), null), 2, "argus-fastpath.xml");
    expect("edit Protobuf class", run("ProtectGeneratedCode", edit(proto), null), 2, "protobuf");
    expect("edit a schema", run("ProtectGeneratedCode", edit(source), null), 0, "");
  }

  private static void guardGitPush() throws Exception {
    Path repo = Files.createTempDirectory("argus-hook-test");
    Path cwd = Path.of("").toAbsolutePath();
    try {
      runProcess(repo, null, "git", "init", "-q", "-b", "main");
      for (String command :
          List.of(
              "git push --force",
              "git push -f origin feat/1-x",
              "git push -fu origin feat/1-x",
              "git push --force-with-lease origin feat/1-x",
              "git push origin +feat/1-x",
              "git push origin main",
              "git push origin HEAD:main",
              "git push origin feat/1-x:refs/heads/main",
              "git push --all origin",
              "git status && git -C . push --mirror")) {
        expect(command, run("GuardGitPush", bash(command, cwd), null), 2, "Blocked");
      }
      for (String command :
          List.of(
              "git push -u origin feat/1-x",
              "git push origin feat/1-fix-main-link",
              "git status",
              "./mvnw verify")) {
        expect(command, run("GuardGitPush", bash(command, cwd), null), 0, "");
      }
      expect("git push on main", run("GuardGitPush", bash("git push", repo), null), 2, "main");
    } finally {
      try (var files = Files.walk(repo)) {
        files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      }
    }
  }

  private static void formatJava() throws Exception {
    Path sample =
        Path.of("libs/architecture-rules/src/test/java/com/argus/architecture/HookTestSample.java")
            .toAbsolutePath();
    try {
      Files.writeString(
          sample, "package com.argus.architecture;\nclass HookTestSample {   int   x =1;  }\n");
      expect("format a Java file", run("FormatJava", edit(sample), null), 0, "");
      String formatted = Files.readString(sample);
      if (!formatted.equals(
          "package com.argus.architecture;\n\nclass HookTestSample {\n  int x = 1;\n}\n")) {
        failures.add("format a Java file: unexpected result\n" + formatted);
      }
      Files.writeString(sample, "package com.argus.architecture;\nclass HookTestSample {\n");
      expect("report a file that does not parse", run("FormatJava", edit(sample), null), 2, "");
    } finally {
      Files.deleteIfExists(sample);
    }
  }

  private static void expect(String name, Result result, int exitCode, String message) {
    if (result.exitCode() != exitCode || !result.stderr().contains(message)) {
      failures.add(name + ": exit " + result.exitCode() + "\n" + result.stderr());
    }
  }

  private static String edit(Path file) {
    return "{\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":" + quote(file.toString()) + "}}";
  }

  private static String bash(String command, Path cwd) {
    return "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":"
        + quote(command)
        + "},\"cwd\":"
        + quote(cwd.toString())
        + "}";
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static Result run(String hook, String json, Path dir) throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return runProcess(dir, json, java, HOOKS.resolve(hook + ".java").toString());
  }

  private static Result runProcess(Path dir, String stdin, String... command) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(command);
    if (dir != null) {
      builder.directory(dir.toFile());
    }
    Process process = builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    try (var in = process.getOutputStream()) {
      if (stdin != null) {
        in.write(stdin.getBytes(StandardCharsets.UTF_8));
      }
    }
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.waitFor(), stderr);
  }

  private record Result(int exitCode, String stderr) {}
}
