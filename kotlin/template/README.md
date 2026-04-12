# Kotlin template

This template generates a Kotlin JVM project with a `stack_profile` prompt that controls the initial dependency bundle.

## Stack profiles

- `default`: baseline Kotlin application with Log4j and test dependencies.
- `cli`: `default` plus `clikt` and `mordant` for command-line apps.
- `web`: `default` plus a Ktor server stack with JSON serialization.
- `db`: `default` plus Exposed, HikariCP, Flyway, and PostgreSQL driver support.
- `web-db`: combines the `web` and `db` dependency bundles.

## Notes

- The generated `build.gradle.kts` is a normal Gradle build file, so you can still add or remove dependencies after project creation.
- Keep `stack_profile` for stable dependency bundles. Use manual Gradle edits for one-off libraries that do not belong in every generated project.
- The generated `Main.kt` also changes by profile: `web` and `web-db` add `ServerModule`, while `db` and `web-db` add `DatabaseModule`.