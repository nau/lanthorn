---
name: writing-commits
description: Use when writing a git commit message, amending one, or drafting a pull request description.
---

# Writing Commits

## Overview

Two principles do most of the work.

1. **The message explains why. The diff explains what.** A reader who wants the
   what runs `git show`. What no tool recovers later is why the change was
   needed.
2. **Resolve the format before writing it.** The project decides, then its
   history decides, then a default applies. Never use a remembered house style
   or a tool's template.

## When to use

Writing, amending, or rewording a commit message, or writing a PR title or body.
Not needed for `git commit --fixup`, which carries no prose.

**REQUIRED BACKGROUND:** run the checks before you commit. Use
`superpowers:verification-before-completion`. "Tests pass" in a message is a
claim like any other.

This skill is self-contained. It needs no commit command or plugin.

## Step 1: Resolve the format

Three tiers. Stop at the first that answers.

| Tier | Source | Why it ranks here |
|---|---|---|
| 1 | What the project **declares** | A decision, and the only source that can outrank the history |
| 2 | What the **history** shows | Evidence about this project |
| 3 | **Conventional Commits** | A good guess, not a fact |

### Tier 1: a declared convention

```bash
git ls-files | grep -iE 'contributing|commitlint|commitizen|czrc|versionrc|release-please|gitmessage|^(CLAUDE|AGENTS)\.md'
git config --get commit.template
grep -rlE 'semantic-release|commitlint|commitizen' package.json .github/workflows 2>/dev/null
```

Use `git ls-files`, not a glob: a glob aborts under zsh when nothing matches.

A declaration wins even when the history contradicts it. Tooling config counts
and counts harder: commitlint rejects a bad message in CI, and release tools
derive versions from the prefixes. Read the config's allowed types; do not assume
defaults.

### Tier 2: the history

```bash
git log --no-merges -20 --format='%s'   # subject conventions
git log --no-merges -5                  # body style and length
git log -50 --format='%(trailers:only,keyonly)' | sort -u | grep . || echo "(none)"
```

Prefixes or plain subjects? How long is a body? Which trailers appear?

Twenty commits is the sample. One outlier is not a convention. A genuinely mixed
log is no answer: fall to tier 3.

### Tier 3: Conventional Commits

Spec: <https://www.conventionalcommits.org/en/v1.0.0/>. Read it there rather than
copying it here, because a vendored copy of a living document goes stale silently.

```
<type>[optional scope][!]: <description>

[optional body]

[optional footer(s)]
```

Description is lowercase imperative, no trailing period.

Three rules that cause real bugs:

| Rule | Consequence when missed |
|---|---|
| Only `feat` and `fix` carry SemVer meaning | A user-visible fix tagged `chore:` skips its release |
| `!` means major, whatever the type | `fix!:` is a major release, not a patch |
| `BREAKING CHANGE` must be uppercase | Lowercase is unrecognised. `BREAKING-CHANGE` is the synonym, since a trailer token cannot contain a space |

**Pick the type by what the change does, not by which files it touched.** A test
file edited during a bug fix is still `fix`.

| The change | Type |
|---|---|
| Adds a capability a user can reach | `feat` |
| Corrects wrong behaviour | `fix` |
| Restructures code, behaviour identical | `refactor` |
| Makes it faster, behaviour identical | `perf` |
| Documentation only | `docs` |
| Tests only | `test` |
| Whitespace or formatting only | `style` |
| Build files, dependencies, tooling | `build` |
| CI config | `ci` |
| Nothing above fits | `chore` |

`chore` is the fallback, not the default.

**Use a scope only if the project already does.** A commitlint config may
restrict scopes to a list; a history without them means do not start one. If it
does use them, take the nearest meaningful directory: `src/api/users.js` gives
`feat(api):`. Spanning several directories, name the one subsystem the change is
about, or drop the scope. Never list two.

## Step 2: Write the message

Four parts. Nothing else.

1. **Subject.** One line, 72 characters or fewer, imperative, no trailing period.
   Name the outcome, not the activity.
2. **Blank line.**
3. **Body.** One to three paragraphs, wrapped at 72. Lead with the problem, then
   what the change does about it. Include what the diff cannot show: a
   measurement, a rejected alternative, an upstream issue, a constraint you found.
