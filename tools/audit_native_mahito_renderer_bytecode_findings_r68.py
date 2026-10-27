#!/usr/bin/env python3
"""Static documentation audit for the verified Mahito renderer bytecode findings."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
report = root / "NATIVE_MAHITO_RENDERER_BYTECODE_FINDINGS_r68.md"
renderer = root / "src/main/java/com/jujutsuenhancement/client/ClientModEvents.java"
model = root / "src/main/java/com/jujutsuenhancement/client/model/NativeMahitoPhase2GeoModel.java"
checks = {
    "bytecode findings report exists": report.is_file(),
    "report identifies DynamicGeoEntityRenderer": "DynamicGeoEntityRenderer" in report.read_text(encoding="utf-8"),
    "report records native render layers": "GenericArmorLayer" in report.read_text(encoding="utf-8") and "GenericItemLayer" in report.read_text(encoding="utf-8"),
    "report does not claim runtime verification": "No renderer swap, Java compilation, or in-game rendering is claimed" in report.read_text(encoding="utf-8"),
    "renderer adapter source exists": renderer.is_file(),
    "phase two model adapter source exists": model.is_file(),
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
print(f"Result: {sum(checks.values())}/{len(checks)}")
raise SystemExit(0 if all(checks.values()) else 1)
