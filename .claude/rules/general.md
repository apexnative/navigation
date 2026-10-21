# General rules

- **Native names:** pod and Swift module `ApexNavigation`, Java/Kotlin package `dev.apexnative.navigation`,
  Objective-C prefix `ANN`, log prefix `[ApexNavigation]`.
- **One behavior change per commit.** Conventional commits (commitlint runs on commit-msg).
- **Android needs JDK 17.** Run Gradle and `react-native run-android` through `scripts/android-env.sh` (the example's
  scripts do); never change global Java settings.
- **Languages.** New Android code in Kotlin. The iOS core stays Objective-C++; Swift only in separate modules.
- **English** in code, comments, docs and commit messages. Comments are plain prose: what the code is for and why.
- **Examples** in code, docs and the Showcase use neutral names: Home, Profile, Settings, Details, Login.
- **An option does what its name promises**, without code in the app. Separate options don't depend on each other.
  One meaning on both platforms; where a platform can't do it, the docs of the option say so.
- **Native code doesn't reach into React Native's views** (their subviews, their layout): Fabric owns and recycles
  them.
- **Both platforms.** A change of behavior is made on iOS and Android, or the docs say which platform has it.
- **Checks before done,** through `scripts/` (see Commands in `CLAUDE.md`): `scripts/check.sh` for TypeScript;
  `scripts/build-ios.sh` / `scripts/build-android.sh --lib` for native code (warnings are errors).
- **Agents: the model fits the job.** Sonnet for mechanical work (applying decisions already made, coverage and
  cross-reference checks, tables, searches, scripts), Opus for design, native code and review, Haiku for plain
  lookups. Effort `high` for native code, design and review; `medium` for the mechanical agents (researcher,
  docs-writer, qa, ci-doctor); `xhigh` only for the hardest design questions. The agents in `.claude/agents/` carry their
  model; a generic agent gets `model` in its call.
- **Agents: few and focused.** At most four at a time. Each reads only its part and reports in a few lines, the details
  go into files. A restarted agent finishes only what is left.
