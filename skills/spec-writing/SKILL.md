---
name: spec-writing
description: Use when writing or editing a technical specification, protocol document, security design review, or any document that states normative requirements for auditors, independent implementers, and AI agents.
---

# Spec Writing

## Overview

A spec has three readers at once: a human auditor, an independent implementer who
cannot ask you questions, and an AI agent. Write for all three. The rules below
come from ASD-STE100 Simplified Technical English, without its restricted
dictionary. Keep exact domain terms. Precision beats simple words.

Core principle: **one requirement, one sentence, one named actor, one stable ID.**

Second principle: **story before rules.** A reader needs a model of the system
before a list of exact rules.

## When to use

Use this skill when you:

- write or edit a protocol specification or technical design document
- add a requirement to an existing spec
- review a spec for ambiguity before an audit
- write a security design review

Do not use it for README files, tutorials, or commit messages. Those are prose.

## Requirements

- One requirement per sentence. Do not put two rules in one sentence.
- Name the actor. Write the module or component that does the work.
- Use the BCP 14 keywords in CAPITALS for normative force: MUST, MUST NOT,
  REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY, OPTIONAL.
- Capitals are the signal. A lowercase "must" is ordinary prose and binds
  nothing, so you can still write one without creating a requirement.
- Never hide a rule inside a parenthesis or an aside. Give it its own bullet.
- Put rationale in a separate "Why" note. Never delete the rationale. The
  rationale is what lets a reader handle a case the spec did not list.
- Give an example for any rule that is not trivial. See below for the test.

## Story before rules

A list of exact rules is hard to read without a model of the system. Give the
reader that model first. A spec has two layers:

| Layer | Says | Form | Binds |
|---|---|---|---|
| Informative | How the system works, and why it exists | Paragraphs, in time order | Nothing |
| Normative | What each actor MUST do | One rule per bullet, with an ID | The implementation |

The informative layer is the Overview of the document, its goals and non-goals,
and the Overview of each operation. Write it like this:

- Label it *Informative*.
- Tell the flow in time order. Start with the normal case. Then say what happens
  on failure.
- Write paragraphs. Connect the sentences with "then", "so", and "because".
- State behaviour in the present tense: "the auth service revokes the family".
  The present tense describes the design. A keyword in capitals states a rule,
  and rules live in the normative layer.
- Use the defined terms. The limit of 25 words per sentence still applies.
- Keep it short: at most 200 words for the document, 2 to 4 sentences for an
  operation.

Every behaviour of a specified component that the informative layer describes
MUST also appear as a numbered rule. Where the two layers disagree, the rule
wins.

Order is behaviour. When the story says "then" or "before" and the order
matters, state that order in a numbered rule too.

A non-goal names work that the spec leaves out. Write each one down, so that no
reader builds it.

**Why:** A reviewer checks the design against the story, and the code against
the rules. An agent uses the story to handle a case that no rule lists. A spec
with rules only gives the reader 40 exact sentences and nothing to attach them
to.

## Assumptions

An assumption is a fact that the spec relies on and does not enforce. It is not
informative text. If it is false, the rules no longer give the result that the
spec promises.

- One assumption per entry, with a stable ID in its own series: `[A-1]`.
- Name who or what makes it true.
- Cite the ID from each rule or "Why" note that depends on it.

> The clocks of all auth service instances agree within 1 second. The
> deployment platform runs NTP on each instance. `[A-1]`

**Why:** An auditor must know which guarantees come from the system and which
come from its environment. An assumption stated in passing gets no owner, and
nobody checks it.

## Examples

A rule states the law. An example proves you and the reader read it the same way.
Most disagreements between a spec and an implementation are not disputes about
the rule; they are two honest readings of one sentence.

**Give an example when any of these is true:**

| Signal | Why prose alone fails |
|---|---|
| A boundary is involved | "in the past" does not say what happens at exactly now |
| Encoding, ordering, or format matters | Byte order and field order are invisible in prose |
| The sentence has two defensible readings | The example picks one |
| The rule interacts with another rule | Prose describes each rule alone |
| A concrete value, unit, or limit appears | "10 per minute" needs a window definition |

**Skip the example when** the rule restates a type (`amount` is a non-negative
integer), or when only one reading is possible. An example for a trivial rule is
noise, and noise trains readers to skip examples that matter.

Worked case. The rule is trivial-looking and is not:

> The auth service MUST reject an access token whose `exp` claim is in the
> past. `[TR-1]`
>
> *Example, illustrative.* With `exp = 1700000000`:
>
> | Server time | Result |
> |---|---|
> | 1699999999 | accept |
> | 1700000000 | accept, `exp` is not yet past |
> | 1700000001 | reject |

Two implementers read "in the past" as `<` and `<=`. The table costs three lines
and removes a class of bug that only shows up under clock skew.

**Mark every example illustrative or normative.**

- *Illustrative* is the default. If it disagrees with the rule, the rule wins.
- *Normative* means conformance depends on it. Test vectors, wire formats, and
  reference encodings are normative. Say so, and say which rule each one tests.

An example MUST NOT be the only place a rule appears. If the example shows
behaviour the prose never states, move that behaviour into a numbered rule. That
is the same failure as hiding a rule in a parenthesis.

## How formal to be

All three kinds use the CAPITALS convention. They differ only in how much they
say about it.

