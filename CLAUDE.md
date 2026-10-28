# CLAUDE.md

Guidance for an agent working on the `lanthorn` plugin itself. `AGENTS.md` is a
symlink to this file, so every harness reads the same guidance.

## What this repo is

A multi-host agent plugin. The repository root is both the marketplace and the
single plugin. `.claude-plugin/marketplace.json` points at `"./"`.

```
.claude-plugin/
  marketplace.json    # Claude Code marketplace entry, source "./"
  plugin.json         # Claude Code manifest, name and version
.codex-plugin/
  plugin.json         # Codex manifest
hooks/
  hooks.json          # Claude Code SessionStart wiring
  session-start       # emits the router as additionalContext
scripts/
  sync-version.sh     # keeps every host manifest on one version
skills/
  <skill-name>/
    SKILL.md
    references/       # per-host adaptation, only where hosts differ
```

`plugin.json` and the marketplace entry must agree on the name. `claude plugin
tag` checks that.

## Hosts

`skills/` is the universal part. Every host reads `SKILL.md`. Only three things
differ per host, and only those belong in a host-specific file:

| Host | Manifest | Router injected at session start |
|------|----------|----------------------------------|
| Claude Code | `.claude-plugin/plugin.json` | Yes, via `hooks/hooks.json` |
| Codex | `.codex-plugin/plugin.json` | No. See `references/codex-tools.md` |

Adding a host means a manifest, a row in that table, a `FOLLOWERS` entry in
`scripts/sync-version.sh`, and a reference file only if its tool names or its
session behaviour actually differ. Do not add a host you do not use. Each one is
a file that goes stale silently.

**Do not delete `"hooks": {}` from `.codex-plugin/plugin.json`.** It looks like
dead weight and is not. Codex falls back to auto-discovering `hooks/hooks.json`
unless the value is exactly `{}`, and that file is the Claude Code hook. An
absent field, `[]`, and an empty list all trigger the fallback, which makes Codex
register a Claude Code SessionStart hook and prompt the user to trust it.
Upstream hit this in superpowers v6.1.0.

Run `scripts/sync-version.sh` before any release. It fails on drift.

## Design rule: fork, do not copy

lanthorn complements superpowers. Add a skill here only when you can name the
specific behavior that must differ from the superpowers default, or when no
superpowers skill covers the task at all.

Skills here are general purpose. A skill MUST work for any project. It MUST
also work for any language, unless the language is in its name, as in
`writing-scala`. A language skill MUST yield to the project's own rules. Keep a
worked example concrete, but pick a domain most engineers read without
explanation. Project-specific conventions belong in that project's `CLAUDE.md`,
not here.

Record that reason in the new skill's Overview section. Then add a row to the
routing table in `skills/using-lanthorn/SKILL.md`. A skill missing from that
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
- `using-lanthorn` loads in every session, so it pays its cost on every turn.
  Keep it under 500 words. Upstream `using-superpowers` is 485.
- On-demand skills have no such budget. Superpowers' own skills run from 344 to
  3779 words, median around 1050. Write what the skill needs. The `<500` target
  in `writing-skills` is for session-loaded skills, not for these.
- Push per-host and heavy reference material into `references/`. It loads only
  when the skill actually needs it.

## Verify

```bash
claude plugin validate . --strict     # must pass before any commit
scripts/sync-version.sh               # fails if a host manifest drifted
claude plugin details lanthorn        # component inventory and token cost
wc -w skills/*/SKILL.md               # watch the session-loaded skills
```

`claude plugin validate .claude-plugin/plugin.json --strict` warns that
`CLAUDE.md` at the plugin root is not loaded as plugin context. That is correct
and expected. This file is contributor guidance for the repo, not context to
ship to users.

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
