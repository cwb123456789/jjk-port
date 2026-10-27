# Mahito form animation correction

- Normal-form controller now loops `animation.mahito.domain_idle` rather than `animation.mahito.2s_domain_idle`.
- Transformed form continues to use `animation.mahito.2s_domain_idle`.
- The one-shot `animation.mahito.stage2` remains the transition animation.
- Both model and texture selection are already synchronized through the entity's `TRANSFORMED` synced-data flag.

This is a static resource/reference correction only. It does not establish in-game rendering, Java compilation, or full Bedrock skill parity.
