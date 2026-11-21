# ID lifecycle

Read this before you rename, remove, or add a rule in a spec that already has
IDs.

**An ID is frozen from the moment it is published.** An ID is *published* when
it exists on the main branch, in a released version of the spec, or in a copy
that someone outside the team holds. Until then it is a *draft* ID. The state
belongs to each ID, not to the document. A branch that adds two rules to a
published spec has two draft IDs next to the published ones.

| Change | Draft ID | Published ID |
|---|---|---|
| Rename | Yes. Update each citation in the same change. | Never. |
| Remove the rule | Delete it. Leave no trace. | Mark it withdrawn. Keep its ID. |
| Move the rule to another file | Keep the ID. | Keep the ID. |
| Add a rule | Give it a new name in its area. | – |

- A withdrawn ID is never reused, not even for a similar rule.
- Before you add a name, search for it: `rg -nF '[auth.reuse-grace]' .`. A
  name that exists, withdrawn or not, is taken.
- When you rename a draft ID, search the plan, the code, and the tests for the
  old ID.
- To find the state of an ID, compare the spec with its version on the main
  branch: `git diff main -- <spec file>`. An ID that the main branch already
  holds is published.
- If you cannot find the state of an ID, treat it as published.

**Numbered IDs.** A spec written before word IDs, with IDs such as `[TR-3]`,
keeps them. They are published, so they never change. Each new rule in that
spec takes a word ID.

**Why:** An auditor traces a finding to a rule. An implementer traces a rule to
code. A renamed published ID breaks both links silently, and no test catches
it. A draft ID has no reader outside the branch, so it may change freely.
