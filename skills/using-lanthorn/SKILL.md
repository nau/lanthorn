---
name: using-lanthorn
description: Use at the start of any engineering task, to choose between a lanthorn skill and a superpowers skill.
---

# Using lanthorn

lanthorn does not replace superpowers. It forks a superpowers skill only when
that skill must behave differently, and it adds skills that superpowers does not
cover. Everything not listed below still comes from superpowers.

## Routing table

| Task | Skill |
|------|-------|
| Turn an idea into a design, including a new spec | `superpowers:brainstorming` |
| Brainstorming wrote a design spec, before planning starts | `lanthorn:spec-writing` |
| Edit an existing spec, or word a single requirement | `lanthorn:spec-writing` |
| Write an implementation plan | `superpowers:writing-plans` |
| Implement a feature or a bugfix | `superpowers:test-driven-development` |
| Write or change a test, or test code that is private | `lanthorn:writing-tests` |
| Add or change a log statement, or pick a log level or field name | `lanthorn:writing-logs` |
| Add an abstraction, a dependency, a config option, or code for a "later" need, or review a diff for over-engineering | `lanthorn:writing-less-code` |
| Debug a failure | `superpowers:systematic-debugging` |
| Claim a task is done | `superpowers:verification-before-completion` |
| Write a commit message or a PR description | `lanthorn:writing-commits` |
| Author a new skill | `superpowers:writing-skills` |

A task not in this table has no lanthorn fork. Use the superpowers skill.

**"Write a spec for X" starts at brainstorming, not at `spec-writing`.** A spec
that does not exist yet has no requirements to word. Brainstorming decides what
the spec says. `spec-writing` then decides how each requirement reads, on the
file brainstorming produced, before `writing-plans` consumes it.

The `lanthorn:` and `superpowers:` prefixes are Claude Code namespacing. On
other harnesses, use the bare skill name.

## Platform adaptation

If your harness appears here, read its reference file before you use the table:

- Codex: `references/codex-tools.md`

Claude Code needs no reference file. Its SessionStart hook injects this skill,
and the `Skill` tool loads the rest by their prefixed names.

## Rule

Read the routing table before you answer. If a row matches, invoke that skill
first. Announce which skill you use and why.

Precedence depends on whether the lanthorn skill is a fork or an addition.

- A lanthorn **fork** of a superpowers skill replaces the original. It exists
  because the default was wrong for this work.
- A lanthorn **addition** covers something superpowers does not. It runs
  alongside the superpowers skills, not instead of them. `spec-writing`,
  `writing-tests`, `writing-logs` and `writing-less-code` are additions. None
  displaces brainstorming, planning, or TDD.
