from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java").read_text()
checks = {
    "origin uses Sukuna coordinates": "new Vec3(sukuna.getX()" in source and "sukuna.getY() + sukuna.getBbHeight() * 0.65D" in source,
    "aim uses player's view ray": "player.getEyePosition().add(player.getLookAngle().normalize().scale(24.0D))" in source,
    "direction aims from Sukuna to target point": "Vec3 direction = aimPoint.subtract(origin);" in source,
    "zero-vector fallback exists": "if (direction.lengthSqr() < 1.0E-6D) direction = player.getLookAngle();" in source,
    "projectile positioned at Sukuna origin": "arrow.setPos(origin.x, origin.y, origin.z);" in source,
    "projectile launched with computed direction": "arrow.launch(player, sukuna, direction);" in source,
    "sound originates from projectile origin": "origin.x, origin.y, origin.z," in source,
}
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"{len(checks)}/{len(checks)} checks passed")
