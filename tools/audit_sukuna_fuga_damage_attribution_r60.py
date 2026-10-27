#!/usr/bin/env python3
"""Static regression check: Fuga is aimed by the player but owned by Sukuna."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
bridge = (root / "src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java").read_text()
arrow = (root / "src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java").read_text()
checks = {
    "projectile owner is Sukuna": "arrow.launch(sukuna, player, direction)" in bridge,
    "player remains excluded from projectile collision": "ignoredEntityUuid = ignoredEntity.getUUID()" in arrow and "ignoredEntityUuid == null || !ignoredEntityUuid.equals(entity.getUUID())" in arrow,
    "damage attribution uses projectile owner": "Entity damageOwner = owner != null ? owner : this;" in arrow and "indirectMagic(this, damageOwner)" in arrow,
    "owner allies are filtered from damage": "owner.isAlliedTo(candidate)" in arrow,
    "aim direction remains player-controlled": "aimPoint = player.getEyePosition().add(player.getLookAngle().normalize().scale(24.0D))" in bridge,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {name}")
if not all(checks.values()):
    raise SystemExit(1)
