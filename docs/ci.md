# Continuous integration

Argus uses GitHub Actions. Workflows are organised **by responsibility**: each file does one job, and declares when it runs.

| Workflow | Runs on | Purpose |
| :--- | :--- | :--- |
| [`ci.yml`](../.github/workflows/ci.yml) | Pull request, push to `main`, manual | Build, tests, coverage, static analysis, quality gate, Compose smoke test |
| [`pr-checks.yml`](../.github/workflows/pr-checks.yml) | Pull request | Conventions, new vulnerable dependencies, internal links, line endings, workflow lint, Protobuf breaking changes, Claude Code hooks |
| [`release.yml`](../.github/workflows/release.yml) | Push to `main` | Release pull request, tag, GitHub Release with SBOM |
| [`nightly.yml`](../.github/workflows/nightly.yml) | Every night, manual | Windows build, every link, known vulnerabilities |
| CodeQL (GitHub default setup) | Pull request, push to `main`, weekly | Security analysis of the Java code and the workflows |

Dependabot ([`.github/dependabot.yml`](../.github/dependabot.yml)) is not a workflow: it opens update pull requests, which then go through the same checks as any other.

## When each workflow runs

| | Open a pull request / push to it | Merge into `main` | Every night | Every week | Manual |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `ci.yml` | ✅ | ✅ | | | ✅ |
| `pr-checks.yml` | ✅ | | | | |
| `release.yml` | | ✅ | | | |
| `nightly.yml` | | | ✅ | | ✅ |
| CodeQL | ✅ | ✅ | | ✅ | |

Nothing runs on a branch without a pull request. Open the pull request early, as a draft if the work is not finished, to get feedback from CI. On a pull request, a new push cancels the run still in progress for the same branch.

Pull request checks run on the result of **merging the branch into `main`**, not on the branch alone.

### The lifecycle of a task

