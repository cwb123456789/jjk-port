# Mahito stage-2 model/texture and skill-selection checkpoint (2026-10-10)

- The prototype renderer now switches from `geo/mahito.geo.json` + `textures/entity/mahito.png` to `geo/mahito_stage2.geo.json` + `textures/entity/mahito_stage2.png` once the synchronized transformation state completes.
- Post-transformation interaction has two prototype skill paths: right-click for Soul Strike (8 magic damage, 4-block radius, 30-tick cooldown); crouch + right-click for a wider Idle Transfiguration burst (14 magic damage, 6-block horizontal radius, 60-tick slowness, 160-tick cooldown).
- This uses the provided Bedrock-derived stage-2 assets already present in the project. It does not yet replace the original JujutsuCraft Mahito entity or reproduce every original Bedrock skill/animation; those require integration with the actual JujutsuCraft entity and individual skill assets/procedures.
- Resource audit currently fails on existing Mahito animation keyframe schema checks and an audit-script geometry parsing issue; therefore no claim of full resource validation is made. Java compilation and in-game tests remain unverified.
