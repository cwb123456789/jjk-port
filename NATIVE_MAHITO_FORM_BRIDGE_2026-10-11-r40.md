# Native Mahito form bridge — r40

## Implemented

- Added `NativeMahitoFormBridge`, a Forge living-tick subscriber that only targets the exact JujutsuCraft native `net.mcreator.jujutsucraft.entity.MahitoEntity` class on the server.
- While the native entity's persistent `skill` value is `1520` (Self-Embodiment of Perfection), it waits four entity ticks (0.2 seconds at 20 TPS), then calls the native entity's public `setTexture("mahito_phase2__body")` method.
- Saves the previous texture and restores it when the native skill value leaves `1520`.
- Uses reflection so the project does not need to compile against JujutsuCraft classes directly. Reflection failures fail closed and leave the native dispatcher alone.

## Boundaries / not yet verified

- This is a narrow native-entity integration, not a complete Bedrock skin/animation replacement.
- It selects the texture identifier already present in JujutsuCraft 50.1 (`assets/jujutsucraft/textures/entities/mahito_phase2__body.png`) so the native renderer can resolve it. It does not yet replace the native geometry, combine the separate phase-two head texture, or replace the native GeckoLib animation controller.
- The bridge does not override the native skill dispatcher or alter damage logic.
- No successful Gradle Java compilation or in-game test has been performed in this environment. The current blocker remains offline ForgeGradle plugin resolution.
