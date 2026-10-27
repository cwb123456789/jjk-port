# Sukuna flame-arrow approximation implementation

## Runtime change

The Heian Sukuna interaction no longer emits three vanilla `SmallFireball` projectiles. It creates one registered `SukunaFlameArrowEntity` instead. The projectile has server-authoritative block/entity collision, applies 28 magic damage on its first entity hit, ignites living targets for four seconds, emits flame/soul-fire/smoke particles, and discards on impact or after 80 ticks. Its owner UUID is saved to NBT so owner filtering survives serialization.

## Important scope limit

This is an independent gameplay approximation, not JujutsuCraft's native `OpenProcedure` / `FlameArrowEntity` chain and not a claim of exact Bedrock parity. The custom Heian Sukuna remains a base `Entity`; the native AI compatibility problem is intentionally not bypassed by setting a guessed skill ID.

## Validation

The source files and entity registration were checked statically. A Java compile and in-game test are still required before treating this as runtime-verified.
