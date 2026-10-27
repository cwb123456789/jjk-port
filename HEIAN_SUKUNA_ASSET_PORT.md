# Heian Sukuna asset port checkpoint

- Extracted the matching Bedrock geometry `heian_sukuna.geo.json` from the supplied resource pack.
- Extracted its paired `heian_sukuna.png` texture.
- Geometry declares a 64x64 texture and 62 bones.
- The converted `heian_sukuna.animation.json` is present, but it is not yet wired to a dedicated Forge entity, GeckoLib model, renderer, or animation controller.
- The source geometry is preserved as Bedrock-format JSON; renderer compatibility and coordinate/pivot conversion still require validation.
- No successful Gradle build or in-game render has been completed.
