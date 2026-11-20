# capturing-learnings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the lanthorn skill `capturing-learnings`, which makes an agent record a fact about how a tool or the codebase behaves in the first destination that fits, and prove it with headless agent runs.

**Architecture:** One `SKILL.md` plus one routing row and one README row. A scenario harness in `tools/capturing-learnings-eval/` runs headless `claude -p` agents on fixture repositories, with superpowers and one pinned lanthorn commit loaded as plugins, and scores each run by a script. A baseline commit without the skill is the RED arm. The commit with the skill is the GREEN arm.

**Tech Stack:** bash, python3 (standard library only), git, Claude Code CLI (`claude -p`, stream-json output).

**Spec:** `docs/superpowers/specs/2026-11-20-capturing-learnings-design.md`

## Global Constraints

- No em dash in any file. Use an en dash.
- Conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`, `test:`. No trailer and no attribution line.
- The `description` of the skill names the three trigger moments of `[SF-1]` and nothing about the workflow.
- `SKILL.md` stays under 4,500 tokens, as `claude plugin details lanthorn` estimates. `[SF-2]`
- `skills/using-lanthorn/SKILL.md` stays under 500 words by `wc -w`. `[SF-5]`
- The routing row has 12 words or fewer by `wc -w`, and goes directly after the row "Debug a failure". `[SF-7]`
- The skill text works for any project and any language. `[SF-6]`
- `claude plugin validate . --strict` passes before every commit.
- The harness never writes to the user's own memory or settings. Every run uses its own `autoMemoryDirectory`.

## Review Focus

1. **A project with no `AGENTS.md` or `CLAUDE.md`.** The agent proposes the file and keeps the fact in memory; it creates no file. Test: scenario `shared-noinstr`, Task 2.
2. **The user answers the proposal.** On no, the fact stays in memory and `AGENTS.md` is unchanged. On yes, the fact moves to `AGENTS.md` and leaves memory. Test: scenarios `answer-no` and `answer-yes`, Task 2.
3. **A recorded learning is wrong.** The agent fixes or deletes the old comment. Test: scenario `stale`, Task 2.
4. **A learning found during landing.** The agent writes it to memory only and changes no tracked file. Test: scenario `landing`, Task 2.
5. **The same learning in two places.** A learning written as a comment does not also appear in `AGENTS.md` or memory. Test: an extra check in scenario `surprise`, Task 1.

---

### Task 1: Scenario harness and baseline

**Files:**
- Create: `tools/capturing-learnings-eval/run.sh`
- Create: `tools/capturing-learnings-eval/summarize.py`
- Create: `tools/capturing-learnings-eval/lib.py`
- Create: `tools/capturing-learnings-eval/scenarios/<name>/setup.sh`, `prompt.md`, `check.py` for `surprise`, `noise`, `shared`, `subagent`
- Create: `tools/capturing-learnings-eval/README.md`

**Interfaces:**
- Produces: `run.sh <lanthorn-commit> <scenario> <model> <rep> [<out-dir>]`. It writes the run directory `<out-dir>/<scenario>.<short-commit>.<model>.<rep>/` with `repo/` (the fixture after the run), `memory/` (the run's auto memory), `transcript.jsonl`, `final.txt`, and `verdict.txt`. `<out-dir>` defaults to `${TMPDIR:-/tmp}/capturing-learnings-eval/<date>`, outside the repository, so that no fixture repository nests inside lanthorn. Exit 0 when the agent ran, whatever the verdict.
- Produces: a scenario directory contract. `setup.sh <dir>` creates a git repository at `<dir>` with at least one commit. `prompt.md` is the user message. `check.py <run-dir>` prints `PASS` or `FAIL: <reason>` on one line, and exits 0 or 1.
- Produces: `lib.py` with `final_text(run_dir) -> str`, `report_lines(run_dir, kind) -> list[str]` (`kind` is `learned` or `proposed`), `memory_text(run_dir) -> str` (all files under `memory/` joined), `changed_files(run_dir) -> list[str]` (`git status --porcelain` plus files changed since the fixture's first commit), `subagent_edits(run_dir) -> list[str]` (the new text of each Edit or Write call whose stream-json event has a non-null `parent_tool_use_id`).
- Produces: `summarize.py <out-dir>` prints one row per scenario, commit, and model: `scenario commit model passed/total`.

- [ ] **Step 1: Write `run.sh`**

It extracts `skills/`, `hooks/`, `.claude-plugin/` and `.codex-plugin/` of `<lanthorn-commit>` with `git archive` into `<out-dir>/arms/<short-commit>/`, once per commit. Run `git archive` from the repository root: from a subdirectory it writes an empty archive for a path and reports no error. It runs `setup.sh`, then from inside `repo/`:

```bash
claude -p "$(cat prompt.md)" --model "$model" \
  --setting-sources project --strict-mcp-config --no-session-persistence \
  --plugin-dir "$ROOT/vendor/superpowers" --plugin-dir "$ARM" \
  --settings "{\"autoMemoryDirectory\": \"$RUN/memory\"}" \
  --dangerously-skip-permissions \
  --output-format stream-json --verbose </dev/null >"$RUN/transcript.jsonl"
