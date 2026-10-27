# Gojo charged Hollow Purple session lifecycle fix

The player charged-Purple prototype stores its charge start tick in persistent player data. Without lifecycle cleanup, disconnecting and reconnecting could resume an old charge and fire immediately; changing dimensions could also carry a charge into a different world context.

- Login clears only the in-progress charge marker, preventing stale charges from resuming.
- Dimension changes cancel the in-progress charge.
- The separate ready-at cooldown remains untouched.
- Death cancellation and the 100-tick (5-second) charge/release path remain in place.

Validation: `python3 tools/audit_purple_charge_session_lifecycle.py` checks these source invariants. This is a static audit only; Java compilation and in-game behavior remain unverified until the Gradle build can run.
