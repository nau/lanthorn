# Docs GREEN: the docs system on Sonnet

Batch `docs-green`, 2026-11-21. Arm `work`, Sonnet. All runs read by hand.

| Scenario | Runs | Pass | By reading |
|---|---|---|---|
| D-1 | 3 | 3 | Each run loaded `keeping-project-docs` first, then `spec-writing`. Design doc with a block, planned rules in `auth.md`. |
| D-2 | 2 | 2 | The plan never reached the landed history. The summary has "Rule changes", "Planned rules" and "Docs" in both. |
| D-3 | 2 | 2 | Both stopped on `[auth.family-revoke]`, quoted both texts, proposed a wording, and asked. Neither landed. |
| D-4 | 2 | 2 | `ARCHITECTURE.md` changed in the landing: the code map names `src/persistence/store.sh`. |
| D-5 | 2 | 2 | Both landed with no docs step, wrote no map, and proposed a map that keeps `docs/adr/`. The plan landed, as the no-map rule allows. |
| D-6 | 2 | 2 | The old block says `Superseded by`, and nothing else in that doc changed. |
| D-2 with `docs-append` | 2 | 1 | Both kept both rules with no question about the conflict. Run 2 pushed nothing, see below, so by the keys it fails. |
| S-2, S-3, R-3 | 1 each | 3 | The finish flow still lands, retries, and keeps everything on a no. |

**Append run 2.** The agent found that the fixture's `within_grace` was a helper that nothing
called, so `[auth.reuse-grace]` was not built end to end. It restored the planned note, as
`[docs.spec.planned]` asks, and offered three options. The scripted "yes" then read as an
answer to that question, so it asked once more before pushing. This was a fixture flaw, not a
skill failure: the fixture now wires the helper into `on_reuse`.

Against the baseline: D-1 added no planned rule there, D-2 landed the plan, D-3 merged both
texts without asking, D-4 left `ARCHITECTURE.md` stale, D-5 proposed no map, and D-6 left the
old decision Accepted.
