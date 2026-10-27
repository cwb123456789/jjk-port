# r43 — Native Mahito domain-start animation bridge

## Change
When the native `net.mcreator.jujutsucraft.entity.MahitoEntity` enters skill `1520`, the bridge waits four server ticks. It then selects JujutsuCraft's existing `mahito_phase2__body` texture and invokes the entity's public `setAnimation("domain_expansion1")` method.

## Compatibility evidence
- `MahitoModel` uses `assets/jujutsucraft/geo/human.geo.json` and `assets/jujutsucraft/animations/human.animation.json`.
- The native animation file contains `domain_expansion1`; it does not contain Bedrock key `animation.mahito.stage2`.
- `MahitoEntity` exposes public `setAnimation(String)`, which writes the synchronized animation accessor consumed by its `procedure` controller.

## Limits
This reuses the Java mod's native domain-start animation; it does **not** yet replace the native model with the Bedrock stage-two geometry, import the Bedrock stage-two animation, or replace Mahito's skill dispatcher. The texture and animation timing are statically audited only; the behavior still requires an in-game test.
