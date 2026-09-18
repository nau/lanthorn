---
name: using-lanterdev
description: Use at the start of any engineering task, to choose between a lanterdev skill and a superpowers skill.
---

# Using lanterdev

lanterdev does not replace superpowers. It forks a superpowers skill only when
that skill must behave differently, and it adds skills that superpowers does not
cover. Everything not listed below still comes from superpowers.

## Routing table

| Task | Skill |
|------|-------|
| Turn an idea into a design, including a new spec | `superpowers:brainstorming` |
| Brainstorming wrote a design spec, before planning starts | `lanterdev:spec-writing` |
| Edit an existing spec, or word a single requirement | `lanterdev:spec-writing` |
| Write an implementation plan | `superpowers:writing-plans` |
| Implement a feature or a bugfix | `superpowers:test-driven-development` |
| Debug a failure | `superpowers:systematic-debugging` |
| Claim a task is done | `superpowers:verification-before-completion` |
| Write a commit message or a PR description | `lanterdev:writing-commits` |
| Author a new skill | `superpowers:writing-skills` |

A task not in this table has no lanterdev fork. Use the superpowers skill.

**"Write a spec for X" starts at brainstorming, not at `spec-writing`.** A spec
that does not exist yet has no requirements to word. Brainstorming decides what
the spec says. `spec-writing` then decides how each requirement reads, on the
file brainstorming produced, before `writing-plans` consumes it.

The `lanterdev:` and `superpowers:` prefixes are Claude Code namespacing. On
other harnesses, use the bare skill name.

## Platform adaptation

If your harness appears here, read its reference file before you use the table:

- Codex: `references/codex-tools.md`

Claude Code needs no reference file. Its SessionStart hook injects this skill,
and the `Skill` tool loads the rest by their prefixed names.

## Rule

Read the routing table before you answer. If a row matches, invoke that skill
first. Announce which skill you use and why.

Precedence depends on whether the lanterdev skill is a fork or an addition.

- A lanterdev **fork** of a superpowers skill replaces the original. It exists
  because the default was wrong for this work.
- A lanterdev **addition** covers something superpowers does not. It runs
  alongside the superpowers skills, not instead of them. `spec-writing` is an
  addition. It never displaces brainstorming, planning, or TDD.

## Adding a fork

Do not copy a superpowers skill into `skills/` because it looks useful. Fork it
only after you can name the specific behavior you must change. Record that reason
in the new skill's Overview section.
