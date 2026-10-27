# Hollow Purple trail checkpoint

- Reworked server-side projectile particles to form a segmented trail between the previous and next projectile positions instead of emitting particles only at the projectile center.
- The 200% variant (80 damage configuration) uses more trail segments, larger spread, and denser purple/blue core particles than the normal prototype.
- Impact burst remains layered with purple, blue, white, and vanilla explosion/portal particles.
- Resource validation passes, including geometry/UV/animation reference checks.
- This is still a Forge-side approximation of the Bedrock pack's `hollow_purple` and `200hollow_purple` effects; the Bedrock particle JSON and its custom textures are not executed natively. Java compilation and in-game rendering remain unverified because Gradle could not be downloaded in the environment.
