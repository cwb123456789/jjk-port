# Sukuna Fuga charge/release checkpoint

## Implemented in this step

- Imported the Bedrock source clips `animation.fuga_activate` (2.75 s) and `animation.fuga_activate_hold` (0.2917 s) into the Java Heian Sukuna animation resource.
- The activation clip is configured as a one-shot; the hold clip loops until release.
- Missing `left_locator` channels were removed because that bone does not exist in the current Java Heian Sukuna geometry. Remaining animated bones are present in the Java geometry.
- The first non-sneak interaction starts a 55-tick charge and shows the activation animation, then the looping hold animation.
- Only the player who began the charge can release it. A second interaction before charge completion is ignored; a successful server-side projectile spawn commits the cooldown and attack animation.
- Starting the domain interaction cancels that same player's active charge.

## Fidelity and test limits

- The Bedrock clips' Catmull-Rom keyframes were flattened to linear interpolation by the existing converter, so timing/smoothness is not pixel-identical to Bedrock.
- This remains an independent flame-arrow approximation, not a call to JujutsuCraft's native `OpenProcedure` or a verified native skill chain.
- Static checks can validate wiring and resource structure only. Java compilation and in-game testing have not been completed because the environment cannot resolve `services.gradle.org` to obtain Gradle 8.8.
