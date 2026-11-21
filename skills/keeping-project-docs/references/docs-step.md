# The docs step of a landing

Part of `lanthorn:finishing-a-development-branch`. Run it after the rebase and
before the full suite, on every landing, also one with no design doc. Its
changes are part of the landing, so they fold into the feature's commits, and
the suite then tests a tree that holds them.

Take every path from the map. The commands below use the default map.

## No map

If the instruction file has no `## Docs` section, do nothing but propose a map
in the landing summary, per the skill's "No map yet". Show the full text of the
proposed map there, with each folder that the project already uses. Skip the
steps below.

## Steps

1. **Check the rule IDs.** Two rules must never share an ID. List the IDs that
   end a rule line, and fail the landing on a duplicate:

   ```sh
   # IDs that end a line of a rule bullet, in the living spec folder of the map
   awk '/^- /{item=1} /^$/{item=0} /^[^- ]/{if ($0 !~ /^- /) item=0}
        item && match($0, /\[[a-z][a-z0-9.-]*\]`?[ \t]*$/) {s=substr($0,RSTART,RLENGTH); gsub(/[` \t]/,"",s); print s}' \
       docs/spec/*.md | sort | uniq -d
   ```

   It counts a withdrawn rule too, so a reused withdrawn ID shows up. Then
   check each ID that the branch adds against this pattern, which also rejects
   a doubled hyphen:

   ```sh
   grep -E '^\[[a-z][a-z0-9]*(-[a-z0-9]+)*(\.[a-z][a-z0-9]*(-[a-z0-9]+)*){1,3}\]$'
   ```

   A duplicate or a bad ID stops the landing: report it and ask.

2. **List the rule changes.** From `git diff <newest base>... -- docs/spec/`,
   collect each ID that the branch adds, changes, or withdraws, and each rule of
   the branch that is still planned. They go into the summary lines "Rule
   changes" and "Planned rules".

3. **Update the architecture document** if the branch adds or removes a
   component, changes how two components interact, changes an invariant, adds
   an area, or records a decision that binds later work. Compare the paths that
   the branch changes with the code map. A moved or new top-level folder of
   code is a component change. Otherwise, do not touch it. If the map names a
   section of the instruction file as the architecture document, edit that
   section, and create no `ARCHITECTURE.md`.

4. **Mark replaced decisions.** For each `Replaces:` line that the branch adds,
   set the status line of the replaced block to
   `Status: Superseded by <path>#<anchor>`. Change nothing else in that design
   doc.

5. **Delete the plans.** Remove each file that the branch adds to the plans
   folder. The history rewrite then drops it from the landed history, per
   `references/rewriting-history.md` of the finish skill.

6. **Commit.** Commit the changes of this step as one working commit, before
   the full suite. The history cleanup then folds it into the feature's
   commits, or drops it with a deleted plan.
7. **Report.** The summary line "Docs" lists what steps 3 to 5 changed: the
   architecture document, each superseded decision, each deleted plan.

## Conflicts in an area file

The rebase may stop in an area file.

- **Each side only adds rules with different IDs:** keep both, with no
  question. Git reports two appends at the end of one file as a conflict, yet
  keeping both changes no rule.
- **Both sides change the same rule ID:** this is a conflict that changes
  behaviour. Stop and ask. Two branches that change one rule is a design
  question, and only your human partner can settle it. Name the rule ID and
  both texts in the question.
