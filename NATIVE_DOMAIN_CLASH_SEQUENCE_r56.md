# Native domain clash sequence — r56

## Change
- The native domain bridge now tracks each overlapping pair with a small state object.
- A new overlap emits the existing activation cue once.
- While both recognized domain skills remain active, a short 40-tick visual sequence emits throttled electric-spark/portal pulses every 5 ticks.
- The pair cache is still cleaned after 200 ticks without observation.
- Existing recognized native skill IDs and server-side-only execution are unchanged.

## Scope and limitations
This is a staged visual clash, not domain winner resolution. It does not cancel native domains, apply domain suppression, alter damage, or determine a winner. Those behaviors require verified knowledge of JujutsuCraft's domain lifecycle and in-game testing.
