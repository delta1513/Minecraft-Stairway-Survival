# AGENTS.md

Read this before you touch the repo.

## What this project is

**Stairway Survival** is a PaperMC plugin that runs a minigame. Teams are trapped in 3×3 block "plots" arranged around world spawn. They have to climb to Y=319. When someone gets there, the arena starts disappearing from the bottom up, and the last team standing wins. The plugin has to work on **any world seed**: it never generates or resets worlds.

## Sources of truth (in priority order)

1. **`PLAN.md`**: the implementation spec. Every value, formula, phase and rule is here. **Follow it exactly.**
2. `Minecraft Stairway Survival Challenge - internal document.md`: the internal design notes.
3. `Minecraft Stairway Survival Challenge.md`: the public, player-facing rules.

Do **not** edit any of these three files. If code and spec disagree, the spec wins. If the spec looks wrong, escalate (see below). Don't silently "fix" it.

## Toolchain

- Paper **26.3** API: `compileOnly("io.papermc.paper:paper-api:26.3.build.+")` from `https://repo.papermc.io/repository/maven-public/`.
- **Java 25.** The container only has JDK 21, so use a Gradle Java toolchain with the foojay resolver plugin (`org.gradle.toolchains.foojay-resolver-convention`) to download JDK 25.
- **Gradle wrapper** (`./gradlew`). Kotlin DSL (`build.gradle.kts`).
- **Tests:** JUnit 5 and MockBukkit (`org.mockbukkit.mockbukkit:mockbukkit-v26.3`).
- **Base package:** `io.github.delta1513.stairway`.
- Commands: `./gradlew test` runs the tests. `./gradlew build` builds the jar into `build/libs/`.

### Setup facts learned so far
- Gradle wrapper 9.9.1. The daemon runs on JDK 21. The project compiles and tests on Temurin 25, which foojay downloads into `~/.gradle/jdks`.
- **Tests run on JUnit 6** (`junit-bom:6.1.3`), not 5, because MockBukkit `4.120.0` pulls in 6.x. The JUnit Jupiter API is the same.
- Use **`plugin.yml`**, not `paper-plugin.yml`. Commands go through the Brigadier lifecycle API, so they are not declared in the descriptor.
- `StairwayPlugin` must **not be `final`**, because MockBukkit subclasses it.
- `paper-api` is also on `testImplementation`.
- Maven Central sometimes **rate-limits this container**: it serves 404 with `retry-after`, and Gradle caches the miss. **Do not add extra repositories to the project build files.**
  - In the cloud dev container, run Gradle with `-I /tmp/claude-0/-home-user-Minecraft-Stairway-Survival/7dfa9644-7238-59c5-a62a-b8de1fe7bb94/scratchpad/mirror.init.gradle.kts`, e.g. `./gradlew -I <that path> build`. This init script is session-local and adds Google's Maven Central mirror.

## How to work

- **TDD.** Write or extend the tests first, watch them fail, then implement. A milestone is done only when `./gradlew build` (which includes the tests) passes.
- **Keep game logic testable without a server.** Geometry, zone maths, the state machine, win checks and the random-pick logic are plain Java with an injectable `Random`/clock. Use MockBukkit for listener and command behaviour.
- **Every tunable value comes from config** (PLAN.md §11.2). No magic numbers in the logic.
- Stay inside the scope your task gives you, and don't refactor other milestones' code unless you need to.
- **Commits:** commit on the current branch with a clear message. Do not push, and do not create branches.
- **Finish with a short report:** what you built, test results (pass/fail counts), anything you deferred, and any assumptions you made.

## Verify against authoritative docs. Don't trust memory.

The APIs changed a lot in recent versions: gamerule names, potion effect names, the command API, the plugin descriptor. Before you use an API you aren't sure of, check it here:

| Topic | Source |
|---|---|
| Paper/Bukkit Javadoc (26.3) | https://jd.papermc.io/paper/26.3/ |
| Paper dev docs (project setup, plugin.yml vs paper-plugin.yml, lifecycle) | https://docs.papermc.io/paper/dev/ |
| Brigadier command API | https://docs.papermc.io/paper/dev/command-api/basics/introduction/ |
| Paper builds / versions / Java requirements | https://fill.papermc.io/v3/projects/paper |
| Adventure (text components, boss bars, titles) | https://docs.advntr.dev/ |
| MockBukkit | https://docs.mockbukkit.org/ |
| Gradle toolchains | https://docs.gradle.org/current/userguide/toolchains.html |
| Vanilla game mechanics (effects, beds, world border, build height, game rules, tags) | https://minecraft.wiki/ (e.g. `/w/Bed`, `/w/World_border`, `/w/Altitude`, `/w/Game_rule`, `/w/Wither_(effect)`) |

The Javadoc wins over the wiki for API questions. The wiki wins for vanilla behaviour. If they conflict, or something in PLAN.md §13 turns out to be false, say so in your report.

## Escalation: when to stop and ask

Stop and report back, starting your final message with **`BLOCKED:`** and giving a precise question, if:
- the spec (PLAN.md or the design docs) contradicts itself, contradicts the API or vanilla behaviour, or is ambiguous about how the game should behave;
- a fact in PLAN.md turns out to be false (e.g. an API that doesn't exist);
- you're stuck on tooling or the environment (downloads failing, the build not resolving) after a reasonable attempt.

Before you stop, commit any work that builds. Don't guess at game design. Pure implementation details (class names, helper structure, data structures) are your call.
