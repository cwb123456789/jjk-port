# Heian Sukuna attack-animation trigger checkpoint

## Implemented

- Added synchronized integer animation state to `HeianSukunaEntity`.
- Added a server-side 32-tick state window, then returns the controller to idle.
- Connected normal interaction with the existing temporary fireball prototype to cycle through the five imported Bedrock-derived attack clips (`attack1` through `attack5`).
- Added a static audit to ensure the referenced animation IDs exist and the server-side trigger path is present.

## Scope and limitations

- This only connects animation playback to the current prototype interaction. It does **not** make the small-fireball spread equivalent to the Bedrock "灶" skill.
- Sneak interaction still creates the current domain visual prototype; the full Bedrock domain animation/skill sequence is not yet connected.
- The 32-tick window is a provisional state reset, not a claim that each clip's authored duration is 32 ticks.
- Gradle compilation and in-game playback remain unverified in this environment.