| Kind of spec | Audience | Declare the keywords? |
|---|---|---|
| Development spec, design doc | Your team and its agents | No. The capitals carry it. |
| Protocol or interface spec | Implementers and auditors who cannot ask you | One line: "Capitals are normative, per BCP 14." |
| Standards-track document | An RFC series or equivalent | Yes, the series' exact boilerplate. Copy it from their template. |

Most specs are the first row. A reader who can ask you a question does not need
the clause, so do not spend a paragraph on it.

<Bad>
The token is checked for expiry and (if a refresh token is present) also
rotated, and the old token must be revoked.
</Bad>

<Good>
*Informative.* A client trades a refresh token for a new access token and a new
refresh token. Each refresh token works once, because the auth service revokes
it during the trade. A second use of a refresh token means that someone copied
it, so the auth service revokes every token in that family.

- The auth service MUST reject an access token whose `exp` claim is in the past. `[TR-1]`
- The auth service MUST issue a new refresh token on every refresh. `[TR-2]`
- The auth service MUST revoke the presented refresh token before it returns
  the new one. `[TR-3]`
- The auth service MUST revoke the whole token family when a client presents an
  already revoked refresh token. `[TR-4]`

**Why:** `[TR-2]` and `[TR-3]` together make a stolen refresh token single-use.
`[TR-4]` turns the replay of a stolen token into a detected theft rather than a
rejected request. Rotation without revocation leaves both tokens valid, so the
theft stays invisible.
</Good>

The bad version packs three rules into one sentence, hides one in a parenthesis,
and drops the actor from all three. It uses a lowercase "must" for a normative
rule. It never says that revocation completes before the new token is returned,
and `[TR-4]` is missing entirely, so an implementer builds rotation that cannot
detect theft. The good version also opens with the story, so the reader knows
what the four rules achieve before the first one.

## Terminology

- One term for one thing, every time. Define it once in a Definitions section.
- Do not invent a synonym for variety. Repeat the term.
- Fix the spelling and the casing of every domain term in the Definitions
  section, then never vary it. Pick one of `refresh token` / `refresh-token`,
  one of `txid` / `txId` / `transaction ID`, and hold to it everywhere.
- Maximum 3 words in a noun cluster. Write "expiry of the refresh token", not
  "refresh token expiry deadline check".

## Sentences

- Target 20 words for a rule, 25 for an explanation. Split anything longer.
- Active voice. Simple tenses.
- Standard English typography and punctuation. No house style for dashes or
  quotes.

## Traceability

- Give every normative check a stable ID: a short prefix for the operation, then
  a number. Example: `[TR-3]` is the third check of "Token refresh".
- Cite the ID from the implementation and from the tests: `// spec [TR-3]`.
- Record any divergence from the implementation in an *Implementation status*
  note on that rule.

**An ID is frozen from the moment it is published.** An ID is *published* when
it exists on the main branch, in a released version of the spec, or in a copy
that someone outside the team holds. Until then it is a *draft* ID. The state
belongs to each ID, not to the document. A branch that adds two rules to a
published spec has two draft IDs next to the published ones.

| Change | Draft ID | Published ID |
|---|---|---|
| Renumber or reorder | Yes. Keep the numbers in reading order. | Never. |
| Remove the rule | Delete it. Leave no trace. | Mark it withdrawn. Keep its number. |
| Add a rule | Renumber the draft IDs, so that they stay in reading order. | Give the new rule the next free number. |

- Draft IDs take the numbers after the highest published number of the series.
  A withdrawn number counts as published.
- When you renumber a draft ID, update each citation in the same change. Search
  the plan, the code, and the tests for the old ID.
- To find the state of an ID, compare the spec with its version on the main
  branch: `git diff main -- <spec file>`. An ID that the main branch already
  holds is published.
- If you cannot find the state of an ID, treat it as published.

**Why:** An auditor traces a finding to a rule. An implementer traces a rule to
code. A renumbered published ID breaks both links silently, and no test catches
it. A draft ID has no reader outside the branch. A gap or a "withdrawn" marker
there records a history that nobody needs.

## Structure

Keep one template per repeated kind of section, and fill every field every time.

For the document, in this order:

1. **Overview** - *informative.* The main flow as a story, in at most 200 words.
2. **Goals and non-goals** - *informative.* The problem, why it matters now,
   and the work that the spec leaves out.
3. **Assumptions** - each fact that the spec relies on and does not enforce,
   with its `[A-n]` ID.
4. **Definitions** - one entry per domain term.
5. One section per operation, endpoint, or transaction.
6. **Open questions** - *informative.* Each point that the source left
   undecided. For each one, cite the rule that decides it, or say that it is
   still open.

For an operation, an endpoint, or a transaction:

1. **Overview** - *informative.* What it achieves and how the flow runs, in 2 to
   4 sentences.
2. **Who** - the actor that invokes it.
3. **Trigger** - the condition that makes it valid to run.
4. **Structure** - a table of inputs, outputs, and their types.
5. **Checks enforced here** - the numbered, ID-tagged rules this component runs.
6. **Checks delegated elsewhere** - only if any exist. Name who enforces them.

An empty field is information. Write "None" rather than dropping the heading.

**Why:** A reader who learns the template once can then find any field in any
section without reading the section. A missing heading looks like an oversight,
so the reader goes hunting for a rule that does not exist.

## Common mistakes

Each row is also a self-check. If you can find one in your draft, fix it before
you hand the spec over.

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

**Where the spec and an implementation disagree, the spec wins.** The last row is
the one that costs the most. Changing the spec to match the code turns a bug into
a requirement, silently.
