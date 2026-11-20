# capturing-learnings – design

## Overview

*Informative.* During a task, an agent finds out how a tool, a library, a
service, or the codebase really behaves. The code does not show the fact, so it
ends with the session. The next agent then pays to find it again, or fails.

With this skill, the agent notices such a moment, because the skill names the
moments it can observe. It then checks whether a future agent would act
differently because of the fact. If so, it puts the fact in the first
destination that fits: a test, a comment at the place where the fact matters, a
shared instruction file, or the harness's memory. It writes the near
destinations itself. It asks before it changes a shared file. At the end of
the reply, it reports each learning in one line.

*Example, illustrative.* `git archive <commit>:<path>` run from a subdirectory
writes an empty archive and reports no error. One comment above the call keeps
the next agent from finding that out again.

## Goals and non-goals

*Informative.* Claude Code auto memory keeps the user's preferences and
corrections. It skips what it can derive from the code, including debugging
fixes, and its notes load in every session, not where a fact matters. No
superpowers skill captures learnings. This skill is a lanthorn addition, not a
fork.

Goals:

- A learning lands where the next agent meets it, at the moment it matters.
- The agent records a learning without being asked.
- The user sees every recorded learning, in one line each.

Non-goals:

- Preferences, corrections, and feedback from the user. Auto memory keeps them.
- Task status, plans, and progress notes.
- Pruning or merging existing memory. `consolidate-memory` does that.
- A hook, or any other runtime file outside `skills/`. The test harness may
  live under `tools/`, as `tools/spec-style-eval/` does (my call).
- A release or a version bump (my call).

Anti-goals. The skill fails, even if it runs, when it:

- records noise, such as an ordinary typo in a command;
- changes a shared destination without the user's yes;
- interrupts the user about a fact that belongs in a code comment.

## Approaches

*Informative.*

| Approach | Gain | Cost |
|---|---|---|
| **A. A skill whose description names observable moments** | No new machinery. Works on Codex. Testable before anything else is built. | The agent may not notice the moment. |
| B. Approach A, plus a Claude Code hook that reminds the agent on a tool failure or at stop | The reminder does not depend on the agent. | Claude Code only. Fires on failures that teach nothing. One more per-host file. |
| C. One rule in `using-lanthorn`: record what you learned before you report done | Smallest change. | Loads in every session. The free words cannot hold the destination order. Catches only the end of a task, so a subagent's learning is lost. |

The user picked A. If the tests show that agents miss the moment, B can add the
hook on top of A later.

## Assumptions

- The harness offers the skill to the agent by its `description`, and loads the
  body only when the agent invokes it. Claude Code and Codex both do this. `[A-1]`
- Claude Code keeps only the first 5,000 tokens of a loaded skill after a
  compaction. Claude Code sets this limit. `[A-2]`

## Definitions

- **Learning**: a fact about how a tool, a library, a service, or the codebase
  really behaves. The agent found it by work. The code, its tests, and the git
  history do not show it.
- **Load-bearing**: a learning is load-bearing when a future agent would act
  differently because of it.
- **Destination**: a place where a learning is kept. The four destinations are
  listed in `[RL-4]`.
- **Shared destination**: destination 3 of `[RL-4]`.
- **Current repository**: the repository that the agent works in. A plugin
  that is installed from elsewhere is not part of it.
- **Subagent**: an agent whose task another agent dispatched. A fork is a
  subagent too.
- **Controller**: the agent that dispatched a subagent.

## Skill file

*Informative.* The skill is one `SKILL.md` in `skills/capturing-learnings/`,
plus one row in the router. The trigger lives in the `description`, so it must
name moments that the agent can observe.

- The `description` MUST name these three moments, and nothing about the
  workflow. `[SF-1]`
  1. A command, a tool, or a library gave a result that the agent did not
     expect.
  2. A fix worked after an earlier attempt failed.
  3. The agent is about to write the final report of a task or of a subagent.
- `SKILL.md` MUST stay under 4,500 tokens. `[SF-2]`
- The Overview of `SKILL.md` MUST name the gap that auto memory and superpowers
  leave. `[SF-3]`
- `skills/using-lanthorn/SKILL.md` MUST get one routing row for the skill.
  `[SF-4]`
- `skills/using-lanthorn/SKILL.md` MUST stay under 500 words. `[SF-5]`
- The skill text MUST work for any project and any language. `[SF-6]`

**Why:** `[SF-1]`: an agent that reads a workflow in the description follows the
description and skips the body. `[SF-2]` keeps the whole skill after a
compaction `[A-2]`. `[SF-5]`: the router loads in every session.

*Example, illustrative.* A description that meets `[SF-1]`:

> Use when a command, tool, or library behaved in a way you did not expect,
> when a fix worked after an earlier attempt failed, or before you write the
> final report of a task or a subagent.

## Recording a learning

*Informative.* The agent hits one of the three moments, then decides whether
the fact is a load-bearing learning. If it is, the agent picks the first
destination that fits. It writes a near destination itself, or proposes the
text for a shared one. Then it reports the result.

**Who:** the agent that did the work.

**Trigger:** one of the three moments of `[SF-1]`.

**Checks enforced here:**

- The agent MUST record a learning only if it is load-bearing. `[RL-1]`
- The agent MUST NOT record a user preference, a correction, task status, or a
  one-off fix. `[RL-2]`