```

For `answer-yes` and `answer-no` (Task 2), drop `--no-session-persistence`, read the session id from the `system/init` event, and run a second `claude -p "$(cat answer.md)" --resume <id>` with the same flags. Then it runs `check.py` and writes `verdict.txt`.

- [ ] **Step 2: Verify memory isolation**

Run `run.sh HEAD noise sonnet 0` with a temporary `prompt.md` of "Save a memory that the build uses make, then reply done." Expected: a file under `<run>/memory/` mentions make, and no new file under `~/.claude/projects/` names the fixture path. If headless runs do not write memory, stop and report it: `[RL-7]` is then untestable, and the plan needs the user.

- [ ] **Step 3: Write the four scenarios of the spec's Testing table**

| Scenario | Fixture and prompt | `check.py` passes when |
|---|---|---|
| `surprise` | A script must copy `docs/` of a given commit into `out/` and runs from `tools/`. Its test `bash tools/test-export.sh` fails while the output is empty. The obvious fix, `git archive <commit>:docs \| tar -x` from inside `tools/`, writes an empty archive with no error. Prompt: make the test pass. | A `learned:` line names the archive behaviour. A comment or test in `repo/` names it. `AGENTS.md` does not name it, and `memory/` does not name it (Review Focus 5). |
| `noise` | A Python project. The prompt asks to run `pytset -q` (a typo) and fix the one failing test. | No `learned:` or `proposed:` line. `memory/` is empty. |
| `shared` | A project with `AGENTS.md`. Every test fails unless `TZ=UTC` is set, and no single file is the place for it. Prompt: fix the failing date test. | A `proposed:` line names `TZ=UTC`. `AGENTS.md` is unchanged. `memory/` names `TZ=UTC`. |
| `subagent` | The `surprise` fixture. Prompt: dispatch one general-purpose subagent with the Agent tool to make the test pass, then report. | The Agent tool result holds a `learned:` line. `subagent_edits` holds no text that names the archive behaviour. The final reply holds a `learned:` line. |

If a fixture's surprise turns out not to surprise the baseline agent (it avoids the trap in more than 2 of 5 runs), replace the trap with another real behaviour of a common tool, and record which in the README.

- [ ] **Step 4: Run the baseline**

Run each of the four scenarios 5 times on `sonnet` and 5 times on `haiku`, at the commit before Task 2 (record its hash in the README). Run up to 10 at a time. Then `python3 summarize.py <out-dir>`.
Expected: `surprise`, `shared`, and `subagent` fail in most runs. If `surprise` passes in 4 or more of 5 runs on both models, stop and report: the skill has no failure to fix (`[TS-1]`).

- [ ] **Step 5: Write the README and commit**

The README states the purpose, the run command, the scenario contract, the baseline commit, and the baseline table from `summarize.py`. Commit the summary table in the README, not the transcripts.

```bash
git add tools/capturing-learnings-eval
git commit -m "test: add the capturing-learnings scenario harness and baseline"
```

### Task 2: The skill, its routing row, and the extra scenarios

**Files:**
- Create: `skills/capturing-learnings/SKILL.md`
- Modify: `skills/using-lanthorn/SKILL.md` (one row after "Debug a failure"; add `capturing-learnings` to the list of additions in the Rule section)
- Modify: `README.md` (one row in the skill table)
- Create: scenarios `shared-noinstr`, `answer-yes`, `answer-no`, `stale`, `landing` in `tools/capturing-learnings-eval/scenarios/`
- Modify: `tools/capturing-learnings-eval/README.md`

**Interfaces:**
- Consumes: `run.sh`, `lib.py`, and `summarize.py` from Task 1.
- Produces: the skill id `lanthorn:capturing-learnings`, and the report line formats `learned: <fact> → <destination>` and `proposed: <fact> → <file>, waiting for your yes (kept in memory meanwhile)`.

- [ ] **Step 1: Write the Review Focus scenarios**

| Scenario | Fixture and prompt | `check.py` passes when |
|---|---|---|
| `shared-noinstr` | The `shared` fixture without `AGENTS.md`. | A `proposed:` line. No `AGENTS.md` or `CLAUDE.md` exists. `memory/` names `TZ=UTC`. |
| `answer-no` | `shared`, then a second turn: "No, keep it out of AGENTS.md." | `AGENTS.md` unchanged. `memory/` names `TZ=UTC`. |
| `answer-yes` | `shared`, then a second turn: "Yes, add it." | `AGENTS.md` names `TZ=UTC`. `memory/` does not. |
| `stale` | The `surprise` fixture, plus a comment above the export call that claims `git archive <commit>:<path>` works from any directory. | That comment no longer makes the claim. A `learned:` line. |
| `landing` | A fixture with a local bare remote whose `pre-receive` hook rejects a push without a `Signed-off-by` line. Prompt: "The full suite passed. Commit the staged change and push it to origin main." | No tracked file changed apart from the commit. `memory/` names the sign-off rule. A `learned:` line. |

- [ ] **Step 2: Run them at the baseline commit**

Run each 2 times on `sonnet`. Expected: most fail. Record the result in the README.

- [ ] **Step 3: Write `skills/capturing-learnings/SKILL.md`**

Frontmatter `name: capturing-learnings`. A description that meets `[SF-1]`; the spec's illustrative description is a valid start. The body implements `[SF-3]`, `[RL-1]` to `[RL-15]`, and `[SL-1]` to `[SL-4]`, with the spec's Definitions. The Overview names the gap of `[SF-3]`. Use the spec's two report lines as the example. Put the destination order and the mixed rule near the top, so they survive a compaction.

- [ ] **Step 4: Add the routing row and the README row**

Routing row, directly after "Debug a failure", 12 words or fewer:

```markdown
| A tool surprised you, or a task ends | `lanthorn:capturing-learnings` |
```

Run `wc -w skills/using-lanthorn/SKILL.md`. Expected: under 500. Run `claude plugin details lanthorn`. Expected: `capturing-learnings` on-invoke under 4.5k.

- [ ] **Step 5: Run all nine scenarios at the working commit**

Commit the skill first on the branch, so that `run.sh` can pin it. Run the four spec scenarios 5 times on `sonnet` and on `haiku`, and the five Review Focus scenarios 2 times on each.
Expected, per `[TS-3]`: `surprise` and `subagent` pass in 4 or more of 5 per model, `noise` in 4 or more of 5, `shared` in 5 of 5. Each Review Focus scenario passes in 2 of 2.

- [ ] **Step 6: Close loopholes**

For each failing run, read `final.txt` and the transcript, name the rationalisation, and change the skill text that allowed it. Rerun only the failing scenario, 5 times per model. Repeat until Step 5's criteria hold. Rerun a changed wording before you keep it: one round of 5 can mislead.

- [ ] **Step 7: Validate and commit**

Run `claude plugin validate . --strict` and `scripts/sync-version.sh`. Expected: both pass. Add the GREEN table to the eval README.

```bash
git add skills/capturing-learnings skills/using-lanthorn/SKILL.md README.md tools/capturing-learnings-eval
git commit -m "feat: add capturing-learnings, which records how a tool really behaves"
```

### Task 3: Confirmation on Opus

**Files:**
- Modify: `tools/capturing-learnings-eval/README.md`

**Interfaces:**
- Consumes: the final skill commit from Task 2.

- [ ] **Step 1: Run the four spec scenarios on Opus**

Run each 3 times on `opus` at the final commit. Expected: `surprise` and `shared` pass in 3 of 3, `noise` and `subagent` in 2 or more of 3. A failure goes back to Task 2 Step 6.

- [ ] **Step 2: Record and commit**

Add the Opus table to the eval README.

```bash
git add tools/capturing-learnings-eval/README.md
git commit -m "test: confirm capturing-learnings on Opus"
```
