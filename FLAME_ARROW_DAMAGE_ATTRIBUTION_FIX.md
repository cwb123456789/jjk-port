# Flame Arrow Damage Attribution Fix

The custom Sukuna flame-arrow approximation now creates an indirect-magic damage source with the projectile as the direct source and its resolved owner as the attacker. If the owner is no longer loaded, the projectile itself is used as a fallback attacker so the collision still resolves without a null reference.

This is a source-level change only. The audit checks that the attribution path is present, but Java compilation and an in-game damage/death-message test remain outstanding because the Gradle 8.8 distribution cannot be downloaded in the current environment (`services.gradle.org` DNS lookup fails).
