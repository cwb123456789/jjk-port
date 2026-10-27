#!/usr/bin/env python3
"""Flag Bedrock poly_mesh geometry that GeckoLib 4.8.4 may fail to deserialize.

The user's Oct 5 client log showed GeckoLib 4.8.4 crashing while loading
mahitodomainpatch:geo/perfection_domain.geo.json, in PolyMesh deserialization:
"Array must have size 1, but has size 3". This audit flags poly_mesh usage
so it cannot silently pass the basic JSON/geometry structural audit.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src/main/resources/assets"
errors = []
checked = 0

for path in sorted(ASSET_ROOT.rglob("*.geo.json")):
    checked += 1
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"INVALID JSON: {path.relative_to(ROOT)}: {exc}")
        continue
    for gi, geometry in enumerate(data.get("minecraft:geometry", [])):
        for bone in geometry.get("bones", []):
            if "poly_mesh" in bone or "poly_mesh" in bone.get("cubes", []):
                errors.append(
                    f"UNSUPPORTED/RISKY poly_mesh: {path.relative_to(ROOT)} "
                    f"geometry[{gi}] bone={bone.get('name')}"
                )
            for ci, cube in enumerate(bone.get("cubes", [])):
                if isinstance(cube, dict) and "poly_mesh" in cube:
                    errors.append(
                        f"UNSUPPORTED/RISKY poly_mesh: {path.relative_to(ROOT)} "
                        f"geometry[{gi}] bone={bone.get('name')} cube[{ci}]"
                    )

print(f"Checked {checked} geometry files for poly_mesh incompatibility.")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print("PASS: no poly_mesh blocks found in this mod's geometry resources.")
