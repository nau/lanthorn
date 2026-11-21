---
name: keeping-project-docs
description: Use when writing or placing a design doc, an architecture document, or a living spec file; when a project's instruction file has no Docs section; before changing code in an area that has a spec file; or when the finish flow runs its docs step.
---

# Keeping Project Docs

## Overview

A project keeps three kinds of knowledge in its repository. The architecture
document is the short entry point. The living spec holds the current rules of
each area, one file per area. The design docs hold the history: one dated file
per feature, with its decisions as ADR-style blocks. No superpowers skill says
where these live or how they stay current, so this lanthorn addition does.

A feature branch edits the living spec next to its code. The git diff is then
the record of what changed, and two branches that change one rule meet as a git
conflict. At landing, the finish flow runs the docs step of
[references/docs-step.md](references/docs-step.md).

How to word a rule, the rule ID format, the design doc header, and the decision
block belong to `lanthorn:spec-writing`. This skill says where each document
lives and what each one holds.

## The map

The map is the `## Docs` section of the project's instruction file, `AGENTS.md`
or `CLAUDE.md`. Take the place of every doc from it.

```markdown
## Docs

Architecture: `ARCHITECTURE.md`
Living spec: `docs/spec/<area>.md`
Design docs: `docs/designs/YYYY-MM-DD-<topic>-design.md`
Plans: `docs/plans/`, deleted when the feature lands
Find a decision: `rg -n -A2 '^### Decision:' docs/designs/`
Find a rule and its citations: `rg -nF '[auth.reuse-grace]' .`
Before you change code in an area, read its file in `docs/spec/`.
```

- The map names the architecture document, the living spec folder, the design
  docs folder, and the plans folder. It gives the two search commands, and it
  ends with the reading rule.
- A small project may name a section of the instruction file as its
  architecture document: "Architecture: the 'What this repo is' section of this
  file".
- The map may name a learnings file. `lanthorn:capturing-learnings` writes there.
- `brainstorming` and `writing-plans` write to the folders that the map names.

**No map yet:**

1. Look at the project's layout. Keep each folder that it already uses for a
   kind of doc. A project with `docs/adr/` gets "Design docs: `docs/adr/`".
2. Propose the map as text. Do not write it before your human partner says yes.
3. Never move an existing doc without a yes. Teammates and links point at the
   old paths.
4. During a landing, the proposal goes into the landing summary, and the
   landing goes ahead without a docs step.

## Design docs

- Write the design doc in the design docs folder, on the feature branch, in the
  feature worktree. A design doc on the base branch never reaches the docs step.
- Its Approaches section holds one decision block per decision, per
  `spec-writing`.
- At design time, write each new or changed rule into its area file, marked
  *Implementation status: planned*. The design doc links each rule by its ID.
- A planned change to an existing rule keeps the current text of the rule, and
  puts the planned text in the note. Until the code changes, the old rule holds.
- An area file whose rules are all planned may carry one planned note at its
  top, instead of one per rule.
- The commit that builds a planned rule removes its note.
- A design doc may land before its code, with its rules still planned.
- After a design doc lands, never edit it, except for the status line of a
  decision that a later block replaces.

## The living spec

An area file answers one question: what must this area do now?

- It opens with a story of at most 200 words, labelled informative.
- Then it lists the current rules, worded per `spec-writing`.
- It may hold Assumptions and Definitions.
- It never holds goals, approaches, or open questions. Those stay in the design
  doc of their change.
- Each rule's "Why" note links the decision block that the rule comes from.
- It uses the operation template (Who, Trigger, Structure) only for a public
  interface, such as an endpoint.
- When it passes 300 lines, split it into files of sub-areas, such as
  `auth-tokens.md` and `auth-sessions.md`. Each moved rule keeps its ID, because
  code and tests cite the ID, not the file.
- There is no index file. `ls` of the living spec folder lists the areas.

**Before you change code in an area, read its file.** The spec wins over the
code. An agent that has not read a rule cannot know that its change breaks one.

## The architecture document

The entry point for a human and an agent. It states what `ls` and `grep` cannot
show. Keep it under 200 lines.

| Section | Holds | Include when |
|---|---|---|
| Context | What the system is, who uses it, which external systems it talks to | Always |
| Components and interactions | A diagram, and one line per component | Always |
| Key assumptions and goals | 3 to 7 that are high level and not evident | When any exist |
| Invariants and boundaries | What must never happen | Always |
| Code map | Where each component lives, and its area file | More than a few top-level folders |
| Cross-cutting patterns | The one way the project does errors, logging, config, persistence | A house pattern exists |
| Main flow | One sequence diagram of the most important request | The order is not obvious |

- Draw each diagram in Mermaid, in a fenced block. GitHub and GitLab render it,
  and an agent can edit it in the same landing.
- Leave out library versions, deploy steps, and the rules of a single area.
  Each has another owner, so a copy goes stale.
- A decision that binds later work and is no rule of an area, such as "Postgres
  is the only store", goes here or into the instruction file, with a link to
  its decision block.

[references/templates.md](references/templates.md) holds a template of an area
file and of an architecture document.

## Rationalisations

| Thought | Reality |
|---|---|
| "I'll put the rules in the design doc and copy them later" | Write them into the area file now, marked planned. One copy, and git sees each change. |
| "This rule is not built yet, so it stays out of the spec" | It goes in with a planned note. The spec then shows the whole design from day one. |
| "The refactor has no design doc, so the docs need nothing" | A moved component changes the architecture document. The docs step runs on every landing. |
| "I'll fix the old design doc so it matches" | A landed design doc is a record. Only a replaced decision's status line changes. |
| "The project has no map, so I'll create the default folders" | Propose a map that keeps the project's folders, and wait for a yes. |
| "An index of areas helps the reader" | `ls` is the index. A hand-kept one drifts. |
