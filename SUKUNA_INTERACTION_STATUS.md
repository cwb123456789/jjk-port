# Heian Sukuna interaction status

## Verified statically

- Entity interaction handling is restricted to the main hand and a server-side `ServerPlayer`.
- The five-step animation index and the single-attack cooldown advance only after at least one projectile is accepted by the server.
- Attack animation selection is published through `SynchedEntityData`.
- The interaction currently spawns vanilla `SmallFireball` entities. This remains a gameplay placeholder, **not** the native JujutsuCraft `灶` / Stove skill and not a faithful Bedrock port.
- Sneak interaction creates the current `DomainShrineEntity` prototype. Its radius damage and particles are custom approximations, not the full original domain implementation.

## Validation limits

`tools/audit_sukuna_interaction_server_flow.py` checks source-level invariants only. No Gradle build, Forge launch, multiplayer synchronization test, or in-game animation test has been completed in this environment. Native skill dispatch IDs alone do not establish that the native procedure can safely be called on `HeianSukunaEntity`; its class hierarchy and native state requirements still need resolution.
