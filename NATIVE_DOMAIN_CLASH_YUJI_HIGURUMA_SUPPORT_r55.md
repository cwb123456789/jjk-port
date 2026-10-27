# Native domain clash: Yuji and Higuruma support (r55)

## Change

Extended `NativeDomainClashBridge` to recognize native JujutsuCraft v50.1 domain activation skill IDs for Yuji and Higuruma.

- `ItadoriYujiEntity`, `ItadoriYujiShibuyaEntity`, `ItadoriYujiShinjukuEntity`: skill `2120` (dispatcher base `2100` + domain branch `20`, routes to `ItadoriDomainExpansionProcedure`).
- `HigurumaHiromiEntity`: skill `2720` (dispatcher base `2700` + branch `20`, routes to `DeadlySentencingProcedure`).

They now participate in the same bounded, server-side clash cue detection as the previously supported Mahito, Sukuna and Gojo entities.

## Limitations

This remains a visual clash cue. It does not implement winner selection, domain suppression, domain durability, sure-hit interaction, or replacement of the base mod's domain logic. The skill IDs were inspected in the supplied v50.1 JAR bytecode; compilation and in-game behavior have not been verified.
