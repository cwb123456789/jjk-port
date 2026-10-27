# Mahito stage-2 transformation prototype checkpoint (2026-10-09)

- Converted the `animation.mahito.stage2` clip from the user-provided Bedrock resource reference into GeckoLib-style keyframes.
- Deliberately retained only `animation.mahito.stage2` plus an empty looping idle clip in the runtime animation JSON. The full Bedrock animation library references many bones absent from the imported 16-bone Mahito geometry and contains non-finite converted vectors in unrelated clips; binding that entire file would be unsafe.
- The separate `/summon jujutsuenhancement:mahito_prototype` entity now starts the stage-2 animation when right-clicked with the main hand. The server synchronizes the trigger and clears it after 111 ticks, after which the controller returns to idle.
- Static resource validation passes: all 6 stage-2 animated bones (`root`, `head`, arms, legs) exist in `mahito.geo.json`; no malformed vectors or missing animation bones remain in the runtime file.
- Not implemented: changing the original JujutsuCraft Mahito entity, transformation skin/model swap, transformation effects/particles, or stage-2 abilities.
- Java build/game test remains blocked because Gradle 8.8 cannot be downloaded (`UnknownHostException: services.gradle.org`). Runtime API compatibility is therefore unverified.

## Follow-up stage-2 ability pass (2026-10-09)

- Changed the stage-2 animation clip to `loop: false` so it is authored as a one-shot transformation rather than an endlessly looping clip.
- After transformation, the prototype now emits low-frequency soul/portal ambient particles on the server.
- Right-clicking the transformed prototype triggers a server-side short-range soul-strike prototype: living targets within a 4-block horizontal radius (2 vertical) that are not the interacting player or allied to that player take 8 magic damage; hit and swing particles/sound are emitted. Cooldown: 30 ticks.
- Transformation state, timer, cooldown, and initiating player's UUID are saved in entity NBT. The saved owner UUID is currently informational; no owner-follow AI or original JujutsuCraft entity replacement has been added.
- Static resource validation passes. Java compilation and in-game behavior remain unverified because the Gradle distribution cannot be fetched in this environment.
