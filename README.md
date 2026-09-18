# lanterdev

An agent plugin of engineering skills, for any language and any project. It runs
on Claude Code and on Codex.

It does **not** replace [superpowers](https://github.com/obra/superpowers).
lanterdev adds skills that superpowers does not cover, and forks a superpowers
skill only when that skill must behave differently. Everything else routes back
to superpowers. `skills/using-lanterdev` holds the routing table.

## Skills

| Skill | Use it when |
|-------|-------------|
| `using-lanterdev` | Any task. It routes to the right skill. |
| `spec-writing` | Writing or editing a spec, protocol doc, or normative requirement. |

## Install

Clone the repository first. Then, for Claude Code:

```
/plugin marketplace add <path-to-clone>
/plugin install lanterdev@lanterdev
```

`marketplace add` also takes a URL or a GitHub `owner/repo` slug.

For Codex, install the clone as a plugin. Codex reads `.codex-plugin/plugin.json`
and loads `skills/` from it.

lanterdev expects superpowers to be installed alongside it. Most rows in the
routing table point there. Claude Code declares this in `plugin.json` and
installs it for you. On other hosts, install superpowers yourself.

### Host differences

| Host | Router loaded automatically |
|------|-----------------------------|
| Claude Code | Yes. A SessionStart hook injects it. |
| Codex | No. Read `skills/using-lanterdev/SKILL.md` at the start of a task. |

## Develop

```
claude plugin validate . --strict
```

`CLAUDE.md` holds the rules for changing this plugin.

## License

MIT
