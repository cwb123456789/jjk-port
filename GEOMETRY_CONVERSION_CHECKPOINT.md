# Java/GeckoLib geometry conversion checkpoint

Converted Bedrock geometry JSON files into a GeckoLib-oriented resource layout.
This is a resource conversion checkpoint, not proof of in-game rendering; runtime renderer registration and a successful Gradle build are still pending.

| Output | Source geometry | Bones | Cubes | Bytes |
|---|---|---:|---:|---:|
| `geo/malevolent_shrine.geo.json` | `geometry.malevolent_shrine` | 126 | 2326 | 4639440 |
| `geo/heian_shrine.geo.json` | `geometry.heian_shrine` | 195 | 1626 | 3190880 |
| `geo/innate_sukuna.geo.json` | `geometry.innate_sukuna` | 16 | 49 | 102041 |
| `geo/yuji_domain.geo.json` | `geometry.yuji_domain` | 2 | 401 | 757228 |

## Compatibility notes

- Set `format_version` to `1.12.0` and gave each geometry a unique `geometry.jujutsuenhancement.*` identifier.
- Copied associated candidate PNG textures to `textures/entity/` without modifying image data.
- These models still need GeckoLib dependency, model/renderer classes, texture-size and pivot checks, and in-game validation. Geometry format similarity does not guarantee perfect visual compatibility.
- Current build blocker remains inability to download Gradle 8.8 in this environment.
