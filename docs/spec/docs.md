# Docs system

*Implementation status:* built in `skills/keeping-project-docs/`,
`skills/spec-writing/`, and `skills/finishing-a-development-branch/`. The
scenarios of `tools/finish-flow-eval/results/docs-green/summary.md` test them.
The design is
[2026-11-21-docs-system-design.md](../superpowers/specs/2026-11-21-docs-system-design.md).

## Story

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

## Assumptions

- `superpowers:brainstorming` and `superpowers:writing-plans` write to a folder
  that the instruction file declares. Both say that a preference of the human
  partner overrides their default folder. `[docs.assume.folder-preference]`
- GitHub and GitLab render a Mermaid diagram in a fenced block. The preview of
  VS Code needs an extension. `[docs.assume.mermaid]`
- The instruction file loads in every session. Claude Code and Codex load
  `AGENTS.md` or `CLAUDE.md` at session start. `[docs.assume.instruction-file]`

## Definitions

- **instruction file** – `AGENTS.md` or `CLAUDE.md` at the project root.
- **map** – the `## Docs` section of the instruction file.
- **area** – a part of the system with its own rules, such as `auth`.
- **area file** – the file of the living spec for one area.
- **design doc** – the dated document of one feature, written during
  brainstorming.
- **decision block** – a part of a design doc that starts with
  `### Decision:`, and records one decision in the form of an ADR.
- **rule ID** – the bracketed name at the end of a rule, such as
  `[auth.reuse-grace]`.

## The map

*Informative.* The map names the place of each kind of doc, carries the reading
rule, and gives the search commands. A project with no map gets a proposal built
from its own layout.

- An agent MUST take the place of each kind of doc from the map.
  `[docs.map.use]`
- The map MUST name the architecture document, the living spec folder, the
  design docs folder, and the plans folder. `[docs.map.places]`
- For a small project, the map MAY name a section of the instruction file as the
  architecture document. `[docs.map.arch-section]`
- The map MUST give the search commands for decisions and for rule IDs.
  `[docs.map.search]`
- The map MAY name a learnings file for `lanthorn:capturing-learnings`.
  `[docs.map.learnings]`
- The map MUST hold the reading rule of `[docs.spec.read-first]` as one line.
  `[docs.map.reading-rule]`
- If the project has no map, the agent MUST propose one. `[docs.map.propose]`
- The agent MUST NOT write a proposed map before the human partner says yes.
  `[docs.map.ask]`
- A proposed map MUST keep each folder that the project already uses for that
  kind of doc. `[docs.map.keeps-folders]`
- The agent MUST NOT move an existing doc without a yes. `[docs.map.no-move]`

*Example, illustrative.* The default map:

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

**Why:** each skill that writes a doc has its own default folder today. One map
removes the conflict, and `[docs.assume.folder-preference]` lets the
superpowers skills follow it. `[docs.map.no-move]` exists because teammates and
links point at the old paths.

## Design docs and decisions

*Informative.* Brainstorming writes the design doc in the feature worktree, and
`spec-writing` words it. The design doc holds the story of the change and one
decision block per decision. It links the rules that the branch adds or changes
in the area files.

- The agent MUST write the design doc in the design docs folder of the map.
  `[docs.design.folder]`
- The agent MUST commit the design doc on the feature branch.
  `[docs.design.on-branch]`
- The design doc MUST start with a header that holds one key, `area`, which
  names each area that the doc touches, or `none`. `[docs.design.header]`
- The Approaches section MUST hold one decision block per decision.
  `[docs.design.blocks]`
- A decision block MUST start with `### Decision:` and a title that states the
  chosen option. `[docs.design.block-title]`
- A decision block MUST hold the lines `Status:`, `Area:`, `Context:`,
  `Options:`, `Chosen:`, and `Consequences:`, in this order.
  `[docs.design.block-lines]`
- A decision that replaces an earlier one MUST hold a `Replaces:` line with the
  path and the anchor of the earlier block. `[docs.design.replaces]`
- The design doc MUST link each rule that the branch adds or changes, by its
  rule ID. `[docs.design.links-rules]`
- After a design doc lands, an agent MUST NOT edit it, except for the status
  line of `[docs.landing.supersede]`. `[docs.design.frozen]`
- The designer MUST write each new or changed rule into its area file at design
  time. `[docs.design.rules-first]`
- A design doc MAY land before its code, with its rules marked planned.
  `[docs.design.early]`

*Example, illustrative.* A decision block:

```markdown
### Decision: Accept a reused refresh token for 10 seconds

Status: Accepted
Area: auth
Context: Mobile clients retry on a bad network, and the retry revokes the family.
Options:
- A 10-second grace period: no client change. A stolen token works for 10 seconds.
- A per-client retry token: exact. Every client must change.
Chosen: The grace period, because the clients ship monthly and the risk is short.
Consequences: Changes [auth.family-revoke]. Adds [auth.reuse-grace].
```

