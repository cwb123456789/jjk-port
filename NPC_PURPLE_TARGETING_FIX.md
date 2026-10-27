# NPC 200% Hollow Purple targeting fix

- Added explicit imports for vanilla particle and sound symbols used by the prototype command handler.
- On charge start, the Gojo NPC records the UUID of the nearest `SukunaFushiguroEntity` in its 20-block search area.
- After the 100-tick charge, the projectile direction aims at that target's bounding-box center when the target is still alive and within 40 blocks; otherwise it falls back to the NPC look direction.
- The one-shot flag remains set only after the projectile is successfully added to the level.

This change is source-level only. It has not been Java-compiled or verified in a running Minecraft instance; exact JujutsuCraft runtime entity class names still need in-game confirmation.
