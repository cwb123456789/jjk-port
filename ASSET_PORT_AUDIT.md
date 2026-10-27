# Asset port audit

Source: provided `咒术觉醒v1.81.mcaddon`, resource-pack subarchive. The outer add-on contains separate behavior/resource packages; the resource package declares minimum Bedrock engine version 1.20.80. Its `.geo.json`, animation, particle, and entity JSON files are Bedrock formats, not directly loadable as Java Forge assets.

## Extracted PNG candidates

These PNGs were copied without altering pixel data into `assets/jujutsuenhancement/textures/imported_bedrock/`. They are candidate source textures only; no Java entity/model currently references them.

- `heian_sukuna.png` ← `JujutsuAwa/textures/blocks/heian_sukuna.png` (3,136 bytes)
- `domain_barrier.png` ← `JujutsuAwa/textures/entity/domain_barrier.png` (356 bytes)
- `domain_barrier2.png` ← `JujutsuAwa/textures/entity/domain_barrier2.png` (112 bytes)
- `domain_black.png` ← `JujutsuAwa/textures/entity/domain_black.png` (91 bytes)
- `entity__heian_sukuna.png` ← `JujutsuAwa/textures/entity/heian_sukuna.png` (99,831 bytes)
- `heian_sukuna_red.png` ← `JujutsuAwa/textures/entity/heian_sukuna_red.png` (21,054 bytes)
- `perfection_domain.png` ← `JujutsuAwa/textures/entity/perfection_domain.png` (735 bytes)
- `perfection_hands.png` ← `JujutsuAwa/textures/entity/perfection_hands.png` (13,074 bytes)
- `perfection_hands2.png` ← `JujutsuAwa/textures/entity/perfection_hands2.png` (579 bytes)
- `student_gojo.png` ← `JujutsuAwa/textures/entity/student_gojo.png` (190,101 bytes)
- `student_gojo2.png` ← `JujutsuAwa/textures/entity/student_gojo2.png` (220,889 bytes)
- `domainamp.png` ← `JujutsuAwa/textures/items/domainamp.png` (596 bytes)
- `simpledomain.png` ← `JujutsuAwa/textures/items/simpledomain.png` (4,084 bytes)
- `soul_shrine1.png` ← `JujutsuAwa/textures/items/soul_shrine1.png` (4,370 bytes)
- `soul_shrine2.png` ← `JujutsuAwa/textures/items/soul_shrine2.png` (5,666 bytes)
- `heian_shrine.png` ← `JujutsuAwa/textures/models/heian_shrine.png` (368,178 bytes)
- `malevolent_shrine.png` ← `JujutsuAwa/textures/models/malevolent_shrine.png` (131,067 bytes)
- `malevolent_shrine_gray.png` ← `JujutsuAwa/textures/models/malevolent_shrine_gray.png` (122,499 bytes)
- `sukuna_innate_domain.png` ← `JujutsuAwa/textures/models/sukuna_innate_domain.png` (245 bytes)
- `yuji_domain.png` ← `JujutsuAwa/textures/models/yuji_domain.png` (67,306 bytes)
- `simpledomain1.png` ← `JujutsuAwa/textures/particle/simpledomain1.png` (4,510 bytes)
- `simpledomain2.png` ← `JujutsuAwa/textures/particle/simpledomain2.png` (6,448 bytes)

## Next technical conversion

1. Convert selected Bedrock geometry to Java-compatible geometry or recreate the relevant display model using GeckoLib.
2. Port Bedrock particle JSON into Java particle registrations/rendering; schemas are incompatible.
3. Connect domain activation to JujutsuCraft APIs only after identifying stable public hooks or safe event-based integration.
4. Build and test with Forge 1.20.1 / 47.4.26. This environment currently cannot download Gradle 8.8, so a compiled JAR has not been produced.
