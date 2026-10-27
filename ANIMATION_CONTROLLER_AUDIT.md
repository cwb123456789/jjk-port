# Animation controller reference audit

Date: 2026-10-09

- Added `tools/audit_animation_controller_refs.py`.
- The script scans Java `RawAnimation` calls using `thenLoop` / `thenPlayOnce` and verifies that each exact animation identifier exists in an animation JSON under the mod assets.
- Current result: 2 controller references checked, 0 missing identifiers.
- `animation.heian_sukuna.idle` exists in `heian_sukuna.animation.json`.
- `animation.malevolent_shrine.form` exists in `malevolent_shrine.animation.json`.

This is a static name check only. It does not verify GeckoLib runtime loading, Java compilation, or in-game animation playback. Gradle 8.8 download remains blocked by DNS resolution for `services.gradle.org` in the current environment.
