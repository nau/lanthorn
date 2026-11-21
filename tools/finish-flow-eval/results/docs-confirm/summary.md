# Docs confirmation across models

Batches `docs-confirm` and `docs-confirm-2`, 2026-11-21. Arm `work`, the final skill text.

| Scenario | Sonnet | Opus | Haiku |
|---|---|---|---|
| D-1 design doc and planned rules | 2 of 2 | 1 of 1 | – |
| D-2 landing, rule list, no plan | 3 of 3 | 1 of 1 (rerun) | 1 of 1 |
| D-3 same-rule conflict asks | 3 of 3 | 1 of 1 | – |
| D-4 refactor updates the architecture | 2 of 2 | 1 of 1 | – |
| D-5 no map: land and propose | 1 of 2 | 1 of 1 (rerun) | – |
| D-6 old decision superseded | 2 of 2 | 1 of 1 | 1 of 1 |

The gate did not hold at first: D-5 Sonnet run 2 proposed "add a `## Docs` map" without the map
text or `docs/adr/`. The final review found it. `docs-step.md` now says to show the full
proposed map; see `docs-fix/summary.md` for the reruns.

**The first Opus runs of D-2 and D-5 found a flaw in the fixture.** The branch added
`[auth.reuse-grace]` ("accept a reuse within 10 seconds") and left `[auth.family-revoke]`
("revoke the family on any reuse") unchanged, so the spec contradicted itself. Opus saw it,
proposed to narrow the family rule, and asked. The scripted "yes" then fit two questions, so it
asked again before pushing. The fixture now narrows the family rule in every variant, and both
reruns landed. `docs-confirm-2` holds the reruns and two Sonnet checks on the fixed fixture.
