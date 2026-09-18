# CLAUDE.md

Guidance for Claude Code when working on the `lanterdev` plugin itself.

## What this repo is

A Claude Code plugin. The repository root is both the marketplace and the single
plugin. `.claude-plugin/marketplace.json` points at `"./"`.

```
.claude-plugin/
  marketplace.json    # marketplace entry, source "./"
  plugin.json         # plugin manifest, name and version
skills/
  <skill-name>/SKILL.md
```

`plugin.json` and the marketplace entry must agree on the name. `claude plugin
tag` checks that.

## Design rule: fork, do not copy

lanterdev complements superpowers. Add a skill here only when you can name the
specific behavior that must differ from the superpowers default, or when no
superpowers skill covers the task at all.

Skills here are general purpose. A skill MUST work for any language and any
project. Keep a worked example concrete, but pick a domain most engineers read
without explanation. Project-specific conventions belong in that project's
`CLAUDE.md`, not here.

Record that reason in the new skill's Overview section. Then add a row to the
routing table in `skills/using-lanterdev/SKILL.md`. A skill missing from that
table is unreachable in practice.

## Upstream superpowers

`vendor/superpowers` is a git submodule pinned to the upstream tag that matches
the installed version. It is read-only reference. Claude Code scans only
`skills/` at the plugin root, so nothing under `vendor/` ever registers as a
skill. This is verified, not assumed.

Read it before you fork a skill, and diff against it afterwards:

```bash
git submodule update --init                       # first checkout
diff vendor/superpowers/skills/<name>/SKILL.md skills/<name>/SKILL.md
```

To move to a newer upstream version, check out the new tag inside
`vendor/superpowers`, then commit the changed gitlink here.

## Authoring a skill

Use `superpowers:writing-skills`. It defines the frontmatter, the discovery
rules, and the test-first cycle.

Two rules that this repo adds:

- The `description` field states triggering conditions only. Never summarize the
  skill's workflow there. An agent that can read the workflow in the description
  will skip the body.
- `using-lanterdev` loads in every session. Keep it under 300 words.

## Verify

```bash
claude plugin validate . --strict     # must pass before any commit
claude plugin details lanterdev       # component inventory and token cost
wc -w skills/*/SKILL.md               # watch the session-loaded skills
```

Test a skill locally before installing it:

```bash
claude --plugin-dir .
```

## Writing style

House style for every file in this repository:

- No em dashes. Use an en dash (–).
- No `Co-Authored-By: Claude` trailer. No "Generated with Claude Code" footer.
  Not in commits, not in PR bodies, not anywhere.
- Short sentences, active voice, one instruction per sentence. The plugin's own
  `spec-writing` skill states the full rules. Follow them here too.

## Commits

Conventional commit style: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
Keep the message to one or two paragraphs.
