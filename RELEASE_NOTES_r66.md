# Release notes — r66

## Native Mahito phase-two geometry renderer adapter

- Added a GeckoLib model adapter for the native JujutsuCraft Mahito entity.
- Connected the adapter to the existing renderer during Forge's client `AddLayers` event, guarded for missing entity types and renderer API differences.
- Uses the synchronized native texture field as the switch signal: the phase-two mapped geometry and phase-two texture are selected when the native form bridge applies `mahito_phase2__body`; otherwise the original native human geometry and native texture remain active.
- Preserves JujutsuCraft's native animation resource so existing skill animation keys remain available.
- Added a dedicated 9-check static audit and included it in the regression runner.

## Validation

- Static regression: 35/35 audits passed.
- Resource validation: 18 JSON files checked.
- ZIP integrity: verified after packaging.
- Java compile and in-game rendering: not verified in this environment. In particular, the reflective renderer model-field replacement and the visual alignment of the mapped geometry still require a client launch test.

## Known scope boundary

The Bedrock-derived `mahito_stage2_native.animation.json` is still not wired into the native skill dispatcher. r66 switches the geometry/texture while retaining native animations; Bedrock-specific animation retargeting is a follow-up task.
