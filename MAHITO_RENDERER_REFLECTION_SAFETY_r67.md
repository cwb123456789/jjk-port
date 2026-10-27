# r67 — Native Mahito renderer reflection safety

This change narrows the reflective model-field replacement used by the optional phase-two adapter. It now skips static and final fields, checks that the current field value is a compatible model instance, and logs the exact field name on success. If no eligible field is found, the original model remains in place.

## Scope and limits

- This is a defensive compatibility improvement, not proof that the original JujutsuCraft renderer exposes a replaceable instance field.
- The adapter still depends on the native entity being registered as `jujutsucraft:mahito` and its renderer being a GeckoLib `GeoEntityRenderer`.
- Static audits do not verify Minecraft/Forge binary compatibility, Java compilation, rendered geometry, or in-game animation playback.
- Compile and client-launch testing remain required before calling the Mahito stage-two renderer integration complete.
