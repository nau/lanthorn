# spec-writing micro-tests

2026-11-21. One prompt per run, no tools, Sonnet, the skill text as the system prompt.
`spec-micro-old` uses the text before the docs system, `spec-micro-new` the text after it.

| Prompt | Old text, 3 runs | New text, 3 runs |
|---|---|---|
| 1. Three rules for token refresh | `[TR-1]` to `[TR-3]` in 3 of 3 | Word IDs that match the ID regex in 3 of 3, no numbers |
| 2. A decision record | Prose sections ("## Overview"), no block | `### Decision:` with the six lines in order, 3 of 3 |
| 3. Add a rule to a spec with `[TR-1]` to `[TR-3]` | Adds `[TR-4]` | Keeps `[TR-1]` to `[TR-3]`, adds a word ID, 3 of 3. The area name varied: `token-refresh`, `refresh`, `tr`. |
| 4. Add a rule to a 320-line area file | No split | Names the 300-line limit and keeps every ID, 3 of 3. Two runs propose a split; one asks for the grouping, because the 150 rules are identical placeholders. |
