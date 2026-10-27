#!/usr/bin/env python3
"""Static audit: Hollow Purple impact burst must use the collision contact point."""
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "src/main/java/com/jujutsuenhancement/entity/HollowPurpleEntity.java"
text = source.read_text(encoding="utf-8")
checks = {
    "block collision uses ray-traced location": "hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to" in text,
    "impact coordinates are applied to entity position": "this.setPos(impact.x, impact.y, impact.z);" in text,
    "core burst uses impact point": "sendParticles(PURPLE_CORE, impact.x, impact.y, impact.z, 36" in text,
    "blue shell burst uses impact point": "sendParticles(BLUE_EDGE, impact.x, impact.y, impact.z, 24" in text,
    "explosion burst uses impact point": "sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 3" in text,
    "entity discarded after impact": "discard();\n                return;" in text,
    "block ray trace still precedes impact resolution": text.index("HitResult hit = this.level().clip") < text.index("Vec3 impact = hit.getType()"),
    "impact branch does not advance through wall first": text.index("Vec3 impact = hit.getType()") < text.index("this.setPos(to.x, to.y, to.z);"),
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
print(f"{len(checks)-len(failed)}/{len(checks)} passed")
raise SystemExit(bool(failed))