- The agent MUST NOT record a fact that the code, its tests, or the git history
  already shows. `[RL-3]`
- The agent MUST put a learning in the first destination of this list that
  fits. `[RL-4]`
  1. A test, an assertion, or a check that fails when the fact is forgotten.
  2. A comment at the place where the fact matters.
  3. A section of the project's `AGENTS.md` or `CLAUDE.md`, or a file of a
     skill, new or existing, in the current repository (my call).
  4. The harness's own memory, in the format that the harness's memory rules
     set.
- The agent MUST write a learning at destination 1, 2, or 4 without asking.
  `[RL-5]`
- The agent MUST propose the exact text of a learning for a shared destination,
  and MUST NOT write it before the user says yes. `[RL-6]`
- When the agent proposes a learning, it MUST also write the learning to
  destination 4 at once. `[RL-7]`
- When the user says yes, the agent MUST write the learning to the shared
  destination and delete it from destination 4. When the user says no, the
  agent MUST leave it in destination 4. `[RL-8]`
- When a learning depends on a version, the agent MUST record the version or
  the condition under which it saw the behaviour. `[RL-9]`
- When the agent finds that a recorded learning is wrong, it MUST fix or delete
  it in the same task. `[RL-10]`
- The agent MUST end its reply with one line per learning that it recorded,
  proposed, fixed, or deleted. `[RL-11]`
- With no such learning, the reply MUST have no such line. `[RL-12]`

**Checks delegated elsewhere:** None.

**Why:** `[RL-1]` to `[RL-3]` keep noise out, because a noisy store loses its
value. `[RL-4]`: a test enforces the fact, and a comment loads only where the
fact matters, while memory loads in every session. `[RL-5]` and `[RL-6]` are
the user's mixed rule: near destinations change without a question, and a
shared file changes only with a yes. `[RL-7]` and `[RL-8]` are the user's
choice: a proposal that gets no answer, or a no, does not lose the learning.
`[RL-9]`: here the version is load-bearing,
because the next agent needs it to judge whether the fact still holds. This
differs on purpose from `writing-commits`, which adds a section to `AGENTS.md`
without asking.

*Example, illustrative.* The report lines of `[RL-11]`:

```
learned: git archive from a subdirectory writes an empty archive → comment in lib.sh
proposed: Metals needs autoImportBuilds → AGENTS.md section, waiting for your yes (kept in memory meanwhile)
```

On Codex, destination 4 does not exist. `[RL-4]` then falls to the next
destination that fits, or to none, and `[RL-7]` has no effect.

## Subagent learning

*Informative.* A subagent finds a learning, but it does not own shared files.
It reports the learning, and its controller applies this skill to it.

**Who:** a subagent, then its controller.

**Trigger:** the subagent is about to write its final report.

**Checks enforced here:**

- When the controller dispatches a subagent, it SHOULD ask in the dispatch
  prompt for `learned:` lines in the report (my call). `[SL-1]`
- A subagent MUST put each learning in its final report, as a `learned:` line.
  `[SL-2]`
- A subagent MUST NOT write a learning to any destination. `[SL-3]`
- The controller MUST apply `[RL-1]` to `[RL-12]` to each `learned:` line in a
  subagent's report. `[SL-4]`

**Checks delegated elsewhere:** None.

**Why:** `[SL-1]` reaches a subagent that does not load this skill, without a
fourth trigger in `[SF-1]`. `[SL-3]` keeps one agent in charge of the files, so
two subagents do not write the same comment or memory note. A fork sees the
controller's conversation, so the definition of a subagent names it.

## Testing

*Informative.* The builder tests the trigger as it fires in real use. Headless
`claude -p` runs load the plugin from the repository folder with
`--plugin-dir`. The skill is not pasted into the prompt.

- The builder MUST run each scenario below without the skill first, as
  `superpowers:writing-skills` requires. `[TS-1]`
- The builder MUST run each scenario 5 times on Sonnet and 5 times on Haiku.
  `[TS-2]`
- The skill passes only if it meets every pass criterion of the table. `[TS-3]`

| Scenario | Pass criterion |
|---|---|
| Surprise: a command behaves against its obvious reading, and the task needs a workaround | A learning at destination 1 or 2, with a `learned:` line, in at least 4 of 5 runs per model |
| Noise: a task with an ordinary failure, such as a typo in a command | No learning in at least 4 of 5 runs per model |
| Shared fact: the learning belongs in `AGENTS.md` | A `proposed:` line, the learning in memory, and `AGENTS.md` unchanged, in 5 of 5 runs per model |
| Subagent: a fresh general-purpose subagent finds a surprise (my call) | A `learned:` line in its report, and no file changed for the learning, in at least 4 of 5 runs per model |

## Left to the builder

*Informative.*

- The exact wording of the skill.
- The skill name. `capturing-learnings` is my call.
- The fixture repositories and their surprising behaviour.
- Whether the destination rules need a `references/` file.

## Open questions

*Informative.*

- Should a learning that holds in every project, such as the `git archive`
  behaviour, also go to a place outside the project? `[RL-4]` keeps it in the
  project only. Still open.
- Does a fresh subagent on Codex see lanthorn skills? On Claude Code it does.
  `[SL-1]` covers the case where it does not. Still open.
