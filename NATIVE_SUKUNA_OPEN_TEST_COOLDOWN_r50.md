# Native Sukuna Open test cooldown — r50

The `/jujutsuenhancement sukuna_native_open_test` command now has a per-native-entity 100-tick (5-second) test cooldown. The guard is checked before writing skill ID 107. The cooldown timestamp is committed only after the native `CursedTechniqueSukunaProcedure` reflection call returns without throwing. Other Sukuna entities have independent timestamps.

This is a guard against repeated manual test invocations, not an automatic AI integration and not proof that the game successfully rendered the attack. The existing native dispatcher path remains unchanged.

Validation: static audit only; Gradle compilation and in-game execution have not been completed.