1. **Create the branch `feat/10-…` and commit without a pull request:** nothing runs.
2. **Open the pull request (draft or not):** `ci.yml`, `pr-checks.yml` and CodeQL run in parallel. Each job shows as its own check on the pull request.
3. **Push a fix:** the three run again; the previous run of the same branch is cancelled.
4. **Merge (only with the required checks green, see [Protection of `main`](#protection-of-main)):**
   - `ci.yml` builds `main` again. The SonarQube analysis of `main` is the baseline the next pull request is compared with, and the README badge shows its result.
   - `release.yml` opens or updates the release pull request. Nothing is published yet.
   - CodeQL analyses `main`.
5. **Merge the release pull request when you want to publish a version:** `release.yml` creates the tag (for example `v0.2.0`) and the GitHub Release, and attaches the SBOM.
6. **That night, around 03:00 UTC:** `nightly.yml` runs on `main`, whether it changed or not. If it fails, it opens or updates an issue.
7. **Once a week:** CodeQL analyses `main` again.

## Running a workflow by hand

`ci.yml` and `nightly.yml` can be started by hand on any branch:

- **On GitHub:** *Actions* tab → choose the workflow → *Run workflow* → pick the branch.
- **From a terminal:**

  ```bash
  gh workflow run ci.yml --ref feat/10-my-branch
  gh run watch
  ```

Things to know:

- GitHub only offers the button for workflows that have `workflow_dispatch` **in `main`**. The run uses **the version of the workflow in the chosen branch**, so this is also how to try a change to a workflow before merging it.
- A manual run tests the branch as it is. The pull request run tests the branch merged into `main`: if `main` has moved on, they can differ.
- SonarQube analyses a manual run as a branch, not as a pull request. It shows in SonarQube, but does not change the pull request's quality gate.
- A manual run does not cancel, and is not cancelled by, the pull request run of the same branch.

`release.yml` cannot be started by hand, so that nothing is released by accident.

## `ci.yml`: build and quality gate

### Build and test

Runs `./mvnw verify` on Linux with JDK 25 (Temurin), with the Maven repository cached between runs. The build fails on:

| Check | Tool | Threshold |
| :--- | :--- | :--- |
| Tests | JUnit, ArchUnit | Every test passes |
| Formatting | Spotless (google-java-format) | No unformatted file |
| Static analysis | Error Prone + NullAway | No warning at all (`-Werror`) |
| Coverage | JaCoCo | ≥ 80 % lines and ≥ 70 % branches per module; generated code excluded |

All of them are part of the Maven build, so `./mvnw verify` on your machine gives the same result as CI. See [Building](../README.md#-building) for how each one is configured.

The JUnit results are published as a *Test report* check on the pull request, with failing tests annotated on the code. When the build fails, the raw reports are uploaded as an artifact of the run.

### SonarQube Cloud quality gate

After the build, `sonar:sonar` sends the code and the JaCoCo reports to [SonarQube Cloud](https://sonarcloud.io/summary/new_code?id=MeSeeFoo_argus-quant-engine) and **waits for the quality gate**: a red gate fails the job. On a pull request the gate applies to the **new code** (coverage ≥ 80 %, duplication < 3 %, no new bugs or vulnerabilities, A ratings); on `main` the analysis becomes the baseline for the next pull requests.

The coverage thresholds of JaCoCo and of SonarQube are complementary: JaCoCo keeps the total of each module above the minimum, SonarQube makes every change carry its own tests.

The analysis needs the `SONAR_TOKEN` secret. Without it (pull requests from forks, Dependabot) the step is skipped with a warning and the quality gate is not checked.

<details>
<summary>One-time setup of SonarQube Cloud</summary>

1. Sign in to [sonarcloud.io](https://sonarcloud.io) with GitHub and import the GitHub account `MeSeeFoo` and the repository. The organisation key is `argus-quant-engine` and the project key `MeSeeFoo_argus-quant-engine`; both are set in the root `pom.xml` (`sonar.organization`, `sonar.projectKey`) and must match the keys shown in SonarQube Cloud.
2. In the project, *Administration → Analysis Method*: turn **off** *Automatic Analysis* (CI runs the analysis).
3. *My Account → Security*: generate a token, and save it in GitHub as the repository secret `SONAR_TOKEN` (*Settings → Secrets and variables → Actions*).

</details>

### Compose smoke test

Creates `.env` from `.env.example` and runs `docker compose up -d --wait` in `infra/docker`, so it fails if any container does not become healthy within five minutes. The container logs are printed when it fails, and the stack is always removed with its volumes.

## `pr-checks.yml`: pull request only

| Job | What it checks |
| :--- | :--- |
| **Conventions** | The title follows Conventional Commits and ends with the issue, for example `feat(build): add parent POM (#1)`. The branch is `<type>/<issue>-<slug>`. Skipped for Dependabot and release-please pull requests. Editing the title runs the check again. |
| **Dependency review** | The pull request does not add a dependency with a known vulnerability of moderate severity or higher. |
| **Internal links** | Links between Markdown files, including `#anchors`, with [lychee](https://lychee.cli.rs/) in offline mode. External links are checked by `nightly.yml`, because a website that is down must not block a pull request. Settings in [`lychee.toml`](../lychee.toml), exclusions in [`.lycheeignore`](../.lycheeignore). |
| **Line endings** | Every file is stored with the line endings set in [`.gitattributes`](../.gitattributes): LF in the repository, converted to CRLF on checkout only for `.cmd` and `.bat` files. Some tools, Dependabot among them, commit CRLF, and Git then shows those files as modified on every checkout. To fix it, run `git add --renormalize .` and commit the result. |
| **Workflow lint** | [actionlint](https://github.com/rhysd/actionlint) (syntax and types of the workflows) and [zizmor](https://docs.zizmor.sh/) (security: unpinned actions, template injection, excessive permissions…). |
| **Protobuf contracts** | `buf lint`, `buf format` and `buf breaking` against the target branch, with the rules in [`buf.yaml`](../contracts/src/main/protobuf/buf.yaml). See [Evolving the schemas](../contracts/README.md#evolving-the-schemas). |
| **Claude Code hooks** | On Linux and Windows, runs [`HookTest.java`](../.claude/hooks/HookTest.java): each hook of [`.claude/settings.json`](../.claude/settings.json) receives the JSON Claude Code would send, and its verdict is checked. See [Claude Code](../CLAUDE.md#claude-code-settings-and-hooks). |

## `release.yml`: versions and releases

Uses [release-please](https://github.com/googleapis/release-please), configured in [`release-please-config.json`](../release-please-config.json):

- On every merge into `main`, it opens or updates **one release pull request** with the next version, computed from the Conventional Commits since the last release, the new section of `CHANGELOG.md`, and the version of every `pom.xml`.
- Versions follow semantic versioning. Before 1.0.0, `feat` bumps the minor version and a breaking change (`!`) bumps the minor version too; `fix` bumps the patch version.
- Merging the release pull request creates the tag `vX.Y.Z` and the GitHub Release, and a second job attaches the SBOM of the Maven build (CycloneDX, `argus-sbom.json` and `argus-sbom.xml`).
- `pom.xml` versions are plain release versions (`0.2.0`): there are no `-SNAPSHOT` pull requests after a release.

- The first release is `0.1.0` (`initial-version` in the configuration).

release-please opens pull requests with the workflow token, which the repository must allow: *Settings → Actions → General → Workflow permissions → Allow GitHub Actions to create and approve pull requests*. Without it, `release.yml` fails with "GitHub Actions is not permitted to create or approve pull requests". The ruleset requires no approvals, so the approval part of that setting grants nothing extra.

> [!IMPORTANT]
> GitHub does not run workflows on pull requests opened with the workflows' own token, so the release pull request starts **without checks** and cannot be merged while they are required. **Close and reopen it**: that runs the checks, and it can be merged once they are green.

## `nightly.yml`: slow and changing checks

Runs at 03:00 UTC on `main` (GitHub may start it late when busy), or by hand on any branch:

| Job | What it checks |
| :--- | :--- |
| **Build and test on Windows** | `mvnw.cmd verify`: line endings, paths and scripts that only fail on Windows. |
| **All links** | Every link in the Markdown files, external ones included. |
| **Known vulnerabilities** | [OSV-Scanner](https://google.github.io/osv-scanner/) on the Maven dependencies, transitive ones included. New vulnerabilities are published every day, so this can fail without any code change. |

When a **scheduled** run fails, it opens an issue labelled `nightly-failure`, or comments on the open one. Close the issue once it is fixed.

GitHub disables scheduled workflows in public repositories after 60 days without activity; re-enable it from the *Actions* tab if that happens.

## Security

- **Actions pinned by SHA.** Every action is referenced by commit SHA, with its version as a comment, so a moved tag cannot change what runs. Dependabot keeps them up to date.
- **Least privilege.** The default token is read-only; each job asks only for the permissions it needs.
- **Dependabot.** Weekly updates of Maven dependencies, actions and Compose images, grouped by ecosystem for minor and patch versions, after a 7-day cooldown: a compromised release is usually caught before then. Security updates and alerts are on.
- **CodeQL.** GitHub's default setup analyses the Java code and the workflows. Results are under *Security → Code scanning*.
- **Secret scanning with push protection.** GitHub rejects a push that contains a known kind of secret.

## Protection of `main`

A repository ruleset on `main` requires:

- A pull request for every change; direct pushes, force pushes and deleting the branch are rejected.
- These checks green: `Build and test`, `Compose smoke test`, `Conventions`, `Dependency review`, `Internal links`, `Line endings`, `Workflow lint`, `Protobuf contracts` and CodeQL.
- The branch up to date with `main` before merging.
- Merge commits only (no squash, no rebase), so every commit stays visible in `main`.

The ruleset lives in the repository settings; [`.github/rulesets/protect-main.json`](../.github/rulesets/protect-main.json) keeps a copy so that changes are reviewed like code. After changing the file, apply it with:

```bash
gh api -X PUT repos/MeSeeFoo/argus-quant-engine/rulesets/25230975 --input .github/rulesets/protect-main.json
```

A new required check must be added to the ruleset by name (the job's `name:`), after it has run at least once. If an external service is down (for example SonarQube Cloud), an administrator can remove its check from the ruleset temporarily (*Settings → Rules → Rulesets*).

## Running the checks locally

| Check | Command |
| :--- | :--- |
| Build, tests, formatting, static analysis, coverage | `./mvnw verify` |
| Format the code | `./mvnw spotless:apply` |
| Coverage report | `target/site/jacoco/index.html` in each module, after `verify` |
| Compose smoke test | `docker compose up -d --wait` in `infra/docker` |
| Protobuf contracts | `buf lint && buf breaking --against '../../../../.git#branch=main,subdir=contracts/src/main/protobuf'` in `contracts/src/main/protobuf` |
| Line endings | `git add --renormalize . && git diff --cached --stat` (lists the files to fix; `git reset` to unstage) |
| Links | `lychee --offline './**/*.md'` (add no flag to check external links too) |
| Workflows | `actionlint` and `zizmor .github` |
| Claude Code hooks | `java .claude/hooks/HookTest.java` |
| Vulnerabilities | `osv-scanner scan source -r .` |

[buf](https://buf.build/docs/installation/), [lychee](https://lychee.cli.rs/), [actionlint](https://github.com/rhysd/actionlint), [zizmor](https://docs.zizmor.sh/installation/) and [OSV-Scanner](https://google.github.io/osv-scanner/installation/) are optional: CI runs them anyway.
