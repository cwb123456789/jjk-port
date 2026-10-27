# Low-health emergency Purple target selection

The low-health Gojo NPC prototype now prioritizes the `SukunaFushiguroEntity` matchup when one is alive, non-allied, and within 32 blocks. If no such entity is available, it falls back to the nearest living non-allied entity in range. If no candidate exists, the prototype keeps its prior behavior and fires along Gojo's look direction.

This is a static-code improvement only. JujutsuCraft's custom NPC alliance behavior and the final projectile direction still require in-game testing; Java compilation has not been confirmed in this environment.
