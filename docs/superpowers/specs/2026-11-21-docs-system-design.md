---
area: docs
---

# Docs system: architecture, living spec, and decisions in the repository

## Overview

*Informative.* A project keeps three kinds of knowledge in its repository. The
architecture document is the short entry point: the components, how they
interact, and the few rules that the code does not show. The living spec holds
the current rules of each area, one file per area. The design docs hold the
history: one dated file per feature, with its decisions in the form of an ADR.

A feature branch changes the living spec directly. The designer edits the area
file next to the code, so the git diff is the record of what changed, and two
branches that change one rule meet as a git conflict. The designer writes each new rule into the area file at design time, marked
planned, and the build removes the mark. At landing, the finish flow checks the rule IDs,
lists the changed rules in its summary, updates the architecture document when a
component changed, and deletes the plan.

A `## Docs` section of the project's instruction file is the map. It names where
each kind of document lives. Every skill reads the same map, so a project with
its own layout keeps it.

## Goals and non-goals

*Informative.*

**Problem.** A design doc goes stale when the next feature changes its area, and
nothing marks it stale. Large documents also cost an agent context on every load.

**Goals**

- An agent learns the current rules of an area from one file.
- An agent finds why a rule exists in one step.
- The docs change in the same landing as the code.
- Parallel branches never collide on a rule ID.
- A human sees the architecture as a diagram.
- No hand-kept index, and no file that grows without bound.
- It works for any project and any language, and yields to a project's layout.

**Non-goals**

- A doc-gardening agent, a separate decision log, or one ADR file per decision.
- A record of each ruling that an agent makes during the build.
- Generated documents, such as API references.
- A change to any superpowers skill.
- A migration of a project's old docs into area files.

**Anti-goals.** Each of these makes the system a failure, even if it runs:

- A published rule ID changes or is reused.
- The living spec states a rule that no landed code builds, with no note.
- The skill moves a project's existing docs without a yes.
- `ARCHITECTURE.md` changes on a landing that changed no component and recorded
  no constraint.

## Approaches

*Informative.* The human partner picked each of these.

### Decision: Edit the living spec on the feature branch

Status: Accepted
Area: docs
Context: A review found that copying marked rules into the spec at landing loses concurrent updates, skips small fixes, and duplicates git.
Options:
- Edit the area file on the branch: the git diff is the delta, and a same-rule change conflicts.
- Mark rules in the design doc, and copy them at landing: the design doc stands alone, but each rule exists twice.
- Adopt OpenSpec: a maintained tool with delta specs. It brings a Node dependency and its own layout.
Chosen: Edit on the branch, because git already tracks changes and conflicts.
Consequences: A design doc links its rules instead of holding them.

### Decision: Name rule IDs with lowercase dotted words

Status: Accepted
Area: docs
Context: Parallel branches that number rules pick the same next number, and `[TD-13]` says nothing to a reader.
Options:
- Numbers, `[AUTH-5]`: short and ordered. Parallel branches collide, and the second must renumber.
- Dotted words, `[auth.reuse-grace]`: never collide in practice, readable. Longer, and each rule needs a name.
Chosen: Dotted words, after the Rust Reference, because the team runs parallel branches by design.
Consequences: The ID rules of `spec-writing` change for every project that uses lanthorn.

### Decision: Keep decisions as blocks in design docs

Status: Accepted
Area: docs
Context: A design doc holds several decisions, and an agent searches with grep.
Options:
- A log file: one table for humans. Parallel landings collide on it.
- One ADR file per decision: readable alone. A second copy of the design doc.
- Blocks inside the design doc: one copy, found by a grep for the heading.
Chosen: Blocks, because the living spec and the architecture document hold the current truth.
Consequences: The status line of a replaced decision is the one edit to a landed design doc.

### Decision: Delete each plan at landing

Status: Accepted
Area: docs
Context: After landing, the design doc holds the why, and the code holds the how.
Options:
- Delete the plan at landing.
- Keep plans on `main`.
Chosen: Delete, because a plan holds nothing that the next agent needs.
Consequences: A plan lives only on its feature branch.

### Decision: Build it as a new skill, keeping-project-docs

Status: Accepted
Area: lanthorn
Context: Several skills need to know where project docs live.
Options:
- A new skill that owns the map and the formats.
- Split it between `spec-writing` and the finish skill: `spec-writing` then carries the architecture rules into every spec edit.
- A template in each project's `AGENTS.md`: the copies drift, and the text costs context in every session.
Chosen: A new skill, because one owner answers "where does knowledge live".
Consequences: The finish skill and capturing-learnings point at it.

## Rules

*Informative.* This design adds the area file [`docs/spec/docs.md`](../../spec/docs.md), with
every rule marked planned. It adds these rule IDs:

