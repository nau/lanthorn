# lanthorn

An agent plugin of engineering skills, for any language and any project. It runs
on Claude Code and on Codex.

It does **not** replace [superpowers](https://github.com/obra/superpowers).
lanthorn adds skills that superpowers does not cover, and forks a superpowers
skill only when that skill must behave differently. Everything else routes back
to superpowers. `skills/using-lanthorn` holds the routing table.

## Skills

| Skill | Use it when |
|-------|-------------|
| `using-lanthorn` | Any task. It routes to the right skill. |
| `spec-writing` | Writing or editing a spec, protocol doc, or normative requirement. |
| `writing-commits` | Writing a commit message or a pull request description. |

## The name

A lanthorn is the old spelling of lantern, from the days when the panes were
scraped horn rather than glass. You carry one to see the ground you are actually
standing on. Every skill here works the same way: read the repository's own
history, the project's own declaration, the actual spec, rather than applying a
template from memory.

## Install

Clone the repository first. Then, for Claude Code:

```
/plugin marketplace add <path-to-clone>
/plugin install lanthorn@lanthorn
```

`marketplace add` also takes a URL or a GitHub `owner/repo` slug.

For Codex, install the clone as a plugin. Codex reads `.codex-plugin/plugin.json`
and loads `skills/` from it.

lanthorn expects superpowers to be installed alongside it. Most rows in the
routing table point there. Claude Code declares this in `plugin.json` and
installs it for you. On other hosts, install superpowers yourself.

### Host differences

| Host | Router loaded automatically |
|------|-----------------------------|
| Claude Code | Yes. A SessionStart hook injects it. |
| Codex | No. Read `skills/using-lanthorn/SKILL.md` at the start of a task. |

## Develop

```
claude plugin validate . --strict
```

`CLAUDE.md` holds the rules for changing this plugin.

## License

MIT
