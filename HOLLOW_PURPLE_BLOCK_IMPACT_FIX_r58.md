# r58 — Hollow Purple block-impact positioning

## Change

`HollowPurpleEntity` now moves to the ray-traced block contact point before spawning its terminal particle burst. The timeout burst uses the intended next position. Previously, the entity discarded itself after emitting the burst at its previous tick position, which could make the impact effect appear offset from the wall.

## Validation

- `tools/audit_hollow_purple_block_impact_r58.py`: 8/8 static checks passed.
- `tools/run_regression_audits.py`: 28/28 static audits passed, including JSON/resource validation (18 JSON files).
- Java compilation and Minecraft runtime testing were not performed; this static audit does not establish in-game behavior.
