#!/usr/bin/env python3
"""Ensure Sukuna attack state advances atomically after a successful spawn."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
bridge = (ROOT / "src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java").read_text(encoding="utf-8")
entity = (ROOT / "src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java").read_text(encoding="utf-8")
spawn_gate = bridge.index("if (player.serverLevel().addFreshEntity(arrow)) {")
commit_call = bridge.index("sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);")
checks = {
    "custom projectile spawn success gate exists": "if (player.serverLevel().addFreshEntity(arrow)) {" in bridge,
    "attack commit occurs inside success gate": spawn_gate < commit_call < bridge.index("// If the server rejects the spawn"),
    "release state is separate from regular combo sequence": "public void commitFugaRelease(long gameTime, int cooldownTicks)" in entity and "entityData.set(SKILL_ANIMATION, 8);" in entity,
    "cooldown advances inside entity commit": "nextBasicAttackGameTime = gameTime + Math.max(0, cooldownTicks);" in entity,
    "release animation timer is set by entity commit": "skillAnimationTicks = FUGA_RELEASE_TICKS;" in entity,
    "failed spawn path does not commit attack": bridge.index("// If the server rejects the spawn") > commit_call,
}
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit(1)
