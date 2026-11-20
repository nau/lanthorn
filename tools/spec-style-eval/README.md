# Spec-style eval

Does an agent implement better from a spec in the old `spec-writing` style or in the new
one? An earlier test compared what agents write with each skill version. This harness
measures what an agent builds from the result.

Rule content was about equal in both styles in that earlier test. So "no difference" is the
likely result. Read this harness as a regression check on the new style.

## Pipeline

```
design notes + skeleton  -> spec writer + skill version (old | new) -> spec
spec + skeleton          -> implementer in a fresh context          -> validator + its own tests
validator + hidden tests -> score
```

| Arm | Skill text | Style |
|-----|------------|-------|
| `old` | `git show 9330de6:skills/spec-writing/SKILL.md` | Atomic MUST rules with IDs, "Why" notes, examples |
| `new` | `git show 79acec3:skills/spec-writing/SKILL.md` | The same, plus an informative layer: story, goals, assumptions, open questions |

`run.sh` pins both commits. It never reads the working file.

The task is a Scalus validator, `Deposit`: a guest locks a deposit for a booking, and the
host returns it or claims it. The project comes from the `validator.g8` template, as in
`scalus/scripts/llm-smoke-eval.sh`.

## Why not `claude plugin eval`

`claude plugin eval` compares a plugin against a no-plugin baseline, with one agent per
case. This eval compares two versions of one skill, and it has two agent stages with a file
handed from one to the other. So it is a plain script. It lives in `tools/`, because
`evals/` is the default directory of `claude plugin eval`.

## The oracle

Everything in `oracle/` was written before any spec existed.

| File | Role | Who sees it |
|------|------|-------------|
| `notes.md` | Design notes, as a team chat. They decide every tested point. | Writer |
| `Deposit.scala` | Skeleton: datum, redeemer, validator signature. | Writer, implementer |
| `HiddenOracle.test.scala` | 22 hidden tests in 3 groups. | Nobody. Copied in after the implementer exits. |
| `Deposit.reference.scala` | Reference validator. Passes every hidden test. | Nobody |
| `Deposit.naive.scala` | A validator written "from memory", with stock choices. | Nobody |
| `decisions.md` | Each decision, the chat line that makes it, and its tests. | Nobody |

The notes make 19 decisions. 11 differ from what the Scalus examples do: the fee rounds up,
it has a 2 ADA minimum and a 50% clamp, the host receives exactly the fee, one deadline is
inclusive and another is strict. So the score measures the spec, not the model's memory.

`check-oracle.sh` proves the oracle. Run it after any change to `oracle/`. Result of
2026-11-20, Scalus 1.5.0:

| Validator | Hidden tests passed | Failed |
|-----------|---------------------|--------|
| Reference | 22/22 | none |
| Skeleton, rejects everything | 0/22 | all |
| Reference with Claim bound `>` flipped to `>=` | 21/22 | B3 |
| Reference with free-Cancel bound `<=` flipped to `<` | 21/22 | B1 |
| Reference with the rounding flipped to down | 21/22 | S6 |
| From memory | 10/22 | S2 S6 S7 S8, B2 B3 B4 B5, I5 I6 I7 I9 |

Each hidden test holds a context that the validator must accept and one that it must reject.
So a validator that rejects everything scores 0.

## Metrics

One `result.json` per implementation:

- **Hidden tests passed**, total and per group. This is the primary metric. The denominator
  is always 22.
- Status: `ok`, `compile-failure`, `interface-changed`, `timeout` or `agent-error`. Only
  `ok` runs say something about the spec. Report the others separately.
- Whether the implementer's own tests pass.
- Citation coverage: rule IDs cited in code and tests, over rule IDs in the spec. Assumption
  IDs `[A-n]` are not rules and stay out of the denominator. Cited IDs that the spec does not
  hold are counted as unknown.
- Gaps: the points that the implementer reports as "the spec did not say", in
  `SPEC-GAPS.md`. A missing file is recorded as missing, not as 0.
- Wall time and cost of the agent, from its transcript.
- Leaks: harness file names found in the implementer's transcript. Must be none.

## What "worse" means

Written before the pilot ran.

- The unit is the mean number of hidden tests passed per implementation, over the `ok` runs
  of an arm.
- The noise is the absolute difference in hidden tests passed between two implementations of
  one spec. The pilot measures it once. The full run measures it once per spec.
- **The new style is worse if its mean is lower than the mean of the old style by more than
  the largest noise measured.** A smaller difference is "no difference found".
- A run that is not `ok` counts against the harness or the model, not against the style.
  Exception: if one arm has clearly more such runs, report that as a finding of its own.
- Gate: if both arms pass every hidden test, or if runs that are not `ok` dominate, the task
  is wrong. Change the task. Do not add runs.

## Pilot result, 2026-11-20

The pilot is batch `pilot`: one Sonnet spec per arm, 3 Sonnet implementations.

