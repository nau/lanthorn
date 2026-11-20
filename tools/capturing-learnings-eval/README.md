# capturing-learnings eval

Does an agent record what it learns about a tool, in the right place, without being asked?
This harness runs headless agents on small fixture repositories and scores each run with a
script. It tests the skill `capturing-learnings` against
`docs/superpowers/specs/2026-11-20-capturing-learnings-design.md`.

## Run

```bash
git submodule update --init vendor/superpowers
tools/capturing-learnings-eval/run.sh <lanthorn-commit> <scenario> <model> <rep> [<out-dir>]
python3 tools/capturing-learnings-eval/summarize.py <out-dir> --reasons
```

Many runs in parallel:

```bash
for s in surprise noise shared subagent; do for m in sonnet haiku; do for r in 1 2 3 4 5; do
  echo "$s $m $r"; done; done; done |
  xargs -P 10 -n 3 sh -c 'tools/capturing-learnings-eval/run.sh <commit> "$0" "$1" "$2" /tmp/cl-eval/<name>'
```

Harness self-tests: `python3 -m unittest discover -s tools/capturing-learnings-eval/tests`.

## How a run works

- `run.sh` extracts `skills/`, `hooks/` and the manifests of `<lanthorn-commit>` with
  `git archive`, once per commit, and loads them with `--plugin-dir`, next to the vendored
  superpowers. `--setting-sources project` keeps your own plugins, hooks and `CLAUDE.md` out.
- Each run gets its own auto memory folder through
  `--settings '{"autoMemoryDirectory": "<run>/memory"}'`. A run never writes to your memory.
- Fixtures live under `<out-dir>`, outside this repository. The default is
  `${TMPDIR:-/tmp}/capturing-learnings-eval/<date>`.
- A scenario with an `answer.md` runs two turns: the prompt, then the answer with `--resume`.

## Scenario contract

`scenarios/<name>/` holds:

- `setup.sh <dir>` creates a git repository at `<dir>` with at least one commit.
- `prompt.md` is the user message. `answer.md`, when present, is the second turn.
- `check.py <run-dir>` prints `PASS` or `FAIL: <reason>` and exits 0 or 1. It uses `lib.py`.

| Scenario | The agent meets | It passes when |
|---|---|---|
| `surprise` | `git archive <commit>:docs` run from `tools/` writes an empty archive, with no error | A `learned:` line, a comment that names the behaviour, and the fact in no second place |
| `noise` | A typo in a command, and an ordinary bug | No `learned:` or `proposed:` line, and no memory |
| `shared` | Tests pass only with `APP_ENV=test`, which only CI sets | A `proposed:` line, `AGENTS.md` unchanged, the fact in memory |
| `subagent` | The `surprise` trap, inside a subagent | The subagent reports a `learned:` line and writes no comment for it; the final reply has a `learned:` line |

## Results

### Baseline, without the skill

Commit `3d48efd`, 5 runs per scenario and model.

| Scenario | Sonnet | Haiku |
|---|---|---|
| `surprise` | 0/5 | 0/5 |
| `noise` | 5/5 | 5/5 |
| `shared` | 0/5 | 0/5 |
| `subagent` | 0/5 | 0/5 |

Every agent found the cause. In `surprise`, one Sonnet reply said: "When run from a
subdirectory, `git archive` only archives the part of the tree matching that subdirectory."
No run reported a learning. One Haiku run of 10 left a comment at the fix. No run wrote
memory or proposed a line for `AGENTS.md`.