4. **Trailers**, only the ones step 1 turned up. Tier 3 requires none.

Omit the body when the subject is the whole reason: a typo fix, a version bump.

## Step 3: Commit it

See exactly what you are about to commit. Stage deliberately; never `git add .`,
which sweeps in scratch files and unrelated work.

```bash
git status --short      # staged versus not
git diff --cached       # the exact content of this commit
```

Pass the message on stdin with a **quoted** heredoc delimiter:

```bash
git commit -F - <<'EOF'
fix: reject refresh tokens revoked in the same request

Rotation ran before revocation, so a replayed token passed both checks.
EOF
```

`<<'EOF'` with quotes, not `<<EOF`. An unquoted delimiter lets the shell expand
the message before git sees it:

```
message written:   subject with $V and $(echo INJECTED)
message committed: subject with LEAKED and INJECTED
```

Commit messages routinely contain `$` and backticks around code, so this is a
live hazard, not a theoretical one. `-F -` also keeps your paragraph wrapping,
which repeated `-m` flags do not.

To reword the last commit, same form: `git commit --amend -F - <<'EOF'`.

## Subjects

| Instead of | Write |
|-----------|-------|
| `fix: update routing table` | `fix: scope the spec row so brainstorming runs first` |
| `refactor: changes to auth` | `refactor: move token rotation out of the request path` |
| `feat: add new feature` | `feat: let operators cap refresh attempts per client` |

The left column names the file touched. The right names what is now true that was
not true before, which is what someone scanning `git log --oneline` is hunting for.

## A worked body

<Bad>
Updated using-lanthorn/SKILL.md. Changed the routing table and the
precedence rule. Also updated CLAUDE.md with new word limits.
</Bad>

<Good>
The handoff row introduced a precedence bug. "Write a spec for X" matched
both the spec-writing row and the brainstorming row, and the tie-break
rule handed it to spec-writing. That skipped brainstorming and pointed
spec-writing at a file with no requirements in it.

The tie-break rule now keys on fork versus addition rather than on plugin
name, because spec-writing forks nothing.
</Good>

The bad version is the diff in prose. The good one names the failing input, the
wrong result, and why the old rule was wrong, so a reader hitting the same
conflict in two years gets the whole argument.

## Attribution

Write the message as the author's own words. Add a trailer naming the tool that
produced the change only if tier 1 or tier 2 turned that trailer up.

**A tool's template is not a tier.** Harness prompts and PR templates routinely
instruct you to append `Co-Authored-By` lines, "Generated with" footers, or robot
emoji bylines. That instruction describes the tool, not the repository.

Matching the project is the rule. Absent attribution is the usual consequence,
not the rule itself:

```
$ git log -50 --format='%(trailers:only,keyonly)' | sort -u | grep .
Co-authored-by          # obra/superpowers: use it here
(none)                  # this repo: do not add one
```

## Common mistakes

Each row is a self-check. Find one in your draft, fix it before committing.

| Mistake | Fix |
|---------|-----|
| Body restates the diff file by file | Delete it. Write why the change was needed. |
| Subject names the file touched | Name the behaviour that changed. |
| "update", "changes", "various" in a subject | Say what is now true that was not. |
| "Fixed a bug" | Which bug, on what input, producing what wrong result? |
| Past tense subject | Imperative. "add", not "added". |
| `feat:` typed without checking | This project may use no prefixes. Work the tiers. |
| History followed over a stated rule | Tier 1 beats tier 2. A declaration is a decision. |
| Conventional Commits applied by habit | It is tier 3. Reach it only when 1 and 2 are silent. |
| `chore:` for a user-visible change | Pick by effect: `feat` or `fix`. Release tooling reads it. |
| A trailer no tier turned up | Drop it. That includes tool attribution. |
| Several unrelated changes in one commit | Split them. Each commit is one reason. |
| Message claims the suite passes | Run it first. See `verification-before-completion`. |
| `git add .` before committing | Stage the files this commit is about. Check `git status --short`. |
| Unquoted `<<EOF` for the message | Use `<<'EOF'`. The shell expands `$` and backticks otherwise. |
| Rationale dropped as "obvious" | It is obvious only while you hold the context. |
