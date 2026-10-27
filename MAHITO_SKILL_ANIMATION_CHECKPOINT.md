# Mahito skill animation hook checkpoint (2026-10-10)

- Added synchronized skill-animation state to the Mahito prototype.
- Right-click Soul Strike now requests `animation.mahito.body_ab1` for 20 ticks.
- Crouch + right-click Idle Transfiguration burst requests `animation.mahito.2s_domain` for 60 ticks.
- Skill animation state and remaining duration are saved to entity NBT.
- These are animation hooks attached to the existing prototype skill effects, not proof of exact frame-perfect parity with Bedrock. Java compilation and in-game tests remain unverified.
