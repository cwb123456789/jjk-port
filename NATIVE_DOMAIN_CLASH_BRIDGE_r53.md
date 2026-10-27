# Native Domain Clash Bridge — r53

## Implemented
- Detects simultaneous native JujutsuCraft v50.1 domain skill states for the verified classes/IDs currently available in the project: Mahito `1520`, Sukuna variants `120`, and Gojo Satoru `220`.
- When two such entities are alive within 32 blocks, emits a portal/end-rod clash cue and a sound at their midpoint.
- De-duplicates each entity pair and rate-limits the cue to once per 60 ticks per pair.
- Runs on the server and leaves the original `skill` state and JujutsuCraft procedures untouched.

## Deliberate limitations
- This is the first clash-detection/visual cue step, not a complete domain contest system. It does not determine a winner, suppress either domain, cancel damage, or implement Yuji/Higuruma domain IDs.
- The bridge only recognizes the exact native entity classes listed in source. More classes/IDs must be verified from the target JAR before adding them.
- Static audits do not prove actual event timing or in-game particle/audio behavior. Gradle compilation and Minecraft testing remain required.
