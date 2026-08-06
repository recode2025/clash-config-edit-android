# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

ClashConfigEditor（"Clash 配置工坊"）is a single-module Android app that lets users **edit Mihomo / Clash YAML configs locally on-device**. It is deliberately zero-network and zero-storage-permission: all file access goes through the system file picker (SAF / `ContentResolver`) or incoming share/view `Intent`s, and "share to Clash" copies into the app's own cache and hands off a `content://` URI. There is no backend, no analytics, and the manifest requests **no permissions**.

UI strings are Simplified Chinese. Target audience: users of ClashMi, Clash Meta for Android, Clash for Android.

## Build & Test

Gradle wrapper project. Requires **JDK 17** and Android SDK Platform 35 (set `JAVA_HOME` and either `local.properties` `sdk.dir=...` or `ANDROID_HOME`).

```bash
./gradlew assembleDebug          # Windows: ./gradlew.bat assembleDebug  →  app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # release is minified + resource-shrunk; needs signing config
./gradlew test                   # JVM unit tests (data layer only — no Robolectric)
./gradlew connectedAndroidTest   # instrumented tests (needs emulator/device)

# single test class / method:
./gradlew test --tests "com.recode.clashcraft.data.ConfigTreeTest"
./gradlew test --tests "com.recode.clashcraft.data.ConfigTreeTest.updatesOnlyTheChangedPathAndKeepsOtherLargeBranches"
```

The meaningful tests are the JVM unit tests in [app/src/test/](app/src/test/); they exercise the pure data layer (`MihomoYaml`, `ConfigTree`, `RuleWizard`, `CustomRuleManager`, `ClashMiBackupReader`). SnakeYAML uses `SafeConstructor`, so these parse/dump tests run on the JVM with no Android dependencies.

## Architecture

### Core design: schema-less, path-addressed, immutable tree

This is the single most important thing to understand. **Configs are never bound to fixed data classes** — that would drop unknown/future keys. Instead the whole config is held as an ordered generic tree (`LinkedHashMap<String,Any?>` / `MutableList<Any?>`) parsed straight from SnakeYAML, and every edit is expressed as a `ConfigPath` (a list of `Key` / `Index` parts, see [ConfigModels.kt](app/src/main/java/com/recode/clashcraft/data/ConfigModels.kt)).

The pipeline of responsibilities:

- **`MihomoYaml`** ([data/MihomoYaml.kt](app/src/main/java/com/recode/clashcraft/data/MihomoYaml.kt)) — parse/dump/summarize. `parse()` loads into `LinkedHashMap` (order preserved), with strict loader options (no duplicate keys, 64 MB `codePointLimit`, nesting depth ≤ 200). `dump()` writes BLOCK style, width 140, UNIX line endings. `summarize()` walks the tree to produce `ConfigSummary` (counts + group info).
- **`ConfigTree`** ([data/ConfigTree.kt](app/src/main/java/com/recode/clashcraft/data/ConfigTree.kt)) — pure functions (`set`/`remove`/`renameKey`/`addMapEntry`/`addListItem`/`moveListItem`) that take `(root, path)` and return a **new** `LinkedHashMap`. Validation is via `require{}`, so failures surface as user-facing messages.
- **`ConfigRepository`** ([data/ConfigRepository.kt](app/src/main/java/com/recode/clashcraft/data/ConfigRepository.kt)) — reads/writes via `ContentResolver`, takes persistable URI permission (read+write, falling back to read-only), caps I/O at 64 MB, auto-detects ZIP by magic bytes/extension.
- **`ClashMiBackupReader`** ([data/ClashMiBackupReader.kt](app/src/main/java/com/recode/clashcraft/data/ClashMiBackupReader.kt)) — reads ClashMi `.backup.zip`, finds YAMLs under any `profiles/` directory, matches them to `profiles.json` for display names, filters out ClashMi auxiliary files (`runtime.yaml`, `service_core_*`, patches/).
- **`RuleWizard`** / **`CustomRuleManager`** — rule generation. The wizard builds a `url-test`/`fallback` group + `PROCESS-NAME`/`DOMAIN-SUFFIX` rules, inserts them before any `MATCH`/`FINAL`, and sets `find-process-mode`. Note: `RuleWizard.apply` works by **re-parsing the dumped text** (dump → parse → mutate → dump), which is intentional.
- **`MainViewModel`** ([MainViewModel.kt](app/src/main/java/com/recode/clashcraft/MainViewModel.kt)) — single `EditorState` `StateFlow`. All tree edits funnel through `editTree` → `applyEditedRoot` (bumps `revision`, marks dirty), then `scheduleSummary` debounces 250 ms before re-summarizing on `Dispatchers.Default`, with a **stale-snapshot guard** (`if (_state.value.root !== root) return@launch`) so an outdated async summarize can't clobber newer edits.
- **UI** ([ui/](app/src/main/java/com/recode/clashcraft/ui/)) — Compose/Material3. `ClashCraftApp.kt` is the 3-page shell (Overview / Config / Wizard) with top bar + snackbar. `ConfigGuiEditor.kt` recursively renders the generic tree by value type (Switch for boolean, dropdown for known enums, text/number fields, expandable objects, collapsed+paginated large lists), grouping known top-level keys into labeled sections. Callbacks are bundled in `ConfigEditorActions`.

### Structural-sharing invariant (do not break)

`ConfigTree.transform` rebuilds **only the nodes on the edited path** and reuses every untouched subtree by reference. This is what makes editing 50k-rule configs cheap, and [ConfigTreeTest](app/src/test/java/com/recode/clashcraft/data/ConfigTreeTest.kt) asserts it with `assertSame`. When touching the tree code, preserve this: copy along the path, alias the rest.

### Share-to-Clash chain

`MainViewModel.saveTo(..., shareAfterSave = true)` writes the file, copies it to `cacheDir/shared-configs/`, gets a FileProvider `content://` URI, and emits on the `shareRequests` SharedFlow. `MainActivity.openInClash()` ([MainActivity.kt](app/src/main/java/com/recode/clashcraft/MainActivity.kt)) builds targeted intents for three known packages (`com.nebula.clashmi`, `com.github.metacubex.clash.meta`, `com.github.kr328.clash` — also declared in the manifest `<queries>`), grants each `FLAG_GRANT_READ_URI_PERMISSION`, and falls back to a chooser if none resolve.

## Conventions & constraints

- **Never introduce fixed data classes for user config.** Editing must preserve `LinkedHashMap` key order and round-trip unknown keys. Unknown keys intentionally surface in the GUI's "其他配置" section.
- **SnakeYAML normalizes on dump** — comments are lost and indent/quote style is unified. This is by design; warn users to keep backups (README does).
- **Background work** (parse/dump/summarize/IO) runs on `Dispatchers.IO` or `Dispatchers.Default`, never on the main thread. Large configs are the common case.
- **ProGuard** keeps `org.yaml.snakeyaml.**` (see [app/proguard-rules.pro](app/proguard-rules.pro)); release builds shrink + minify.
- Signing keys, `local.properties`, and built APKs are gitignored — never commit them.
