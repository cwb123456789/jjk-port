#!/usr/bin/env python3
"""Static guard for the custom Sukuna flame arrow's visible client representation."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
entity = (root / "src/main/java/com/jujutsuenhancement/entity/SukunaFlameArrowEntity.java").read_text()
client = (root / "src/main/java/com/jujutsuenhancement/client/ClientModEvents.java").read_text()
checks = {
    "entity implements ItemSupplier": "implements ItemSupplier" in entity,
    "projectile returns a visible item stack": "public ItemStack getItem()" in entity and "new ItemStack(Items.FIRE_CHARGE)" in entity,
    "renderer import present": "import net.minecraft.client.renderer.entity.ThrownItemRenderer;" in client,
    "projectile renderer registered": "event.registerEntityRenderer(ModEntities.SUKUNA_FLAME_ARROW.get(), ThrownItemRenderer::new);" in client,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {label}")
if not all(checks.values()):
    raise SystemExit(1)
