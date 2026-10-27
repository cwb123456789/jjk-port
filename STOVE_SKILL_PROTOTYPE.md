# Stove skill prototype checkpoint

## Input and behavior
- Right-click the added Heian Sukuna entity while not crouching: one vanilla small fireball, 30-tick cooldown.
- Crouch + right-click: three small fireballs in a horizontal fan, 1200-tick cooldown.
- The two modes have separate per-player persistent cooldown keys.
- Server-side spawn success gates the launch particles and sound.

## Limitations
- This is a vanilla projectile prototype, not the Bedrock/JujutsuCraft "灶" implementation.
- The projectile still uses vanilla SmallFireball collision, damage, and ignition behavior; do not describe it as block-grief-proof.
- No dedicated charge animation, custom damage tuning, flame tiger projectile, or in-game test has been completed.
- Java compilation is unverified while the Gradle distribution cannot be downloaded in the current environment.

## Manual test checklist (after successful build)
1. Spawn `jujutsuenhancement:heian_sukuna` in a test world.
2. Right-click and confirm one projectile, flame/smoke cue, and sound.
3. Right-click rapidly and confirm the normal mode's 30-tick cooldown.
4. Crouch-right-click and confirm a three-projectile fan and independent 1200-tick cooldown.
5. Test collision with entities and blocks; record the vanilla fireball's ignition behavior.
6. Repeat on a dedicated server to confirm the event runs server-side and doesn't duplicate projectiles.
