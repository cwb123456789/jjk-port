# Mahito domain visual lifetime authority fix

The domain-opening animation is the authoritative 60-tick lifetime boundary for its hand-sign and barrier visuals. Newly spawned entities can be processed in a different server tick order depending on when they enter the level's entity tick list. Their own 60-tick expiry remains a failsafe, but the prototype now explicitly discards either tracked visual that is still loaded when the opening timer ends, then clears the saved UUID references.

Cleanup is still restricted to `MahitoDomainHandsEntity` and `MahitoDomainBarrierEntity`. The change does not alter chunk-unload handling, damage, the opening timer, or visual geometry.

Validation: `tools/audit_mahito_domain_lifetime_authority.py` checks the timer boundary, both cleanup paths, UUID reset, per-entity failsafes, and type restriction. This is a source-level audit; it is not a Java compile or an in-game test.
