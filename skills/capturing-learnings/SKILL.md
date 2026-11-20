---
name: capturing-learnings
description: Use when a command, tool, or library behaved in a way you did not expect, when a fix worked after an earlier attempt failed, or before you write the final report of a task or a subagent.
---

# Capturing Learnings

## Overview

During a task you find out how a tool, a library, a service, or the codebase
really behaves. The code does not show it, so the fact ends with the session.
The next agent pays to find it again, or fails.

Claude Code auto memory keeps the user's preferences and corrections. It skips
what it can derive from the code, debugging fixes included, and its notes load
in every session, not where a fact matters. No superpowers skill records such
facts. This skill is a lanthorn addition, not a fork.

Core principle: **put each learning where the next agent meets it, at the
moment it matters.**

An explanation in your reply is not a record. The reply ends with the session.

## What counts

A learning is a fact that you found by work, and that the code, its tests, and
the git history do not show. Example: `git archive <commit>:<path>` run from a
subdirectory writes an empty archive, and reports no error.

Record it only if a future agent would act differently because of it.

Not a learning:

- the user's preferences, corrections, and feedback;
- task status, plans, and progress;
- a one-off fix: a typo, a wrong operator, a missing import;
- anything that the code, a test, or the git history already says.

## Where it goes

Take the first destination that fits. Write each learning to one destination
only.

| # | Destination | You |
|---|---|---|
| 1 | A test, an assertion, or a check that fails when the fact is forgotten | Write it |
| 2 | A comment at the place where the fact matters | Write it |
| 3 | The file that the project's instruction file names for such facts. Else a section of `AGENTS.md` or `CLAUDE.md`, or a skill file, in the current repository | Propose it, and wait for a yes |
| 4 | Your harness's memory, in the format that its memory rules set | Write it |

A test that existed before your fix is not destination 1. It shows that the
code broke, not why the obvious way fails. Write a comment, or a new test that
names the trap.

A fact about the whole project, with no single file as its place, is
destination 3, not 2. Examples: how to run the tests, or an environment
variable that every run needs.

For destination 3:

1. Propose the exact text in your reply. Do not edit the file.
2. Write the learning to destination 4 now, so that it is not lost.
3. On a yes, write it to the file, and delete it from destination 4.
4. On a no, leave it in destination 4.

A project with no instruction file is still destination 3. Propose the new
file. Do not create it.

When the behaviour may depend on a version, record the version or the
condition under which you saw it: "git 2.54: ...". The next agent needs it to
judge whether the fact still holds.

When you find that a recorded learning is wrong, fix or delete it in the same
task. That is a learning too.

On a harness with no memory, destination 4 does not exist. Take the next
destination that fits, or none.

## When to record

- Record a learning as soon as the moment has passed: the surprise is
  explained, or the fix works.
- Record it before you hand the work to a landing or finish step: a rebase, a
  merge, a push. A file change after the final test run forces a new run.
- A commit is not a landing step. If you find the learning after your own
  commit, write the comment or the test in a new commit, and run the tests
  again.
- A learning that you find during a landing step, such as a rejected push,
  goes to destination 4 only. Do not change a tested file. A later task can
  move it.

## Report

Your last message of the turn ends with the report. The report is one line per
learning of the whole turn, in this exact form:

```
learned: git archive from a subdirectory writes an empty archive → comment in tools/export.sh
proposed: tests need APP_ENV=test → AGENTS.md section, waiting for your yes (kept in memory meanwhile)
```

- Each line starts with `learned:` or `proposed:`, in lower case, at the start
  of the line.
- `proposed:` is for a learning that waits for a yes, although it is in memory
  meanwhile.
- `learned:` is for every other learning, and for a fix or a deletion.
- The report lists every learning of the turn, also one that you recorded or
  mentioned earlier in the turn.
- A turn with no learning has no report. Write nothing about learnings, and do
  not mention the reminder.

## Subagents

You are a subagent when another agent dispatched your task. A fork is a
subagent too.

- **As a subagent**, write no learning anywhere. Put each one in your final
  report as a `learned:` line, with the destination that you would pick.
- **As a controller**, ask in the dispatch prompt for `learned:` lines in the
  report. Apply this skill to each line that comes back: check it, pick its
  destination, write or propose it, and report it in your own reply.

## Rationalizations

| Excuse | Reality |
|---|---|
| "I explained the cause in my reply." | The reply ends with the session. The next agent never reads it. |
| "The fix makes the fact obvious." | The fix shows what you did, not why the obvious way fails. The next agent writes the obvious way again. |
| "The test already covers it." | The test fails when the bug returns. It does not say why. Add the comment. |
| "I already committed, so a change now forces a rerun." | A commit is not a landing. Add a commit, and run the tests again. |
| "It is a small thing." | Ask one question: would a future agent act differently because of it? If yes, record it. If no, do not. |
| "AGENTS.md is the helpful place, I'll add it." | A shared file needs a yes. Propose it, and keep it in memory. |
| "The user said: do not change any file." | A proposal and a memory note change no project file. Propose, and report. |
| "Memory and AGENTS.md both, to be safe." | One destination. Two copies drift apart. |
| "I'm the subagent, so I'll add the comment myself." | Report it. The controller owns the files. |
