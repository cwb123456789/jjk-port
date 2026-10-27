# Native Mahito renderer bytecode findings (r68)

## Verified against the supplied JujutsuCraft v50.1 JAR

Inspected with `javap -p` and `javap -c -p` using `/mnt/data/base.jar` (symlink to the supplied base JAR).

- `net.mcreator.jujutsucraft.client.renderer.MahitoRenderer` extends `software.bernie.geckolib.renderer.DynamicGeoEntityRenderer<MahitoEntity>`.
- Its constructor creates a `new MahitoModel()` and passes it to the `DynamicGeoEntityRenderer` constructor.
- The constructor then adds the original `GenericArmorLayer` and `GenericItemLayer`.
- `net.mcreator.jujutsucraft.entity.model.MahitoModel` extends `GeoModel<MahitoEntity>` and supplies model, texture, and animation resources.

## Consequence

The previous r66/r67 reflection strategy scans for a mutable `GeoModel` field on the renderer. The model is initially passed to the GeckoLib superclass constructor, so the field may be inherited and its mutability depends on the exact GeckoLib version. Static audits cannot prove that replacing it will work at runtime. A safer integration must confirm the resolved GeckoLib superclass fields and preserve the native armor/item layers.

## Status

This report records bytecode inspection only. No renderer swap, Java compilation, or in-game rendering is claimed as verified in r68.
