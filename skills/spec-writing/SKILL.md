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

- One assumption per entry, with a stable ID whose second level is `assume`:
  `[auth.assume.clock-sync]`.
- Name who or what makes it true.
- Cite the ID from each rule or "Why" note that depends on it.

> The clocks of all auth service instances agree within 1 second. The
> deployment platform runs NTP on each instance. `[auth.assume.clock-sync]`

**Why:** An auditor must know which guarantees come from the system and which
come from its environment. An assumption stated in passing gets no owner, and
nobody checks it.

## Load-bearing detail only

A spec states what must stay true. A detail earns its place only if it is
load-bearing: change the detail, and an implementer builds something different
or a rule gives a different result.

A detail that is not load-bearing goes stale, because its owner changes it and
nobody edits the spec. Leave it with its owner. Write what the rules need
instead:

| Detail in the source | Its owner | Write in the spec |
|---|---|---|
| File, line, or function of the current code | The code | Nothing. The code cites the rule ID. |
| Version of a library, a database, or a service | The build files | The product without its version, if the source fixes the product: "Postgres" |
| A measurement of today: row count, request rate, latency | The dashboard | A limit, only if a rule enforces one |
| A value that a config or another document defines | That config or document | Its name: "the lifetime of the access token" |
| A count that is true today: "three instances" | The deployment | The property: "more than one instance" |
| Person, team, ticket, meeting, date | The tracker and the git history | Nothing |
| How the team got here | The commit message | The constraint that resulted, not the story: "Postgres is the only store" |

A value that a rule enforces is load-bearing. Keep it, with its unit.

Name what you left out in your reply, in one line, so that the author can put
a detail back:

```
left out: code location, library versions, traffic figures, owner and ticket.
```

**Why:** One stale line tells the reader that the spec is out of date. A reader
who doubts one line then doubts the rules next to it.

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
> past. `[auth.exp]`
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

- The auth service MUST reject an access token whose `exp` claim is in the past. `[auth.exp]`
- The auth service MUST issue a new refresh token on every refresh. `[auth.rotate]`
- The auth service MUST revoke the presented refresh token before it returns
  the new one. `[auth.revoke-before-return]`
- The auth service MUST revoke the whole token family when a client presents an
  already revoked refresh token. `[auth.family-revoke]`

**Why:** `[auth.rotate]` and `[auth.revoke-before-return]` together make a stolen refresh token single-use.
`[auth.family-revoke]` turns the replay of a stolen token into a detected theft rather than a
rejected request. Rotation without revocation leaves both tokens valid, so the
theft stays invisible.
</Good>

The bad version packs three rules into one sentence, hides one in a parenthesis,
and drops the actor from all three. It uses a lowercase "must" for a normative
rule. It never says that revocation completes before the new token is returned,
and `[auth.family-revoke]` is missing entirely, so an implementer builds rotation that cannot
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

- Give every rule a stable ID: a short name in brackets that ends the rule
  sentence. Example: `[auth.revoke-before-return]`.
- The first level names the area, which is also the stem of its file: `auth`
  lives in `auth.md`. A spec with no areas uses its own short name.
- Use only `a-z`, `0-9`, `-`, and `.`. A dot separates levels. A hyphen joins
  words inside a level.
- Each level starts with a letter, with no leading, trailing, or doubled
  hyphen. An ID has 2 to 4 levels and at most 40 characters. A valid ID
  matches `\[[a-z][a-z0-9]*(-[a-z0-9]+)*(\.[a-z][a-z0-9]*(-[a-z0-9]+)*){1,3}\]`.
- Put an *Implementation status* or *Withdrawn* note on its own line under the
  rule, so that the ID stays the last token of the rule.
- Never start a line with an ID. `[id]:` at a line start becomes a hidden
  Markdown link definition.
- Cite the ID from the code and the tests: `// spec [auth.revoke-before-return]`.
- Search for an ID with a fixed string, single-quoted, with a path:
  `rg -nF '[auth.revoke-before-return]' .`
- Record any divergence from the implementation in an *Implementation status*
  note on that rule. A rule that the code does not build yet carries
  *Implementation status: planned*.

