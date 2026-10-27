# Build changelog — r42

## Native Mahito diagnostics and animation compatibility

- Added `/jujutsuenhancement mahito_native_status` in `src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java`.
- The command searches within 32 blocks for the native `net.mcreator.jujutsucraft.entity.MahitoEntity` and reports its skill ID, texture, synced animation, and whether the r40+ texture bridge has applied. It does not modify the entity.
- Added `NATIVE_MAHITO_ANIMATION_COMPATIBILITY_r42.md` documenting JAR-verified native model/animation resources versus the imported Bedrock-derived animation identifiers.
- Added `tools/audit_native_mahito_animation_compatibility.py` and wired it into `tools/run_regression_audits.py`.
- Static audits: 16/16 passed. ZIP integrity checked after packaging.

## Explicit limits

- This is a diagnostic and compatibility checkpoint, not the native Bedrock animation replacement.
- Native Mahito still uses JujutsuCraft's `human.geo.json` and `human.animation.json`; the Bedrock-derived animation resource is not connected to the native renderer.
- Gradle compile and in-game validation remain blocked/unperformed; static checks do not prove runtime correctness.
