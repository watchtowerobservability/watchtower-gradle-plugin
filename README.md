# watchtower-gradle-plugin

Gradle plugin for Android and JVM builds. After a release build it uploads ProGuard/R8 mappings and native
symbols (`.so`) to Watchtower so stack traces are readable, and it writes the release name where the
[Java SDK](https://github.com/watchtowerobservability/watchtower-java) picks it up.

## Install

GitHub Packages Maven repository `https://maven.pkg.github.com/watchtowerobservability/watchtower-gradle-plugin`
(token with `read:packages`). Attested jars are also on each release.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.github.com/watchtowerobservability/watchtower-gradle-plugin") {
            credentials { username = providers.environmentVariable("GITHUB_ACTOR").orNull; password = providers.environmentVariable("GITHUB_TOKEN").orNull }
        }
    }
}
```

## Quick start

```kotlin
plugins { id("io.github.watchtowerobservability.watchtower") version "0.1.0" }

watchtower {
    url.set("https://watchtower.example.com")   // or $WT_API
    project.set("prj_123")                       // or $WT_PROJECT
    release.set("1.4.2")                         // default: project version
}
// the key comes from $WT_API_KEY (a project-admin key); keep it out of build files
```

JVM: run `./gradlew watchtowerUpload` (set `watchtower.mappingFiles` to your ProGuard output).
Android: `assemble*Release` and `bundle*Release` run `watchtowerUpload` afterwards.

## Integrations

| Capability | Android | JVM |
|---|---|---|
| ProGuard/R8 mapping upload (`build/outputs/mapping/**/mapping.txt`) | automatic after release builds | `watchtowerUpload` with `mappingFiles` |
| Native symbol upload (`*.so` in native debug metadata and merged native libs) | automatic | set `nativeSymbolDirs` |
| Release name injected as `META-INF/watchtower.properties` | yes | yes (java plugin) |

## Configuration

| Option | Env | Default |
|---|---|---|
| `watchtower.url` | `WT_API` | none, required |
| `watchtower.project` | `WT_PROJECT` | none, required |
| `watchtower.authToken` | `WT_API_KEY` | none, required; sent as `Authorization: Bearer`, never logged or an up-to-date check input |
| `watchtower.release` | `WT_RELEASE` is read by the SDK | project version |
| `watchtower.mappingFiles` | | `build/outputs/mapping/**/mapping.txt` |
| `watchtower.nativeSymbolDirs` | | `build/intermediates/native_debug_metadata`, `merged_native_libs` |

The upload uses the same API as `wtctl`: it creates the release (idempotent), then posts each file gzip-compressed
to `/v1/projects/{id}/artifacts`. Uploading identical bytes again is a no-op.

## Provenance

`gh attestation verify <file> --repo watchtowerobservability/watchtower-gradle-plugin`

## License

Apache-2.0. Source of truth is the Watchtower monorepo; this repository is a published mirror.
