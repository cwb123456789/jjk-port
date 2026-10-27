# Mahito raw animation quarantine

The original Bedrock animation file is preserved at `docs/source_assets/mahito.animation.bedrock-source.json` rather than shipped as a runtime GeckoLib asset.

Reason: the raw file contains non-finite rotation keyframes and tracks for many bones absent from the current 16-bone Mahito geometry. Shipping it beside the converted animation causes resource audits to fail and risks accidental binding by future code. The renderer continues to reference `animations/mahito_compatible.animation.json`.

This is not a claim that the full Bedrock animation set has been ported. The compatible runtime file currently retains only tracks that can be represented by the available geometry. Full fidelity requires restoring the missing geometry/bones and converting the corresponding tracks.
