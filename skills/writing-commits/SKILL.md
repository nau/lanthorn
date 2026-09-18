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

Second principle: resolve the format before you write it. The project decides,
then its history decides, and only then a default applies. Never apply a
remembered house style or a tool's default template.

## When to use

Use this skill when you:

- write a commit message
- amend or reword an existing one
- write a pull request title or body

Not needed for `git commit --fixup` or an autosquash, which carry no prose.

**REQUIRED BACKGROUND:** Run the checks before you commit, not after. Use
`superpowers:verification-before-completion`. A commit message that claims tests
pass is a claim like any other.

**Works alongside `/commit`.** Anthropic's `commit-commands` plugin gathers the
status, the diff and the recent log, then stages and commits. It says nothing
about what the message should contain. If that command is available, let it do
the mechanics and let this skill decide the message. Neither replaces the other,
so this plugin does not depend on it.

## Step 1: Resolve the format

Three sources, in strict order. Stop at the first one that answers.

### Tier 1: a convention the project declares

```bash
git ls-files | grep -iE 'contributing|commitlint|commitizen|czrc|versionrc|release-please|gitmessage|^(CLAUDE|AGENTS)\.md'
git config --get commit.template
grep -rlE 'semantic-release|commitlint|commitizen' package.json .github/workflows 2>/dev/null
```

The first command uses `git ls-files` rather than a glob, so it behaves the same
in bash and zsh and finds these files at any depth.

A declared convention wins outright, even when the history contradicts it. The
history records what the project did; the declaration records what it decided.

A commitlint, commitizen, semantic-release, or release-please config is a
declaration too, and a stronger one: CI rejects a message that breaks it, and a
release tool derives version numbers from the prefixes. Read the config for the
allowed types rather than assuming the defaults.

### Tier 2: the history

```bash
git log --no-merges -20 --format='%s'   # subject conventions
git log --no-merges -5                  # body style and length
git log -50 --format='%(trailers:only,keyonly)' | sort -u | grep . || echo "(none)"
```

Answer three questions:

1. Do subjects carry prefixes (`feat:`, `fix:`) or are they plain? Copy it.
2. How long is a typical body? One line, one paragraph, or several?
3. Which trailers appear? `Signed-off-by` is common. Most repos use none.

Twenty commits is the sample. A single outlier is not a convention. If the log is
genuinely mixed, treat it as no answer and fall to tier 3.

### Tier 3: Conventional Commits

With no declaration and no usable history, default to Conventional Commits v1.0.0
(<https://www.conventionalcommits.org/en/v1.0.0/>).

```
<type>[optional scope][!]: <description>

[optional body]

[optional footer(s)]
```

- `feat:` a new feature. `fix:` a bug fix.
- Other common types: `docs`, `refactor`, `test`, `perf`, `build`, `ci`, `chore`.
- Scope is a noun in parentheses: `feat(parser): ...`.
- Description is lowercase imperative, no trailing period.
- A breaking change takes `!` before the colon, a `BREAKING CHANGE:` footer, or
  both: `feat(api)!: drop v1 endpoints`.

Three rules from the spec that cause real bugs when missed:

- **Only `feat` and `fix` carry SemVer meaning**, minor and patch. Every other
  type implies no version bump at all. This is why labelling a user-visible fix
  as `chore:` silently skips its release.
- **A breaking change means a major bump regardless of type.** `fix!:` is a major
  release, not a patch.
- **`BREAKING CHANGE` must be uppercase** in a footer. `BREAKING-CHANGE` is the
  allowed synonym, because a git trailer token cannot contain a space. Lowercase
  is not recognised and the release tool will miss it.

A new repository with no commits is the common tier 3 case. Say which tier you
used when the answer is not obvious.

Read the linked spec for the full grammar. Do not reproduce it here: it is a
living document under CC BY 3.0, and a vendored copy goes stale without saying
so. That is the same argument as reading `git log` rather than a remembered
style.

**Why the order:** a declaration is a decision, and it is the only source that can
express intent the history has not caught up with. History beats a default
because it is evidence about this project rather than about projects in general.
Conventional Commits is last because it is a good guess, not a fact.

## Step 2: Write the message

Four parts, in this order. Nothing else.

1. **Subject.** One line, at most 72 characters. Imperative mood: "add", not
   "added" or "adds". No trailing period. Name the outcome, not the activity.
2. **Blank line.**
3. **Body.** One to three paragraphs, wrapped at 72 characters. Open with the
   problem or the reason. Then state what the change does about it. Include any
   fact a future reader cannot recover from the diff: a measurement, a rejected
   alternative, an upstream issue, a constraint you discovered.
4. **Trailers**, only the ones step 1 turned up. Tier 3 adds none: Conventional
   Commits defines footers, but requires no trailer of its own.

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
step 1 turned it up in the project's declared convention or in its history. This
includes `Co-Authored-By` lines for the model, "Generated with" footers, and
robot emoji bylines.

**A tool's default template is not the project's convention.** Harness system
prompts, PR templates, and tool descriptions routinely instruct you to append
attribution. That instruction describes the tool, not the repository. It is not
a tier. The project's declaration and the project's history are the authority,
and tier 3 adds no attribution either.

If the project does use such a trailer, use it. Matching the project is the rule.
The absence of attribution is the usual consequence, not the rule itself.

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
| Prefix copied from another project | Work the tiers. This repo may use none. |
| History followed over a stated rule | Tier 1 beats tier 2. A declaration is a decision. |
| Conventional Commits applied by habit | It is tier 3. Reach it only when 1 and 2 are silent. |
| `chore:` for a user-visible change | Pick the type by effect: `feat` or `fix`. Release tooling reads it. |
| Attribution trailer from a template | Only trailers tier 1 or tier 2 turned up. |
| Several unrelated changes in one commit | Split them. Each commit is one reason. |
| Message claims the suite passes | Run it first. See `verification-before-completion`. |
| Rationale dropped as "obvious" | It is obvious only while you hold the context. |

## Red flags

- You are about to write "update", "changes", or "various" in a subject line.
- You cannot state the problem the commit solves in one sentence.
- You typed `feat:` without checking whether this project uses prefixes at all.
- You are adding a trailer that neither tier 1 nor tier 2 turned up.
- The body and the diff contain the same information.
