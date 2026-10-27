#!/usr/bin/env python3
"""Static check for the in-game Mahito prototype spawn test command."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
commands = (ROOT / "src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java").read_text()
registry = (ROOT / "src/main/java/com/jujutsuenhancement/registry/ModEntities.java").read_text()
mahito = (ROOT / "src/main/java/com/jujutsuenhancement/entity/MahitoPrototypeEntity.java").read_text()
checks = {
    "command is registered": 'Commands.literal("mahito_prototype")' in commands,
    "command routes to spawn method": "spawnMahitoPrototype(ctx.getSource().getPlayerOrException())" in commands,
    "spawn method creates registered prototype": "ModEntities.MAHITO_PROTOTYPE.get().create(level)" in commands,
    "prototype entity type exists": 'ENTITY_TYPES.register("mahito_prototype"' in registry,
    "message discloses independent test entity limitation": "尚未替换 JujutsuCraft 原生真人 NPC" in commands,
    "prototype has 4-tick transformation boundary": "transformTicks >= 4" in mahito or "transformTicks < 4" in mahito,
}
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
print(f"SUMMARY: {sum(checks.values())}/{len(checks)} checks passed")
raise SystemExit(0 if all(checks.values()) else 1)
