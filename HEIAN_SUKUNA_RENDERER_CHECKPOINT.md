# Heian Sukuna renderer integration checkpoint

Added a dedicated `HeianSukunaEntity`, `HeianSukunaModel`, and `HeianSukunaRenderer`; registered the entity and client renderer. The controller requests `animation.heian_sukuna.idle`. The geometry JSON declares GeckoLib-compatible `format_version` 1.12.0.

This is source-level integration only. The geometry has not been validated by GeckoLib at runtime, the animation coordinate/pivot conversion is not yet proven, and the project has not successfully compiled or been tested in Minecraft.

Summon ID intended for testing after a successful build: `/summon jujutsuenhancement:heian_sukuna`.


Compatibility cleanup: removed animation tracks referencing bones absent from the paired geometry (`cloth_part`, `hiten`). These tracks are omitted rather than guessed; corresponding motion is lost until the matching geometry or correct bone mapping is found.
