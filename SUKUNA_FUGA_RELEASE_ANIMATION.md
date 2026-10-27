# Sukuna Fuga release animation checkpoint

- Added `animation.fuga_release` by adapting the 5.0–6.5 second segment of the Bedrock `animation.fuga_arrow1` source clip.
- Kept only channels targeting bones present in the Java `heian_sukuna` geometry. Omitted the source root-position channel because it only had an end-boundary key in this slice and would cause a snap.
- Added synchronized state `8`, played for 30 ticks, and mapped it to `animation.fuga_release`.
- The successful projectile-spawn branch now commits the Fuga release state and cooldown; a rejected spawn leaves the release ready to retry.

## Limits

The source clip is a long Bedrock sequence and this is a trimmed approximation. Catmull-Rom keyframes are represented as linear GeckoLib keyframes; visual equivalence is not claimed. Java compilation and in-game animation checks remain unverified.
