# Native Mahito animation compatibility checkpoint — r42

## Confirmed from the supplied JujutsuCraft JAR

- Native entity: `net.mcreator.jujutsucraft.entity.MahitoEntity`.
- Its native `MahitoModel` loads `jujutsucraft:geo/human.geo.json` and `jujutsucraft:animations/human.animation.json`.
- The native animation file contains names such as `domain_expansion1`, `ragnaraku2`, and `idle`.
- The imported Bedrock-derived file `jujutsuenhancement:animations/mahito_compatible.animation.json` instead uses names such as `animation.mahito.domain_start`, `animation.mahito.domain_idle`, `animation.mahito.domain`, and `animation.mahito.stage2`.
- Those files are not interchangeable by changing only the animation string: the native renderer still uses the JujutsuCraft human geometry and animation resource. Forcing Bedrock animation names onto the native controller without replacing the model's animation resource would be unsafe and can produce a missing-animation/no-op result.

## r42 change

Added `/jujutsuenhancement mahito_native_status`, a read-only in-game diagnostic that finds the nearest native Mahito within 32 blocks and reports `skill`, `getTexture()`, `getSyncedAnimation()`, and whether the form bridge has applied. It makes it possible to test the existing native texture bridge against the actual entity instead of guessing from the standalone prototype.

## Remaining blocker

A real Bedrock-style native animation/model swap needs a verified renderer/model integration strategy (for example a compatible renderer replacement or a carefully configured mixin) and then compilation plus in-game verification. This checkpoint deliberately does not claim the native animation replacement is implemented.
