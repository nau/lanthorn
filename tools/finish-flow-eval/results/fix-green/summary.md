# Fix pass after the final review

Batch `fix-red` and `fix-green`, 2026-11-20. Arm `work`. All runs read by hand.

## Changes to the skill

| Review finding | Change | Evidence before | Evidence after |
|---|---|---|---|
| 2. The fast check is lost when the rewrite is skipped or after a later rebase | One "Fast check" paragraph in step 5: every pass, also when the rewrite is skipped | 4 of 6 S-3 retries ran no fast check. R-6 run 1 of `fix-red` ran none in turn 1. | S-3: 3 of 3 ran it on the retry (`fast_runs_by_turn` `[4, 4]`). R-6: 2 of 2 ran it in turn 1. |
| 3. `writing-commits` adds an `AGENTS.md` commit during the landing | Use `writing-commits` for the wording only. Propose, do not commit. A "Proposals" line in the summary. | 6 of about 31 rewrites. Opus 2 of 2 in `confirm`. | 0 of 6 S-2 runs (4 Sonnet, 2 Opus). Each named the idea under "Proposals". |
| 5. A guessed base branch | Ask before the rebase when more than one branch could be the base. Mark a guess in the summary. | The ruling assumed that the backup ref undoes the rebase. It does not. | Wording only. No scenario has two candidate bases. |

## Finding 1 did not reproduce

R-6: the second teammate commit, pushed after the question, renames `now_ts`, which the feature
calls. With the text before the fix pass, 3 of 3 runs found the break, adjusted, ran the suite,
asked again, and landed a passing tree (`landed_suite_passes` true). So no wording went in for
finding 1. R-6 stays as a regression scenario. With the new text, 2 of 2 pass too.

## Results

| Scenario | Runs | Pass |
|---|---|---|
| R-6, before the fix pass | 3 Sonnet | 3 |
| R-6 | 2 Sonnet | 2 |
| S-3 | 3 Sonnet | 3 |
| S-2 | 4 Sonnet, 2 Opus | 6 |

Every key of `result.json` is true in all 11 runs after the fix pass, including the new ones:
`local_base_unchanged_before_yes`, `landed_suite_passes`, and `turn_exits_ok`.

## Observed, not a finish-flow defect

R-6 run 1 added a `Co-Authored-By` trailer. It loaded `lanthorn:writing-commits`, then
followed the attribution instruction of the harness system prompt. The fixture has no
`.claude/settings.json` with `"attribution": false`, which removes that instruction.
