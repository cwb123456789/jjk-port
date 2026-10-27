# Sukuna Entity-Owned Cooldowns and Combo State

## Change

The Heian Sukuna interaction bridge previously stored basic-attack cooldown, domain cooldown, and combo sequence in the interacting player's persistent data. This allowed multiple players to bypass cooldowns by alternating interactions with the same Sukuna entity and caused the combo sequence to be player-specific.

These values now live on `HeianSukunaEntity`:

- `nextBasicAttackGameTime`
- `nextDomainGameTime`
- `attackSequence`

The bridge checks the relevant entity cooldown before spawning projectiles or creating a domain. It commits the basic-attack sequence/cooldown only after at least one projectile was accepted by the server, and commits domain cooldown only after the shrine entity was successfully added.

## Limits

This is a server-side state ownership fix, not an implementation of the native JujutsuCraft Stove skill. The basic attack still uses vanilla small fireballs as an explicitly temporary placeholder, and the shrine remains a prototype. The cooldown state is not persisted across world save/reload; it resets when the entity is reloaded. Forge compilation and in-game verification have not been completed in this environment.
