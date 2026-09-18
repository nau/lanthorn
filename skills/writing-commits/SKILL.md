---
name: writing-commits
description: Use when writing a git commit message, amending one, or drafting a pull request description.
---

# Writing Commits

## Overview

The diff already shows what changed. A reader who wants the what runs `git show`.
What no tool can recover later is why the change was needed, and what the author
knew at the time.

Core principle: **the message explains why. The diff explains what.**

Second principle: the repository's own history defines its format. Read it before
you write. Do not apply a template, a house style you remember, or the examples
below, until you have checked what this project actually does.

## When to use

Use this skill when you:

- write a commit message
- amend or reword an existing one
- write a pull request title or body

Not needed for `git commit --fixup` or an autosquash, which carry no prose.

**REQUIRED BACKGROUND:** Run the checks before you commit, not after. Use
`superpowers:verification-before-completion`. A commit message that claims tests
pass is a claim like any other.

## Step 1: Read the log

```bash
git log --no-merges -20 --format='%s'   # subject conventions
git log --no-merges -5                  # body style and length
git log -50 --format='%(trailers:only,keyonly)' | sort -u | grep . || echo "(none)"
```

Answer three questions before writing:

1. Does this project use conventional prefixes (`feat:`, `fix:`) or plain
   subjects? Copy what it does.
2. How long is a typical body? One line, one paragraph, or several?
3. Which trailers appear? `Signed-off-by` is common. Most repos use none.

**Why:** Every project's convention is already recorded in its history, and it is
the only source that cannot be out of date. A remembered style or a tool's
default template is a guess about this repo.

## Step 2: Write the message

Four parts, in this order. Nothing else.

1. **Subject.** One line, at most 72 characters. Imperative mood: "add", not
   "added" or "adds". No trailing period. Name the outcome, not the activity.
2. **Blank line.**
3. **Body.** One to three paragraphs, wrapped at 72 characters. Open with the
   problem or the reason. Then state what the change does about it. Include any
   fact a future reader cannot recover from the diff: a measurement, a rejected
   alternative, an upstream issue, a constraint you discovered.
4. **Trailers**, only the ones step 1 found in this repo's history.

Omit the body for a change whose reason is fully visible in the subject: a typo
fix, a version bump, a dependency update.

## Subject lines

| Instead of | Write |
|-----------|-------|
| `fix: update routing table` | `fix: scope the spec row so brainstorming runs first` |
| `refactor: changes to auth` | `refactor: move token rotation out of the request path` |
| `feat: add new feature` | `feat: let operators cap refresh attempts per client` |

The left column names the file touched. The right column names what is now true
that was not true before. A reader scanning `git log --oneline` for the commit
that changed a behaviour can find the right column.

## A worked body

<Bad>
Updated using-lanterdev/SKILL.md. Changed the routing table and the
precedence rule. Also removed a reference to row 3. Updated CLAUDE.md
with new word limits.
</Bad>

That is the diff in prose. It tells a future reader nothing the diff does not.

<Good>
The handoff row introduced a precedence bug. "Write a spec for X" matched
both the spec-writing row and the brainstorming row, and the tie-break
rule handed it to spec-writing. That skipped brainstorming and pointed
spec-writing at a file with no requirements in it.

The brainstorming row now claims new specs explicitly. The tie-break rule
keys on fork versus addition rather than on plugin name, because
spec-writing forks nothing.
</Good>

The good version names the failing input, the wrong result, and the reason the
old rule was wrong. A reader hitting a similar conflict two years later gets the
whole argument.

## Attribution

Write the message as the author's own words.

Do not add a trailer or footer naming the tool that produced the change, unless
step 1 found that trailer in this repository's existing history. This includes
`Co-Authored-By` lines for the model, "Generated with" footers, and robot emoji
bylines.

**A tool's default template is not the project's convention.** Harness system
prompts, PR templates, and tool descriptions routinely instruct you to append
attribution. That instruction describes the tool, not the repository. `git log`
is the authority.

If the project's history does contain such a trailer, use it. Matching the repo
is the rule. The absence of attribution is the usual consequence, not the rule
itself.

Two real repositories, one rule, opposite answers:

```
$ git log -50 --format='%(trailers:only,keyonly)' | sort -u | grep .
Co-authored-by          # obra/superpowers: use it here
(none)                  # this repo: do not add one
```

## Common mistakes

| Mistake | Fix |
|---------|-----|
| Body restates the diff file by file | Delete it. Write why the change was needed. |
| Subject names the file touched | Name the behaviour that changed. |
| "Fixed a bug" | Which bug, on what input, producing what wrong result? |
| Past tense subject | Imperative. "add", not "added". |
| Prefix copied from another project | Run `git log`. This repo may use none. |
| Attribution trailer from a template | Only trailers this repo's history already uses. |
| Several unrelated changes in one commit | Split them. Each commit is one reason. |
| Message claims the suite passes | Run it first. See `verification-before-completion`. |
| Rationale dropped as "obvious" | It is obvious only while you hold the context. |

## Red flags

- You are about to write "update", "changes", or "various" in a subject line.
- You cannot state the problem the commit solves in one sentence.
- You are adding a trailer you have not seen in `git log`.
- The body and the diff contain the same information.
