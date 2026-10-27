# JujutsuCraft 50.1 native skill entry map (verified against supplied JAR)

This file records class/method dispatch points verified by inspecting the supplied
`JujutsuCraft-ver50.1-forge-1.20.1.jar` with `javap -c -p`. It is a code-entry map,
not a claim that the skills below have already been replaced.

## Native entity classes found in the supplied JAR

- `net.mcreator.jujutsucraft.entity.MahitoEntity`
- `net.mcreator.jujutsucraft.entity.SukunaEntity`
- `net.mcreator.jujutsucraft.entity.SukunaFushiguroEntity`
- `net.mcreator.jujutsucraft.entity.SukunaPerfectEntity`
- `net.mcreator.jujutsucraft.entity.GojoSatoruEntity`
- `net.mcreator.jujutsucraft.entity.GojoSatoruSchoolDaysEntity`

## Native dispatcher signatures

The supplied JAR contains these static entry points:

- `net.mcreator.jujutsucraft.procedures.CursedTechniqueMahitoProcedure.execute(LevelAccessor, double, double, double, Entity)`
- `net.mcreator.jujutsucraft.procedures.CursedTechniqueSukunaProcedure.execute(LevelAccessor, double, double, double, Entity)`
- `net.mcreator.jujutsucraft.procedures.CursedTechniqueGojoProcedure.execute(LevelAccessor, double, double, double, Entity)`

Each dispatcher reads `entity.getPersistentData().getDouble("skill")` and branches on that numeric value.

## Skill IDs verified from dispatcher bytecode

### Mahito

| `skill` value | Called procedure |
|---:|---|
| 1503 | `AttackSpeedProcedure` |
| 1504 | `AttackOverheadProcedure` |
| 1505 | `IdleTransfigurationProcedure` |
| 1506 | `TechniqueMahitoHand3Procedure` |
| 1507 | `MahitoGunProcedure` |
| 1508 | `TechniqueBodyRepelSingleProcedure` |
| 1509 | `TechniqueBodyRepelProcedure` |
| 1510 | `TechniquePolymorphicSoulIsomerProcedure` |
| 1515 | `InstantSpiritBodyofDistortedKillingProcedure` |
| 1516 | `AttackStrongMahitoProcedure` |
| 1520 | `SelfEmbodimentOfPerfectionProcedure` |

### Sukuna

| `skill` value | Called procedure |
|---:|---|
| 105 | `DismantleProcedure` |
| 106 | `CleaveProcedure` |
| 107 | `OpenProcedure` |
| 111 | `SkillSukunaCombo1Procedure` |
| 112 | `SkillSukunaCombo2Procedure` |
| 113 | `SkillSukunaCombo3Procedure` |
| 120 | `MalevolentShrineProcedure` |

The dispatcher also contains further skill ranges (including values 150–199); those have not been fully mapped in this short index. Do not assume `107` alone is a complete, standalone NPC “灶/开” activation without verifying its state and preconditions.

### Gojo

| `skill` value | Called procedure |
|---:|---|
| 205 | `InfinityProcedure` |
| 206 | `TechniqueBlueProcedure` |
| 207 | `TechniqueRedProcedure` |
| 208 | `TechniqueBluePunchProcedure` |
| 215 | `HollowPurpleProcedure` |
| 220 | `UnlimitedVoidProcedure` |

## Integration status and constraints

- `JujutsuCraftSkillBridge` currently handles the **custom** `HeianSukunaEntity` prototype. It does not replace the native Sukuna dispatcher.
- `MahitoPrototypeEntity` is a separate test entity. Its 4-tick (0.2-second) model switch is not yet wired to `MahitoEntity`.
- `HollowPurpleEntity` and the NPC logic in `PrototypeCommands` are custom approximations; they do not replace the native Gojo renderer or the native `HollowPurpleProcedure` animation.
- A future native bridge must preserve the original skill state/cooldown/AI assumptions. Simply changing the `skill` NBT value is not sufficient proof that a complete native skill executes correctly.

## Verification method

The class names and numeric branches above were checked against the bytecode of the user-provided JujutsuCraft 50.1 JAR. No runtime/game test is implied by this document.