| ID | Valid | Why |
|---|---|---|
| `[auth.reuse-grace]` | Yes | |
| `[auth.tokens.reuse-grace]` | Yes | Three levels |
| `[AUTH.reuse-grace]` | No | Capitals |
| `[auth:reuse-grace]` | No | A colon |
| `[auth.reuse_grace]` | No | An underscore |
| `[reuse-grace]` | No | One level |

**Why:** a name tells the reader what the rule is about, and parallel branches
rarely pick the same name, where they always pick the same next number.
Without `-F`, grep reads the brackets as a character class and matches
unrelated lines. Git reads a bare `auth:reuse-grace` line at the end of a
commit message as a trailer, so a colon is excluded. Lowercase avoids a case
clash on a case-insensitive filesystem.

**An ID is frozen from the moment it is published.** Before you rename, remove,
or add a rule in a spec that already has IDs, read `references/id-lifecycle.md`.
A spec with numbered IDs, such as `[TR-3]`, keeps them, and its new rules take
word IDs.

## Structure

Keep one template per repeated kind of section, and fill every field every time.

For a design doc, in this order, after a header that holds one key, `area`, which
names each area that the doc touches, or `none`:

1. **Overview** - *informative.* The main flow as a story, in at most 200 words.
2. **Goals and non-goals** - *informative.* The problem, why it matters now,
   and the work that the spec leaves out.
3. **Approaches** - *informative.* Only if the source weighed more than one way
   to build it. One decision block per decision, as below.
4. **Assumptions** - each fact that the spec relies on and does not enforce,
   with its `[<area>.assume.<name>]` ID.
5. **Definitions** - one entry per domain term.
6. One section per operation, endpoint, or transaction.
7. **Open questions** - *informative.* Each point that the source left
   undecided. For each one, cite the rule that decides it, or say that it is
   still open.

For an operation, an endpoint, or a transaction:

1. **Overview** - *informative.* What it achieves and how the flow runs, in 2 to
   4 sentences.
2. **Who** - the actor that invokes it.
3. **Trigger** - the condition that makes it valid to run.
4. **Structure** - a table of inputs, outputs, and their types.
5. **Checks enforced here** - the ID-tagged rules this component runs.
6. **Checks delegated elsewhere** - only if any exist. Name who enforces them.

An empty field is information. Write "None" rather than dropping the heading.

A decision block records one decision, in the form of an ADR. It starts with
`### Decision:` and a title that states the chosen option. Its lines come in
this order:

```markdown
### Decision: Accept a reused refresh token for 10 seconds

Status: Accepted
Area: auth
Context: Mobile clients retry on a bad network, and the retry revokes the family.
Options:
- A 10-second grace period: no client change. A stolen token works for 10 seconds.
- A per-client retry token: exact. Every client must change.
Chosen: The grace period, because the clients ship monthly.
Consequences: Changes [auth.family-revoke]. Adds [auth.reuse-grace].
```

A decision that replaces an earlier one adds a `Replaces:` line with the path
and anchor of the earlier block. Find decisions with
`rg -n -A2 '^### Decision:' <design docs folder>`.

A living spec file, one per area, holds less than a design doc:

| Part | Design doc | Area file |
|---|---|---|
| Header `area:` | Yes | No |
| Overview, the story | Of the change | Of the area as it works now, at most 200 words |
| Goals, non-goals, Approaches, Open questions | Yes | No |
| Assumptions, Definitions | Yes | Yes |
| Rules with IDs and "Why" notes | Links them by ID | Yes, each "Why" links its decision block |
| Operation template (Who, Trigger, Structure) | Yes | Only for a public interface, such as an endpoint |

When an area file passes 300 lines, split it into files of sub-areas. Each
moved rule keeps its ID. `lanthorn:keeping-project-docs` says where each file
lives.

**Why:** A reader who learns the template once can then find any field in any
section without reading the section. A missing heading looks like an oversight,
so the reader goes hunting for a rule that does not exist.

## Common mistakes

Before you hand the spec over, read `references/checklist.md`. Each row is a
self-check. If you can find one in your draft, fix it.

**Where the spec and an implementation disagree, the spec wins.** Changing the
spec to match the code turns a bug into a requirement, silently. Edit the spec
to match the code only as a recorded decision, with an *Implementation status*
note.
