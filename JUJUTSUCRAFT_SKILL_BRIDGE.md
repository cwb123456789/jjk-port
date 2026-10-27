# Sukuna fire-skill prototype checkpoint

- Right-clicking the Heian Sukuna entity fires one vanilla `SmallFireball` from the player's eye position.
- Sneak-right-clicking fires a three-projectile fan with a 8-degree spread.
- Both inputs run on the logical server and use per-player persistent-data cooldowns (30 ticks / 1200 ticks (normal attack / domain)).
- The fan uses vanilla small fireballs, not explosive large fireballs, to avoid adding block griefing.
- Important: this is a functional combat-input prototype only. It does **not** reproduce Bedrock Jujutsu Awakening's "灶" visuals, charge-up, damage tuning, or animations. The earlier attempt to call `AIFlameArrowProcedure` with a player was removed because bytecode inspection shows the AI procedure expects JujutsuCraft AI entity state (`NameRanged_ranged`, owner data); passing a player does not constitute a reliable skill activation.
- Build/game verification is still blocked by unavailable Gradle 8.8 distribution (`services.gradle.org` DNS failure). Compile/API compatibility and in-game behavior remain unverified.
