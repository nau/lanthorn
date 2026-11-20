# ID lifecycle

Read this before you renumber, remove, or add a rule in a spec that already
has IDs.

**An ID is frozen from the moment it is published.** An ID is *published* when
it exists on the main branch, in a released version of the spec, or in a copy
that someone outside the team holds. Until then it is a *draft* ID. The state
belongs to each ID, not to the document. A branch that adds two rules to a
published spec has two draft IDs next to the published ones.

| Change | Draft ID | Published ID |
|---|---|---|
| Renumber or reorder | Yes. Keep the numbers in reading order. | Never. |
| Remove the rule | Delete it. Leave no trace. | Mark it withdrawn. Keep its number. |
| Add a rule | Renumber the draft IDs, so that they stay in reading order. | Give the new rule the next free number. |

- Draft IDs take the numbers after the highest published number of the series.
  A withdrawn number counts as published.
- When you renumber a draft ID, update each citation in the same change. Search
  the plan, the code, and the tests for the old ID.
- To find the state of an ID, compare the spec with its version on the main
  branch: `git diff main -- <spec file>`. An ID that the main branch already
  holds is published.
- If you cannot find the state of an ID, treat it as published.

**Why:** An auditor traces a finding to a rule. An implementer traces a rule to
code. A renumbered published ID breaks both links silently, and no test catches
it. A draft ID has no reader outside the branch. A gap or a "withdrawn" marker
there records a history that nobody needs.
