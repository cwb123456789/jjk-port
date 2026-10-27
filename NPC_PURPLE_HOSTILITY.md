# Gojo NPC 200% Purple target hostility

The five-second NPC charge now only starts against a living `SukunaFushiguroEntity` that Gojo does not consider an ally. The same hostility check is repeated every tick while the target UUID is locked. If the target becomes allied, dies, leaves the level, or moves beyond 32 blocks, the charge is cancelled instead of firing at that target.

This is a source-level safeguard. The static audit does not replace Java compilation or an in-game test with the exact JujutsuCraft build and team/faction setup.
