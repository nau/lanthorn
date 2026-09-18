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
| Write or edit a spec, protocol doc, or normative requirement | `lanterdev:spec-writing` |
| Turn an idea into a design | `superpowers:brainstorming` |
| Brainstorming wrote a design spec, before planning starts | `lanterdev:spec-writing` |
| Write an implementation plan | `superpowers:writing-plans` |
| Implement a feature or a bugfix | `superpowers:test-driven-development` |
| Debug a failure | `superpowers:systematic-debugging` |
| Claim a task is done | `superpowers:verification-before-completion` |
| Author a new skill | `superpowers:writing-skills` |

A task not in this table has no lanterdev fork. Use the superpowers skill.

Row 3 is a handoff, not a fork. Brainstorming decides what the spec says.
`spec-writing` decides how each requirement is worded. Apply it to the spec file
before `writing-plans` reads it.

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

When both a lanterdev skill and a superpowers skill match, the lanterdev skill
wins. A fork exists because the default was wrong for this work.

## Adding a fork

Do not copy a superpowers skill into `skills/` because it looks useful. Fork it
only after you can name the specific behavior you must change. Record that reason
in the new skill's Overview section.
