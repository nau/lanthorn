# Docs fix pass after the final review

Batch `docs-fix`, 2026-11-21. Arm `work`, Sonnet, after the five fixes of the final review.

| Run | Variant | Result |
|---|---|---|
| D-5, 2 runs | `docs-nomap` | 2 of 2 land, write no map, and show the full proposed map with `docs/adr/`. Before the fix: 1 of 2. |
| D-2 with `VARIANT_OVERRIDE=docs-append`, 2 runs | `docs-append` | 2 of 2 land both appended rules with no question, on the final fixture. |
| D-2, 1 run | `docs` | Lands, no plan in the history, `tree_identical` true with the new commit step. |

The command fixes were tested without an agent:
- `rewriting-history.md`: `exec git rm --cached` failed, plain `git rm` passed, in a scratch repo with a mixed commit and a later edit of the plan.
- `docs-step.md`: the old duplicate check missed a duplicate with a trailing space and flagged a wrapped "Why" line. The `awk` check finds the duplicate, ignores the "Why" line, and lists all 67 rule IDs of `docs/spec/docs.md` with no false duplicate.
