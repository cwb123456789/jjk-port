#!/usr/bin/env python3
"""Guard the flame arrow against use of a newer, non-1.20.1 fire API."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java").read_text()
assert "livingTarget.setSecondsOnFire(4)" in source, "Use the Minecraft 1.20.1-compatible fire API"
assert "igniteForSeconds(" not in source, "igniteForSeconds is not the expected 1.20.1 API"
print("PASS: flame arrow uses the 1.20.1 fire API")
