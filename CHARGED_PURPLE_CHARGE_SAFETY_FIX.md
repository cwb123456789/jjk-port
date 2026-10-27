# Charged Purple charge-state safety fix

## Changes
- Store a minimum positive charge-start tick so a charge initiated at world game-time zero is not mistaken for an inactive charge.
- Cancel a pending player 200% Purple charge if the player dies before the five-second charge completes. The projectile is no longer launched by the dead player tick path.

## Validation
- Static source assertions confirm the positive start-tick sentinel and death-cancellation branch are present.
- Resource and renderer audits should be rerun after packaging.
- Java compilation and in-game behavior remain unverified because the Gradle wrapper distribution cannot be downloaded in this environment (`UnknownHostException: services.gradle.org`).