**Why:** see [the decision on blocks](../superpowers/specs/2026-11-21-docs-system-design.md#decision-keep-decisions-as-blocks-in-design-docs).
`[docs.design.block-title]` makes a decision findable by one grep, and
the title answers most searches alone. `[docs.design.on-branch]` keeps a
design in step with its code, and `[docs.design.early]` lets a team see a design
before the code lands. `[docs.design.frozen]` keeps each design doc a record of
its day. The status line is the one exception, because a grep finds an old
decision, and only that line says that it no longer holds.

## Rule IDs

*Informative.* A rule ID is a short name in brackets at the end of a rule. Its
first level names the area and the file. Code and tests cite it, and agents find
it with a fixed-string search.

- A rule ID MUST use only `a-z`, `0-9`, `-`, and `.`. `[docs.id.chars]`
- A dot MUST separate levels. `[docs.id.dots]`
- A hyphen MUST join words inside a level. `[docs.id.hyphens]`
- Each level MUST start with a letter, with no leading, trailing, or doubled
  hyphen. `[docs.id.levels]`
- The first level MUST name the area, which is also the stem of its file.
  `[docs.id.area-first]`
- A rule ID MUST have 2 to 4 levels and at most 40 characters.
  `[docs.id.length]`
- A rule ID MUST end the sentence of its rule. `[docs.id.placement]`
- A rule ID MUST be in brackets, in the spec and in each citation.
  `[docs.id.brackets]`
- Once a rule ID is published, an agent MUST NOT rename it or reuse it.
  `[docs.id.frozen]`
- A rule ID is published when it is on the base branch, in a release, or in a
  copy that someone outside the team holds. `[docs.id.published]`
- A file with numbered IDs MUST keep them. Its new rules MUST take word IDs.
  `[docs.id.old-ids]`
- A withdrawn rule MUST keep its ID, with a withdrawn note.
  `[docs.id.withdrawn]`
- A rule that moves to another file MUST keep its ID. `[docs.id.moves]`
- An agent MUST search for a rule ID with a fixed-string search, single-quoted,
  and with a path. `[docs.id.search]`

*Example, normative.* For `[docs.id.chars]` to `[docs.id.length]`:

| ID | Valid | Why |
|---|---|---|
| `[auth.reuse-grace]` | Yes | |
| `[auth.tokens.reuse-grace]` | Yes | Three levels |
| `[AUTH.reuse-grace]` | No | Capitals |
| `[auth:reuse-grace]` | No | A colon |
| `[auth.reuse_grace]` | No | An underscore |
| `[reuse-grace]` | No | One level |

A valid ID matches:
`\[[a-z][a-z0-9]*(-[a-z0-9]+)*(\.[a-z][a-z0-9]*(-[a-z0-9]+)*){1,3}\]`

**Why:** see [the ID decision](../superpowers/specs/2026-11-21-docs-system-design.md#decision-name-rule-ids-with-lowercase-dotted-words).
Parallel branches give two different rules the same number, but rarely
the same name. Lowercase avoids a case clash on a case-insensitive filesystem
and in a case-sensitive search. `[docs.id.search]`: without `-F`, grep reads the
brackets as a character class and matches unrelated lines. A colon is excluded
because git reads a bare `auth:reuse-grace` line at the end of a commit message
as a trailer. `[docs.id.placement]`: `[id]:` at the start of a line becomes a
hidden Markdown link definition. All of these were tested.

## The living spec

*Informative.* An area file answers one question: what must this area do now?
It opens with a short story, then lists the current rules. The feature branch
edits it next to the code that builds the rules.

- An area file MUST open with an informative story of at most 200 words.
  `[docs.spec.story]`
- An area file MUST list its current rules in the form that `spec-writing` sets
  for a rule. `[docs.spec.rules]`
- An area file MAY hold Assumptions and Definitions. `[docs.spec.extras]`
- An area file MUST NOT hold goals, approaches, or open questions.
  `[docs.spec.no-history]`
- The "Why" note of a rule MUST link the decision block that the rule comes
  from, if one exists. `[docs.spec.why-link]`
- A rule that the code does not build yet MUST carry the note *Implementation
  status: planned*. `[docs.spec.planned]`
- An area file whose rules are all planned MAY carry one planned note at its
  top, instead of one note per rule. `[docs.spec.planned-area]`
- A planned change to an existing rule MUST keep the current text of the rule,
  and MUST put the planned text in the note. `[docs.spec.planned-change]`
- The agent that builds a planned rule MUST remove its note in the same commit.
  `[docs.spec.unplan]`
- When an area file passes 300 lines, the agent SHOULD split it into files of
  sub-areas. `[docs.spec.split]`
- The living spec MUST NOT have an index file. `[docs.spec.no-index]`
- Before an agent changes code in an area, it MUST read that area's file.
  `[docs.spec.read-first]`

**Why:** see [the decision to edit the spec on the branch](../superpowers/specs/2026-11-21-docs-system-design.md#decision-edit-the-living-spec-on-the-feature-branch).
`[docs.spec.planned]` keeps the spec true when a feature lands in
slices, and still shows the whole design from its first day.
`[docs.spec.planned-change]`: until the code changes, the old rule is the one
that holds. `[docs.spec.split]`: 300 lines is about 4,000 tokens, small enough to
load in full. `[docs.spec.no-index]`: `ls` lists the areas, and a hand-kept
index drifts. `[docs.spec.read-first]`: an agent that does not read a rule
cannot know that its change breaks one.

## The architecture document

*Informative.* The architecture document is the entry point for a human and an
agent. It shows the components as a diagram, and it states what `ls` and `grep`
cannot show. Its sections depend on the size and kind of the project.

- The architecture document MUST hold a context paragraph, a components
  section, and an invariants section. `[docs.arch.core]`
- The architecture document MAY hold the other sections of the table below.
  `[docs.arch.optional]`
- Each diagram MUST be a Mermaid diagram in a fenced block.
  `[docs.arch.mermaid]`
- The architecture document SHOULD stay under 200 lines. `[docs.arch.size]`
- The architecture document MUST NOT hold library versions, deploy steps, or
  rules of a single area. `[docs.arch.not-here]`
- A decision that constrains later work and is no rule of an area MUST go to
  the architecture document or the instruction file, with a link to its block.
  `[docs.arch.constraints]`

| Section | Holds | Include when |
|---|---|---|
| Context | What the system is, who uses it, which external systems it talks to | Always |
| Components and interactions | A diagram, and one line per component | Always |
| Key assumptions and goals | 3 to 7 that are high level and not evident | When any exist |
| Invariants and boundaries | What must never happen | Always |
| Code map | Where each component lives, and its area file | More than a few top-level folders |
| Cross-cutting patterns | The one way the project does errors, logging, config, persistence | A house pattern exists |
| Main flow | One sequence diagram of the most important request | The order is not obvious |

**Why:** `[docs.arch.mermaid]`: a text diagram renders for a human, and an agent
can edit it in the same landing. `[docs.arch.not-here]`: each of those details
has another owner, so a copy goes stale. `[docs.arch.constraints]`: a decision
such as "Postgres is the only store" belongs to no area, yet binds every one.

## Landing

*Informative.* The finish flow runs a docs step on every landing, after the
rebase and before the full suite. It needs no design doc. It checks the IDs,
lists what changed, keeps the architecture document current, marks replaced
decisions, and deletes the plan. The changes land in the feature's commits.

- The finish flow MUST run the docs step after the rebase and before the full
  suite, on every landing. `[docs.landing.order]`
- The docs step MUST fail the landing if two rules share an ID.
  `[docs.landing.unique-ids]`
- The docs step MUST check each new rule ID against `[docs.id.chars]` to
  `[docs.id.length]`. `[docs.landing.id-format]`
- The landing summary MUST list each rule ID that the branch adds, changes, or
  withdraws. `[docs.landing.summary]`
- The landing summary MUST list each rule that is still planned.
  `[docs.landing.planned]`
- If the branch adds or removes a component, or changes how two components
  interact, the docs step MUST update the architecture document.
  `[docs.landing.arch]`
- For each `Replaces:` line that the branch adds, the docs step MUST set the
  status of the replaced block to `Superseded by <path>#<anchor>`.
  `[docs.landing.supersede]`
- The docs step MUST delete each plan that the branch adds.
  `[docs.landing.plan]`
- In a project with no map, the docs step MUST do nothing except propose a map
  in the landing summary. `[docs.landing.no-map]`
- A rebase conflict where both sides change the same rule ID MUST count as a
  conflict that changes behaviour, so that the finish flow asks.
  `[docs.landing.conflict]`
- A rebase conflict where each side only adds rules with different IDs MUST be
  resolved by keeping both, with no question. `[docs.landing.append]`

*Example, illustrative.* For `[docs.landing.unique-ids]`:

```sh
# IDs that end a line of a rule bullet, in the living spec folder of the map
awk '/^- /{item=1} /^$/{item=0} /^[^- ]/{if ($0 !~ /^- /) item=0}
  item && match($0, /\[[a-z][a-z0-9.-]*\]`?[ \t]*$/) {s=substr($0,RSTART,RLENGTH); gsub(/[` \t]/,"",s); print s}' \
 docs/spec/*.md | sort | uniq -d
```

**Why:** see [the plan decision](../superpowers/specs/2026-11-21-docs-system-design.md#decision-delete-each-plan-at-landing) for
`[docs.landing.plan]`. `[docs.landing.order]` puts the docs on the tree that the suite tests,
so the landed tree is a tested tree. Running it on every landing keeps the
architecture document current after a refactor with no design doc.
`[docs.landing.no-map]` keeps a landing as fast as before for a project that never
asked for docs. `[docs.landing.conflict]`: two branches that change one rule is a design
question, and only the human partner can settle it. `[docs.landing.append]`:
git reports two appends at the end of one file as a conflict, yet keeping both
changes no rule.
