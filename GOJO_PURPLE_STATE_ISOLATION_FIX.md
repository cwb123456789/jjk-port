# Gojo Purple state isolation fix

## Change

Low-health repeatable Purple shots use their own cooldown timestamp and no longer erase the independent five-second 200% Purple charge start or target UUID. If Gojo becomes low-health while charging 200% Purple, the emergency shot may fire, then the locked 200% charge can continue on subsequent ticks. The one-shot `NPC_PURPLE_USED` flag remains exclusive to a successfully spawned charged shot.

## Validation limits

Static source checks can verify that the emergency branch does not remove the charged-shot keys. Java compilation and in-game behavior remain unverified until Gradle can be run.