- assume: `[docs.assume.folder-preference]`, `[docs.assume.mermaid]`, `[docs.assume.instruction-file]`
- map: `[docs.map.use]`, `[docs.map.places]`, `[docs.map.arch-section]`, `[docs.map.search]`, `[docs.map.learnings]`, `[docs.map.reading-rule]`, `[docs.map.propose]`, `[docs.map.ask]`, `[docs.map.keeps-folders]`, `[docs.map.no-move]`
- design: `[docs.design.folder]`, `[docs.design.on-branch]`, `[docs.design.header]`, `[docs.design.blocks]`, `[docs.design.block-title]`, `[docs.design.block-lines]`, `[docs.design.replaces]`, `[docs.design.links-rules]`, `[docs.design.frozen]`, `[docs.design.rules-first]`, `[docs.design.early]`
- id: `[docs.id.chars]`, `[docs.id.dots]`, `[docs.id.hyphens]`, `[docs.id.levels]`, `[docs.id.area-first]`, `[docs.id.length]`, `[docs.id.placement]`, `[docs.id.brackets]`, `[docs.id.frozen]`, `[docs.id.published]`, `[docs.id.old-ids]`, `[docs.id.withdrawn]`, `[docs.id.moves]`, `[docs.id.search]`
- spec: `[docs.spec.story]`, `[docs.spec.rules]`, `[docs.spec.extras]`, `[docs.spec.no-history]`, `[docs.spec.why-link]`, `[docs.spec.planned]`, `[docs.spec.planned-area]`, `[docs.spec.planned-change]`, `[docs.spec.unplan]`, `[docs.spec.split]`, `[docs.spec.no-index]`, `[docs.spec.read-first]`
- arch: `[docs.arch.core]`, `[docs.arch.optional]`, `[docs.arch.mermaid]`, `[docs.arch.size]`, `[docs.arch.not-here]`, `[docs.arch.constraints]`
- landing: `[docs.landing.order]`, `[docs.landing.unique-ids]`, `[docs.landing.id-format]`, `[docs.landing.summary]`, `[docs.landing.planned]`, `[docs.landing.arch]`, `[docs.landing.supersede]`, `[docs.landing.plan]`, `[docs.landing.no-map]`, `[docs.landing.conflict]`, `[docs.landing.append]`

## Packaging

*Informative.* One new skill holds the map and the formats. Four existing files
get small changes.

- The new skill MUST be `skills/keeping-project-docs/SKILL.md`.
  `[docs.pack.skill]`
- `lanthorn:finishing-a-development-branch` MUST run the docs step.
  `[docs.pack.finish]`
- The finish skill's rule "add no other commit during the landing" MUST make
  an exception for the changes of the docs step. `[docs.pack.finish-exception]`
- The finish skill MUST name the new skill for the details of the docs step.
  `[docs.pack.finish-link]`
- `references/rewriting-history.md` of the finish skill MUST cover a file that
  the branch adds and later deletes. `[docs.pack.rewrite]`
- `lanthorn:spec-writing` MUST state the rule ID format, the header, the
  decision block, and which of its sections a design doc and an area file hold.
  `[docs.pack.spec-writing]`
- `lanthorn:capturing-learnings` MUST name the learnings entry of the map as its
  destination 3. `[docs.pack.learnings]`
- `skills/using-lanthorn/SKILL.md` MUST get one routing row.
  `[docs.pack.router-row]`
- `skills/using-lanthorn/SKILL.md` MUST stay under 500 words.
  `[docs.pack.router-size]`
- This repository MUST get a map in `AGENTS.md`. `[docs.pack.this-repo]`
- That map MUST name `docs/superpowers/specs/` for design docs, `docs/spec/` for
  the living spec, and `docs/superpowers/plans/` for plans.
  `[docs.pack.this-repo-folders]`
- That map MUST name the "What this repo is" section of `AGENTS.md` as the
  architecture document. `[docs.pack.this-repo-arch]`
- The build MUST remove the planned note of `docs/spec/docs.md` for each rule
  that it builds. `[docs.pack.unplan]`
- The plans on `main` of this repository MUST be deleted. `[docs.pack.old-plans]`

*Scenarios, normative.*

| ID | Setup | Pass condition |
|---|---|---|
| D-1 | Brainstorm a small feature in a feature worktree, with a map. | The design doc is in the mapped folder on the branch, with blocks. The area file gains rules with valid IDs, marked planned. |
| D-2 | "Finish the work." on a branch that changes `auth.md`. | The summary lists the changed IDs and the rules still planned. The doc edits land in the feature's commits. No plan file is in the landed history. |
| D-3 | Two branches change `[auth.family-revoke]`. The second finishes. | The rebase conflict stops the landing for a question. |
| D-4 | A refactor moves a component, with no design doc. | `ARCHITECTURE.md` changes in the landing. |
| D-5 | A project with `docs/adr/` and no map. "Finish the work." | The landing goes ahead. Its summary proposes a map that keeps `docs/adr/`. No file moves, and no map is written. |
| D-6 | A branch replaces a decision of an older design doc. | The older block says `Superseded by`, and nothing else in it changes. |

**Why:** `[docs.pack.router-size]`: the router loads in every session and is
close to its limit, so the new row needs a trim elsewhere.

## Left to the builder

*Informative.*

- The word count of the new skill, and which parts go to reference files.
- Whether the scenarios extend `tools/finish-flow-eval/`.
- The templates of the architecture document and of an area file.
- How the docs step detects a changed component, for `[docs.landing.arch]`.

## Open questions

*Informative.*

- **Pilot or scenarios first?** The finish flow took about 100 paid runs. Three
  real landings on one product repository may find more for less.
- **Brainstorming outside a worktree.** `[docs.design.on-branch]` assumes that
  the design starts in a feature worktree. If it starts on the base checkout,
  the doc commits there first.
- **Codex.** The map works the same way there. No scenario runs on Codex.
