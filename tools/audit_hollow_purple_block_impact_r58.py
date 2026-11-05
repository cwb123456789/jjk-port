#!/usr/bin/env python3
"""Static audit: Hollow Purple flies THROUGH blocks, like the Bedrock projectile.

Bedrock moves ds:projectile with scripts/index.js (teleport by velocity each tick, no block test,
removed after 200 ticks). Terrain is deleted by functions (4x4sphere etc.), not by a collision.
An earlier version of this audit demanded a ray-traced stop at the first block; that contradicts
the source and was removed in r73.
"""
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "src/main/java/com/jujutsuenhancement/entity/HollowPurpleEntity.java"
text = source.read_text(encoding="utf-8")
checks = {
    "no block ray trace stops the projectile": "level().clip(" not in text and "ClipContext" not in text,
    "terrain is carved every tick by destroyTerrain": "destroyTerrain(server" in text,
    "carving respects mob griefing": "RULE_MOBGRIEFING" in text,
    "lifetime limit exists": "discard()" in text or "finish(server)" in text,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"Hollow Purple pass-through audit: {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
