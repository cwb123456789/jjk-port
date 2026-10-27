# Mahito domain visual test command

Added the server command:

```text
/jujutsuenhancement mahito_domain_visual_test
```

It spawns the short-lived hand-sign and cube-panel barrier entities three blocks in front of the invoking player. Both are visual-only and expire after approximately 60 server ticks (3 seconds). This command does not apply damage and does not require triggering Mahito's transformation.

Use this to isolate the model/render chain from the transformation flow. If either effect is missing, capture `latest.log` immediately after running the command and check the entity registration, renderer registration, GeckoLib geometry parsing, and texture loading errors.

Validation completed: resource JSON validation, geometry compatibility audit, renderer resource audit, and server-side import audit passed. The two isolated commands are registered in source; Java compilation and in-game rendering remain unverified because the Gradle 8.8 distribution could not be downloaded in the current network environment.


## Isolated visual tests

Use these if the combined test shows only one of its two effects:

- `/jujutsuenhancement mahito_domain_hands_test` spawns only the hand-sign entity.
- `/jujutsuenhancement mahito_domain_barrier_test` spawns only the approximate barrier entity.

Both effects are visual-only, spawn three blocks in the look direction, and expire after about three seconds. If an isolated test fails, the corresponding renderer/model/texture chain can be investigated without ambiguity from the other effect.
