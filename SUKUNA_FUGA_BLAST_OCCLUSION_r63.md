# Sukuna Fuga blast line-of-sight fix (r63)

The custom Fuga projectile's server-side splash damage now checks line of sight from the impact point to each candidate's bounding-box center. A candidate is skipped when a solid block is intersected before the target, preventing splash damage from passing through walls. The direct-hit target remains excluded from splash damage, and existing owner/alliance filters are unchanged.

Validation: `tools/audit_sukuna_fuga_blast_occlusion_r63.py` checks the source ordering and required predicates. This is a static source audit, not a Java compilation or in-game test. The projectile remains a custom approximation, not the native JujutsuCraft skill.
