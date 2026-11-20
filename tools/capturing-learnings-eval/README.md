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
| `shared-noinstr` | `shared`, with no instruction file | A `proposed:` line, no instruction file created, the fact in memory |
| `answer-no` | `shared`, then "No, keep it out of AGENTS.md." | `AGENTS.md` unchanged, the fact in memory |
| `answer-yes` | `shared`, then "Yes, add it." | `AGENTS.md` names the fact, memory does not |
| `stale` | The `surprise` trap, under a comment that claims the call works from any directory | The wrong comment is gone, and a `learned:` line |
| `landing` | A push that a pre-receive hook rejects without a sign-off | No tracked file changed beyond the commit, the fact in memory, a `learned:` line. A run that never meets the rejection passes on the first rule only |

Three diagnostics:

- `surprise-forced`, `shared-forced`, `noise-forced` tell the agent to use the skill. They test the skill body alone.
- `python3 invoked.py <out-dir>` counts the runs that loaded the skill. It separates "never loaded" from "loaded and wrong".

## Results

`3d48efd` is on main: it is the commit before the skill. Every other hash below names a
commit of the development branch before it landed, and is not on main. The skill and the
hook of `fa1e6b2` are the ones that landed. To rerun, pass the landed commit to `run.sh`.

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
memory or proposed a line for `AGENTS.md`. The five other scenarios, 2 Sonnet runs each:
0 of 10.

### The trigger

The skill at `3b5a9dd` had its trigger in the description only. The metric is the count of
runs that loaded the skill.

| Trigger | Loaded the skill |
|---|---|
| Description and routing row only | 0 of 60 |
| Reworded description and row | 2 of 20 (Sonnet 0 of 10) |
| Plus a start-of-task condition (measured, not adopted) | 11 of 20 (Sonnet 2 of 10) |
| A router rule: "Before your final reply to any task, invoke `lanthorn:capturing-learnings`" | Sonnet 0 of 18, Haiku 13 of 18 |
| A Stop and SubagentStop hook that reminds the agent once | 36 of 36 |

Told to use it, the body passed Sonnet 6 of 6 and Haiku 2 of 4, and recorded nothing on
the typo task in 5 of 5. The hook was adopted in `c6b4c30`.

### Final, after the review

Commit `fa1e6b2`. The skill loaded in 66 of 66 runs. The `shared` prompts no longer say
"do not change any file", and the answer checks also check turn 1.

| Scenario | Sonnet | Haiku |
|---|---|---|
| `surprise` | 5/5 | 5/5 |
| `noise` | 4/5 | 5/5 |
| `shared` | 5/5 | 5/5 |
| `subagent` | 5/5 | 4/5 |
| `shared-noinstr` | 2/2 | 1/2 |
| `answer-no` | 2/2 | 1/2 |
| `answer-yes` | 2/2 | 1/2 |
| `stale` | 2/2 | 2/2 |
| `landing` | 2/2 | 2/2 |
| `chat` | 1/3 | 3/3 |

Open: Sonnet still ends 2 of 3 plain questions, and 1 of 5 `noise` runs, with one sentence
such as "Nothing from this turn is a learning." Before the fix it was 2 of 3 on `chat` too,
and Haiku went from 2 of 3 to 0 of 3. Haiku did not propose the `AGENTS.md` line in turn 1
in 3 of 6 runs of the three extra `shared` scenarios.

Mean cost per run, in USD, without the skill (`3d48efd`) and at `fa1e6b2`:

| Scenario | Sonnet | Haiku |
|---|---|---|
| `noise` | 0.074 → 0.074 | 0.007 → 0.007 |
| `surprise` | 0.088 → 0.110 | 0.012 → 0.014 |
| `shared` | 0.076 → 0.108 | 0.006 → 0.009 |

A turn with nothing to learn costs about the same. A turn that records a learning costs more,
for the comment, the memory note, or the proposal.

### With the hook

Commit `988d0fa`.

| Scenario | Sonnet | Haiku |
|---|---|---|
| `surprise` | 3/3 | 4/5 |
| `noise` | 3/3 | 4/5 |
| `shared` | 3/3 | 5/5 |
| `subagent` | 3/3 | 5/5 |
| `stale` | – | 2/2 |
| `landing` | – | 2/2 |

At `c6b4c30`, the commit before the fixed report shape, Sonnet passed `surprise`, `noise`,
`shared` and `subagent` 5 of 5 each, and every extra scenario 2 of 2 except `landing` 1 of
2. Haiku there passed `shared-noinstr`, `answer-no` and `answer-yes` 2 of 2 each.

### Confirmation on Opus

Commit `fbbff68`, with the same skill and hook as `988d0fa`, 3 runs each.

| Scenario | Opus |
|---|---|
| `surprise` | 3/3 |
| `noise` | 3/3 |
| `shared` | 3/3 |
| `subagent` | 3/3 |

Opus loaded the skill in 12 of 12 runs.

### Failures worth reading

Two failures at `988d0fa` are worth reading. The Haiku `surprise` failure wrote a correct
comment above the call, but without the word "archive", which the check requires. The Haiku
`noise` failure recorded a real surprise that it met on the way: macOS `cat` rejects `-A`.
