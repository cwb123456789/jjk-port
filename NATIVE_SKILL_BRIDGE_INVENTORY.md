# JujutsuCraft native-skill bridge inventory

## What was verified

The supplied `JujutsuCraft-ver50.1-forge-1.20.1.jar` contains these class entries:

- `net/mcreator/jujutsucraft/entity/GojoSatoruEntity.class`
- `net/mcreator/jujutsucraft/entity/SukunaFushiguroEntity.class`
- `net/mcreator/jujutsucraft/procedures/MalevolentShrineActiveProcedure.class`
- `net/mcreator/jujutsucraft/procedures/MalevolentShrineProcedure.class`
- `net/mcreator/jujutsucraft/entity/EntityMalevolentShrineEntity.class`
- `net/mcreator/jujutsucraft/entity/EntityMalevolentShrine2Entity.class`

The inventory is checked by `tools/audit_native_skill_bridge_inventory.py` against the local JAR. This confirms class presence only; it does **not** establish the method signatures, trigger conditions, or safe server-side invocation contract.

## Integration status

`JujutsuCraftSkillBridge` currently uses a three-small-fireball spread as a temporary Furnace/灶 placeholder and spawns the enhancement mod's own shrine prototype. These are not native JujutsuCraft skill activations.

Next implementation step is to inspect the native procedure signatures and required entity/player state, then add a guarded bridge that invokes the verified server-side procedure only when its contract is understood. Do not replace the placeholder with reflective calls based only on class names: wrong procedure parameters or missing synchronized state can crash or desynchronize a world.
