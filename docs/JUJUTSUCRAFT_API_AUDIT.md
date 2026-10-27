# JujutsuCraft ver50.1 integration audit

Scope: static inspection of the user-provided `JujutsuCraft-ver50.1-forge-1.20.1.jar`. This is not a runtime compatibility test.

## Confirmed existing procedure entry points

The supplied JAR contains the following public static `execute(LevelAccessor, double, double, double, Entity)` procedures, confirmed with `javap`:

| Existing feature | Procedure class | Confirmed signature |
|---|---|---|
| Malevolent Shrine | `net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure` | `execute(LevelAccessor, double, double, double, Entity)` |
| Hollow Purple | `net.mcreator.jujutsucraft.procedures.HollowPurpleProcedure` | `execute(LevelAccessor, double, double, double, Entity)` |
| AI Hollow Purple | `net.mcreator.jujutsucraft.procedures.AIPurpleProcedure` | `execute(LevelAccessor, double, double, double, Entity)` |
| AI Malevolent Shrine | `net.mcreator.jujutsucraft.procedures.AIMalevolentShrineProcedure` | `execute(LevelAccessor, double, double, double, Entity)` |
| Domain clash/battle | `net.mcreator.jujutsucraft.procedures.DomainExpansionBattleProcedure` | `execute(LevelAccessor, double, double, double, Entity)` |

The JAR also contains these relevant procedures: `AIFlameArrowProcedure`, `AIFlameTigerProcedure`, `AISukunaProcedure`, `AIItadoriYujiProcedure`, `AIBlackFlashProcedure`, `ItadoriDomainExpansionProcedure`, `MalevolentShrineActiveProcedure`, `DomainExpansionCreateBarrierProcedure`, and `HollowPurpleProcedure`.

## Integration decision

- Keep JujutsuCraft as the base mod and preserve its existing combat, entity, technique, and domain systems.
- For existing skills, prefer hooking the existing procedure/event path and replace only the requested presentation or behavior.
- For absent skills (e.g. the requested 200% Purple charge sequence, crouch-triggered unrestricted Purple, 0.2-second Unlimited Void, Sukuna finger transformation, and bespoke execution animations), add new entities/state/timing logic rather than pretending a similarly named existing procedure already implements them.
- The base mod is an optional dependency during development. Any calls into its classes must be guarded so the enhancement mod can fail gracefully when JujutsuCraft is not installed. Direct references from eagerly loaded classes can break startup if the optional dependency is missing.
- Do not directly invoke the listed procedures from a render tick. Skill calls must be server-authoritative and triggered once per state transition to avoid repeated damage or entity spam.

## Limitations

This audit confirms class and method signatures only. It does not prove that these methods can be safely invoked externally, that the obfuscated/transformed runtime signature is unchanged, or that their visuals can be replaced without additional event or renderer hooks. No build or in-game test has completed yet.
