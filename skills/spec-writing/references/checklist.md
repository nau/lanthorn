# Checklist of common mistakes

Each row is a self-check. If you can find one in your draft, fix it before you
hand the spec over.

| Mistake | Fix |
|---------|-----|
| "The token is verified" | Name the actor: "the auth service MUST verify the token". |
| "must" in lowercase for a rule | Use MUST. Lowercase is prose only. |
| Two rules joined by "and" | Split into two bullets, two IDs. |
| A rule inside a parenthesis | Promote it to its own bullet. |
| A rule with no ID | Assign the next number in that operation's series. |
| Rules with no Overview above them | Write the story first: the flow in time order, labelled informative. |
| A keyword in capitals inside an Overview | Move the rule to a numbered bullet. The Overview uses the present tense. |
| Behaviour that appears only in an Overview | Promote it to a numbered rule. |
| An assumption stated in passing | Move it to Assumptions. Give it an `[A-n]` ID and name who makes it true. |
| A path, version, metric, name, or date that no rule depends on | Cut it. Its owner keeps it current. Name it in your reply. |
| A point the source left undecided, settled inside a "Why" note | List it in Open questions. Cite the rule that decides it. |
| Rationale deleted as redundant | Restore it. It handles the unlisted case. |
| Renumbering a published ID after a deletion | Mark the old ID withdrawn. Keep the gap. |
| A gap or a "withdrawn" marker among draft IDs | Renumber the draft IDs in reading order. Update each citation. |
| A new synonym for an existing term | Repeat the defined term. |
| A 5-word noun cluster | Rewrite with "of the". Maximum 3 words. |
| A boundary rule with no example | Add the table. "In the past" hides `<` versus `<=`. |
| An example for a rule with one reading | Cut it. Noise trains readers to skip the examples that matter. |
| An example nobody labelled | Mark it illustrative or normative. |
| Behaviour that appears only in an example | Promote it to a numbered rule. |
| A sentence longer than 25 words | Split it. One rule per sentence. |
| "etc." or "and so on" in a normative list | List every case, or state the rule that generates them. |
| The spec edited to match the code | Only as a recorded decision. Add an *Implementation status* note. |
