# Stove prototype reliability fix

This update changes interaction handling so that cooldowns and event cancellation happen only if at least one projectile was accepted by the server.

- `spawnFireball` now returns whether `ServerLevel.addFreshEntity` succeeded.
- Single-shot cooldown is only recorded on a successful spawn.
- Fan-shot cooldown is recorded if any of its three projectiles spawn; failed individual projectiles do not prevent the remaining fan shots from being attempted.
- If all projectile spawns fail, the interaction is not canceled, allowing the usual entity interaction to continue.

This is a source-level reliability fix only. Java compilation and in-game behavior remain unverified because the Gradle distribution cannot currently be downloaded.
