# Gojo low-health crouch-triggered Purple — r57

## Change
- The Gojo NPC emergency Hollow Purple branch now requires `gojo.isCrouching()` in addition to health at or below 20% and the independent 40-tick repeat cooldown.
- The one-shot 200% Purple encounter against `SukunaFushiguroEntity` remains prioritized and does not require crouching.
- The emergency cooldown is committed only after the projectile successfully enters the level.

## Scope and limitations
- This applies to the existing custom Gojo NPC prototype behavior in `PrototypeCommands`, not to the native JujutsuCraft Gojo skill dispatcher.
- Crouching/pose behavior depends on the target entity actually entering the crouching pose; this needs an in-game test.
- Static audits do not replace Java compilation or Minecraft testing.
