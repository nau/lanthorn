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
| `writing-tests` | Writing or changing a test, or testing code that is private or internal. |

## The name

A lanthorn is the old spelling of lantern, from the days when the panes were
scraped horn rather than glass. You carry one to see the ground you are actually
standing on. Every skill here works the same way: read the repository's own
history, the project's own declaration, the actual spec, rather than applying a
template from memory.

## Install

Claude Code:

```
/plugin marketplace add nau/lanthorn
/plugin install lanthorn@lanthorn
```

Codex: clone the repository and install it as a plugin. Codex reads
`.codex-plugin/plugin.json` and loads `skills/` from it.

```
git clone https://github.com/nau/lanthorn
```

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
git clone --recurse-submodules https://github.com/nau/lanthorn
cd lanthorn
claude --plugin-dir .          # load it without installing
claude plugin validate . --strict
```

`vendor/superpowers` is a submodule holding the upstream text to diff a fork
against. Ordinary installs never fetch it.

`CLAUDE.md` holds the rules for changing this plugin.

## License

MIT
