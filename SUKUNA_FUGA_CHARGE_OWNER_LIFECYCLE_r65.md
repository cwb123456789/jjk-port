# r65 — Sukuna Fuga charge owner lifecycle

## Change
The custom Fuga charge and its ready-to-release window are owned by the initiating player. On the server tick, while either state is active, the entity checks whether that player is still online, alive, and in the same level. A missing/disconnected player, a dead player, or a dimension change cancels the transient charge and clears the charger UUID.

## Why
Previously, the charge was bounded by its normal charge/ready timers, but it could remain active briefly after its initiating player disconnected, died, or changed dimensions. This could leave the custom Sukuna entity visually locked in a charge/ready animation until the timeout elapsed.

## Scope and limits
- The 55-tick charge duration and 100-tick ready window are unchanged.
- This does not implement JujutsuCraft's native `Open` skill dispatcher; it applies only to the custom Heian Sukuna prototype.
- Static source audits do not substitute for Gradle compilation or an in-game test.
