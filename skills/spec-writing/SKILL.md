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
- Use RFC 2119 keywords in CAPITALS for normative force: MUST, MUST NOT, SHOULD,
  SHOULD NOT, MAY. Lowercase "must" is descriptive prose only.
- Never hide a rule inside a parenthesis or an aside. Give it its own bullet.
- Put rationale in a separate "Why" note. Never delete the rationale. The
  rationale is what lets a reader handle a case the spec did not list.

<Bad>
The token is checked for expiry and (if a refresh token is present) also
rotated, and the old token must be revoked.
</Bad>

<Good>
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
detect theft.

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
- Never renumber an ID. Mark a removed rule as withdrawn and keep its number.
- Record any divergence from the implementation in an *Implementation status*
  note on that rule.

**Why:** An auditor traces a finding to a rule. An implementer traces a rule to
code. A renumbered ID breaks both links silently, and no test catches it.

## Structure

Keep one template per repeated kind of section, and fill every field every time.
For an operation, an endpoint, or a transaction:

1. **Purpose** - what it achieves, in one sentence.
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
| Rationale deleted as redundant | Restore it. It handles the unlisted case. |
| Renumbering after a deletion | Mark the old ID withdrawn. Keep the gap. |
| A new synonym for an existing term | Repeat the defined term. |
| A 5-word noun cluster | Rewrite with "of the". Maximum 3 words. |
| A sentence longer than 25 words | Split it. One rule per sentence. |
| "etc." or "and so on" in a normative list | List every case, or state the rule that generates them. |
| The spec edited to match the code | Only as a recorded decision. Add an *Implementation status* note. |

**Where the spec and an implementation disagree, the spec wins.** The last row is
the one that costs the most. Changing the spec to match the code turns a bug into
a requirement, silently.