| Spec | Run | Status | Hidden | Stated | Boundary | Intent | Own tests | Cited | Gaps | Minutes | Cost |
|------|-----|--------|--------|--------|----------|--------|-----------|-------|------|---------|------|
| old | 1 | ok | 22/22 | 8/8 | 5/5 | 9/9 | pass | 35/35 | 5 | 1.5 | $0.40 |
| new | 1 | ok | 22/22 | 8/8 | 5/5 | 9/9 | pass | 33/33 | 5 | 1.2 | $0.32 |
| new | 2 | ok | 22/22 | 8/8 | 5/5 | 9/9 | pass | 31/33 | 7 | 1.4 | $0.38 |

**The gate fires.** Every implementation passes every hidden test. Two more batches changed
one model each, to see if a weaker model leaves the ceiling. They are not part of the pilot
plan, and a model change is not a task change.

| Batch | Writer | Implementer | old | new, run 1 | new, run 2 |
|-------|--------|-------------|-----|------------|------------|
| `pilot-haiku` | Sonnet, the specs of `pilot` | Haiku | 22/22 | 22/22 | 22/22 |
| `pilot-haiku-writer` | Haiku | Sonnet | 22/22 | 22/22 | 22/22 |

- **Noise: not measured.** Two implementations of one spec differ by 0 tests, but every run
  is at the maximum, so no spread can show. Do not use 0 as the noise of a later run. One
  failed test there is not yet "worse".
- No run failed to compile, changed the frozen types, timed out or read the oracle.
- All four specs hold all 19 decisions of `oracle/decisions.md`. This comes from a hand check
  of each spec. One detail: the Haiku new-style spec has no Claim rule for a missing lower
  bound. Its general rule `[GEN-7]` covers the case, and both implementations pass test I2.
- Rule counts vary more between two writer runs than between the styles. Haiku wrote 41
  rules with the old skill and 27 with the new one. The earlier test saw 25 and 38.
- Secondary metrics show no clear difference. Citation coverage is 80% to 100% in both arms.
  The implementers reported 4 to 6 gaps per old-style spec and 3 to 8 per new-style spec.
- Cost and agent time per run, with 3 agents in parallel: a Sonnet spec costs $0.20 and
  takes 1 minute. A Sonnet implementation costs $0.38 and takes 1.5 minutes. A Haiku
  implementation costs $0.25 and takes 5 minutes. Scaffold and scoring time is not measured.
- All runs of this day cost about $4.

Why the score is flat:

1. The notes decide every tested point.
2. Both skill versions demand a numbered rule for every behaviour.
3. So every spec is complete, and a complete rule list is enough for any implementer.

The style has nothing left to change in the score. More runs of this task will not help.
The result shows no regression from the new style in 9 implementations. It cannot show a
gain. A gain can show only on a case that no rule of the spec lists, and this task has no
such case by design.

**Decision, 2026-11-20: closed as "no regression found".** The full run of 12
implementations did not start, and it is not planned. The harness stays as a rerunnable
check. To test for a gain, first add hidden tests on a case that no rule lists, then run the
pilot again.

## Run it

```bash
export TEMPLATE_DIR=/path/to/validator.g8
tools/spec-style-eval/check-oracle.sh

# Pilot: one spec per arm, the new one implemented twice to measure noise.
tools/spec-style-eval/run.sh old sonnet sonnet 1 --batch pilot
tools/spec-style-eval/run.sh new sonnet sonnet 2 --batch pilot

# Implement an existing spec once more.
tools/spec-style-eval/run.sh old sonnet sonnet 1 --batch pilot \
    --spec tools/spec-style-eval/results/pilot/old-s1
```

One invocation writes one spec and implements it `<runs>` times. The full run is 3
invocations per arm with `<runs>` = 2: 6 specs, 12 implementations.

Results go to `results/<batch>/<arm>-s<n>/`: `spec.md`, `writer.json`, and per
implementation `result.json`, the code, the logs and `SPEC-GAPS.md`. `summary.md` holds the
tables. Transcripts stay on disk and out of git.

## Isolation

- Both agents run headless with `--dangerously-skip-permissions`, as the Scalus script does.
  Each one runs in its own temporary directory.
- Both run with `--setting-sources project`. The plugins, hooks and `CLAUDE.md` of the user
  account stay out. This matters: an installed `lanthorn` plugin would hand its own
  `spec-writing` version to the writer of both arms. The implementer still gets the
  template's `AGENTS.md` and the `scalus` plugin, because the project enables it.
- The writer gets the skill text as `STYLE.md`, the same file name in both arms. If the
  pinned commit has a `references/` directory in the skill, the writer gets it next to
  `STYLE.md`. It runs with the `Skill` tool disabled. The harness rejects a writer run that
  loaded a skill.
- The implementer's directory holds the scaffold and `SPEC.md` only. The writer's directory,
  with its copy of the notes, is deleted before the first implementer starts.
- The hidden tests run in a clean project: the template's build file, the implementer's main
  sources and the hidden test file. The implementer's own tests are left out, so a broken
  test file cannot change the score.

## Limits

- One task, one domain. A result holds for this validator.
- The writer is part of the noise. Two specs from one skill version differ.
- The implementer can read any file on the machine. The transcript check finds a read of the
  oracle, but it does not prevent one.
