# Native Mahito Form Persistence — r41

## Change
The native form bridge no longer restores Mahito's original texture as soon as skill ID 1520 clears. Phase two is treated as a persistent transformation, matching the intended post-domain form behavior. The bridge still waits four server ticks before setting the synchronized native texture identifier `mahito_phase2__body`.

## Verified against supplied JujutsuCraft JAR
- Native class: `net.mcreator.jujutsucraft.entity.MahitoEntity`.
- `getTexture()` and `setTexture(String)` exist on that class.
- The native entity's default texture identifier is `mahito`.
- The JAR contains `assets/jujutsucraft/textures/entities/mahito_phase2__body.png`, so `mahito_phase2__body` is an actual asset identifier, not a guessed name.
- Entity texture is held in synchronized entity data, so setting it server-side uses the mod's existing sync path.

## Scope / limitations
This only changes texture selection on the native entity. It does not replace the native GeckoLib model or animation controller, nor the skill dispatcher. The `Texture` value is persisted by JujutsuCraft's entity save/load methods; this bridge's timing/applied flags are also stored in persistent entity data.

## Tests
Run `python3 tools/audit_native_mahito_form_bridge.py` and `python3 tools/run_regression_audits.py`. Gradle compilation and in-game verification remain blocked/unperformed until ForgeGradle dependencies are available.
