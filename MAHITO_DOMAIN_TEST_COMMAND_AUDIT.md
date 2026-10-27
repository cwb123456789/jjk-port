# Mahito Domain Visual Test Command Audit

This checkpoint adds a static audit for the three registered domain visual test commands:

- `/jujutsuenhancement mahito_domain_visual_test`
- `/jujutsuenhancement mahito_domain_hands_test`
- `/jujutsuenhancement mahito_domain_barrier_test`

The audit verifies command-to-handler wiring, partial spawn failure feedback, and the hands/barrier entities' 60-tick server-side expiry. It also checks that these visual-only entities do not implement direct damage logic.

Run from the project root:

```sh
python3 tools/audit_mahito_domain_test_commands.py
```

This is a static source audit only. It does not replace Java compilation or an in-game rendering test. Gradle 8.8 remains unavailable in this environment because the wrapper cannot resolve `services.gradle.org`.
