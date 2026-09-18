# lanterdev

A Claude Code plugin of engineering skills, for any language and any project.

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

Clone the repository, then point Claude Code at the clone:

```
/plugin marketplace add <path-to-clone>
/plugin install lanterdev@lanterdev
```

`marketplace add` also takes a URL or a GitHub `owner/repo` slug.

## Develop

```
claude plugin validate . --strict
```

`CLAUDE.md` holds the rules for changing this plugin.

## License

MIT
