# Native Mahito Phase-Two Renderer Adapter — r66

## Change

- Adds a GeckoLib `GeoModel` adapter that chooses JujutsuEnhancement's native-name mapped stage-two geometry when the native Mahito entity’s synchronized texture changes to `mahito_phase2__body` during Self-Embodiment of Perfection.
- Keeps JujutsuCraft's original `human.geo.json` outside those skills.
- Uses the synchronized native texture field as the model-switch signal, avoiding reliance on server-only persistent NBT on the client.
- Attaches the adapter to the existing JujutsuCraft Mahito GeckoLib renderer during `EntityRenderersEvent.AddLayers`, using a guarded reflective model-field replacement so the base mod remains an optional runtime dependency.
- Keeps the original JujutsuCraft animation resource (`human.animation.json`) to preserve the existing native skill animation keys. This release does **not** yet map the Bedrock-specific animation clips into the native skill dispatcher.

## Validation limits

The new static audit checks resource parsing, skill routing, renderer attachment, texture routing, and native animation-resource retention. The full regression suite and ZIP integrity should be run for this release. Java compilation and an in-game renderer test are not available in the current environment; the reflective attachment and geometry appearance therefore remain runtime-unverified.
