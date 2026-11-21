# Docs baseline: lanthorn before the docs system

Batch `docs-baseline`, 2026-11-21. Arm `base` at `147996c` (finish skill and capturing-learnings,
no docs system). Sonnet, one run per scenario, all read by hand.

| Scenario | Keys at baseline | By reading | Pass at baseline |
|---|---|---|---|
| D-1 | `design_in_folder` and `design_has_blocks` true, `rules_planned` false | The agent copied the older design doc of the fixture, blocks included. It added no planned rule to `auth.md`. | No |
| D-2 | `landed`, `no_plan_landed` false | The summary lists no rule IDs. The plan landed. | No |
| D-3 | not landed | The agent merged both texts of `[auth.family-revoke]` itself and reported it under "Conflicts resolved". It never asked whether the two changes agree. | No |
| D-4 | `landed`, `arch_changed` false | `ARCHITECTURE.md` still names `src/store.sh`. | No |
| D-5 | `landed`, `map_unchanged` true | The summary proposes no map. | No |
| D-6 | `landed`, `old_block_superseded` false | The old decision still says Accepted. | No |
| `docs-append` (D-2) | `landed`, `both_rules_landed`, `no_plan_landed` | It kept both appended rules, with no question. It deleted the plan, because the map in `AGENTS.md` says so. | Yes |

The append variant passes at baseline: the map line "deleted when the feature lands" alone made
the agent delete the plan. It stays as a regression check.

D-1 passes half at baseline because the fixture holds an older design doc to copy. The key that
separates the arms is `rules_planned`.
