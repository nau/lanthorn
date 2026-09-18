# lanthorn on Codex

## No session-start injection

Codex does not run the SessionStart hook that Claude Code runs. Nothing puts the
routing table in front of you automatically.

Read `skills/using-lanthorn/SKILL.md` yourself at the start of an engineering
task, before your first answer. Treat that read as the first step of the task,
not as optional background.

## Loading a skill

Codex discovers skills through the `skills` path in `.codex-plugin/plugin.json`.

If your tool list exposes a skill-loading tool, use it with the bare skill name,
for example `spec-writing`. The `lanthorn:` prefix in the routing table is
Claude Code namespacing, and other harnesses do not use it.

If no such tool exists, read the file directly:

```
skills/<skill-name>/SKILL.md
```

Reading the file is equivalent. The skill is the text, not the tool call.

## superpowers skills

Most routing-table rows point at superpowers, which is a separate plugin.
Install it for Codex the same way you installed lanthorn. If it is absent, the
row has no target: say so rather than improvising the workflow from memory.

Trust your actual tool list over any table, including this one, when they
disagree.
