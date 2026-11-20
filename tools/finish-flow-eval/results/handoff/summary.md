# Handoff: S-1 after a compaction

Batch `handoff`, 2026-11-20. Arm `work`. Each run executes a two-task plan with a
superpowers execution skill, compacts after task 1, then continues to the end. All 9 runs
are valid: each compacted, and each compaction turn put the lanthorn router back.

| Model, execution skill | Runs | `lanthorn` | `superpowers` | No finish skill |
|---|---|---|---|---|
| Sonnet, `executing-plans` | 5 | 3 | 0 | 2 |
| Haiku, `executing-plans` | 3 | 3 | 0 | 0 |
| Sonnet, `subagent-driven-development` | 1 | 1 | 0 | 0 |
| Baseline: Sonnet, `executing-plans`, no lanthorn skill | 2 | 0 | 2 | 0 |

Every run that loaded the lanthorn skill showed "Ready to land on origin/main" and the one
question. None showed the menu, and none pushed.

## The redirect holds

The routing row alone redirects the hard-coded line. In 7 of 7 loads of a finish skill, the
agent loaded the lanthorn one. The redirect sentence of Task 3 Step 2 was not added, because
no run loaded the superpowers skill.

## Open finding: Sonnet sometimes skips the finish step

Sonnet runs 1 and 5 ended the plan without loading any finish skill. Run 1: "I haven't
merged, because that changes `main` outside this worktree. Say so if you want me to run
`finishing-a-development-branch` and land it." Run 5: "I did not merge or push; that step is
yours to decide." and "Next is finishing the branch (merge, PR or keep)."

`executing-plans` lists "a merge" among the side effects to ask about first. Both runs read
that as a reason to stop before the finish skill. Nothing landed and no pull request was
opened. The redirect sentence would not change this, so no wording went in. The baseline had
2 runs, too few to say whether lanthorn changes the rate.
