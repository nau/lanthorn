# Metals lookup

`scripts/metals.sh` drives a standalone Metals MCP server over HTTP. It needs no
editor and no MCP registration. Each checkout, including each git worktree, gets
its own server, so a lookup never reads another branch.

Checked against `metals-mcp` 1.6.9. Run `scripts/metals.sh --help` for the full
command list.

## Setup

The script needs `metals-mcp`, `curl` and `jq` on `PATH`.

- `metals-mcp` is missing? Ask the user before you install it. The install
  command is `cs install metals-mcp`. Coursier can resolve a release candidate,
  so check `metals-mcp --version` afterwards.
- Never pass `--client` to `metals-mcp`. It writes a `.mcp.json` at the
  repository root, and most `.gitignore` files do not cover that file.
- Metals writes `.metals/`, `.bloop/` and, for sbt, `project/metals.sbt`. Never
  commit them. If `git status` shows them, tell the user.

## Cost

| What | Cost |
|---|---|
| Memory | About 550 MB for each server, plus a shared Bloop daemon |
| First `start` in a checkout | sbt or mill exports the build, then Bloop compiles every module. Seconds for a small project, several minutes for a large one |
| Later `start` in the same checkout | Reuses the running server |
| One lookup | 0.1 to 0.5 s, plus an incremental compile before `usages` |

The script compiles through Bloop because the index reads the compiler output.
Bloop keeps a compile state apart from sbt or mill. That compile is not a build
check.

## Lifecycle

1. `scripts/metals.sh start` once, from anywhere inside the checkout. It finds
   the build root, starts a server or reuses one that answers, waits for the
   build import, then compiles. It prints `Ready.` at the end.
2. Look up symbols.
3. A build file changed, such as `build.sbt` or `build.mill`? Run
   `scripts/metals.sh import`. A stale import gives confident wrong answers.
4. `scripts/metals.sh stop` before you remove the worktree. Otherwise leave the
   server running for the next session in that checkout. `stop` does not stop
   the Bloop daemon, which all checkouts share.

Set `METALS_WORKSPACE` when the build root is not the git top level.

`start` does not detect a Metals that an editor runs on the same checkout. It
starts its own server beside it. That combination was not tested. Expect a
second index and a second Bloop compile.

## Commands

| Command | Use it for | Example |
|---|---|---|
| `find <Name> [kind...]` | Get the FQCN for a simple name. It matches at camel-case word starts, so `find mint` also lists `makeMintingTx` | `find Order class trait` |
| `inspect <fqcn> <ctx>` | Members of a type, or the signatures of a method | `inspect shop.Order core` |
| `source <fqcn> <ctx>` | Source of a symbol. Library source comes from source jars | `source cats.data.NonEmptyList core` |
| `docs <fqcn> <ctx>` | ScalaDoc of a symbol | `docs cats.data.NonEmptyList core` |
| `usages <fqcn> <ctx>` | Every reference, as `file:line`. The list includes the definition. A line with more than one reference ends in `(2 references)` | `usages shop.Order.total core` |
| `jar <fqcn> <module>` | The `org:name:version` and the sources jar that define a library type or member. For an inherited member, pass the declaring type | `jar cats.data.NonEmptyList core` |
| `modules` | Module names to pass as `<ctx>` | `modules` |

`<ctx>` is a module name from `modules`, or a `.scala` file in that module. It
gives Metals the classpath that resolves the FQCN. Pick a module that can see
the symbol, such as the module that declares it. `usages` still reports
references from every module, test modules too.

## Where Metals is wrong without an error

Metals reports `isError: false` in each of these cases. `usages` checks each one
and exits with code 3. The other commands do not check.

| Case | What Metals returns | What the script does |
|---|---|---|
| The FQCN does not name a declared symbol: an inherited member, a typo, or an `inline` method of a library trait | Usages of the enclosing object and its whole package, including every `package` and `import` line | `inspect` first. It refuses when the first line names another symbol, such as `package shop` |
| The index is older than the files | References from before your edit, or none | Runs an incremental `compile-full` before every `usages` |
| A file does not compile | No references from that file | Prints the compile errors as a warning, then the partial list |
| The answer is empty | An empty string | Exits 3. Empty is not proof that a symbol is unused |

An inherited member: query the type that declares it. The `extends` clause in
`source` of the subclass names that type. An `inline` method of a library type
does not resolve, not even on its declaring type. Go straight to grep for it.

## Do not use

| Tool | Use instead | Why |
|---|---|---|
| `compile-*` and `test` as a check | The project's build tool | Bloop is not the build. Its settings and its compile state can differ |
| `find-dep` | The version rule in `SKILL.md` | It reports release candidates as the latest version |
| `format-file` and the scalafix tools | The project's formatter and linter | The build tool already runs them, with the project's configuration |
