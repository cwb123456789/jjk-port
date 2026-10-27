#!/usr/bin/env python3
"""Static regression checks for Heian Sukuna interaction handling.

This audit deliberately distinguishes the custom flame-arrow approximation from
JujutsuCraft's native Stove skill. It does not claim runtime or in-game validation.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "src/main/java/com/jujutsuenhancement/integration/JujutsuCraftSkillBridge.java"
ENTITY = ROOT / "src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java"
bridge = BRIDGE.read_text(encoding="utf-8")
entity = ENTITY.read_text(encoding="utf-8")
checks = {
    "server-player gate": "event.getEntity() instanceof ServerPlayer player" in bridge,
    "client-side guard": "event.getLevel().isClientSide()" in bridge,
    "only main hand": "event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND" in bridge,
    "projectile spawn success gates attack commit": "if (player.serverLevel().addFreshEntity(arrow)) {" in bridge and "sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);" in bridge,
    "cooldown applied by entity commit": "nextBasicAttackGameTime = gameTime + Math.max(0, cooldownTicks);" in entity,
    "failed spawn does not advance sequence": "attackSequence = (attackSequence + 1) % ATTACKS.length;" in entity and bridge.find("if (player.serverLevel().addFreshEntity(arrow)) {") < bridge.find("sukuna.commitFugaRelease(now, SINGLE_COOLDOWN_TICKS);"),
    "animation state is synchronized": "SynchedEntityData" in entity and "entityData.set(SKILL_ANIMATION, normalized)" in entity,
    "approximation is explicitly distinguished from native skill": "custom flame-arrow approximation" in bridge and "does not invoke JujutsuCraft's native skill dispatcher" in bridge and "SmallFireball" not in bridge,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if not all(checks.values()):
    raise SystemExit(1)
print("PASS: server-flow static audit; not a substitute for Forge compilation or an in-game test.")
