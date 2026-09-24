---
name: writing-less-code
description: Use when deciding how much to build – before adding an interface, base class, factory, registry, config option, dependency, or cache; when a request mentions a feature coming "later" or asks to "plan for" or "make it extensible"; when a request leaves a parameter such as a TTL, limit, or retry count unstated; and when reviewing a diff for over-engineering, dead flexibility, or unused config.
---

# Writing Less Code

## Overview

Use this skill when you decide how much to build, while you write code or review
a diff.

Core principle: **build what the request needs now. A future requirement is
served by code that is easy to change, not by code that anticipates the
change.**

## The ladder

Understand the problem first. Read the code the change touches and trace the
real flow. Then take the first rung that holds.

1. **Does this need to exist?** A speculative need gets one line in your reply, not code.
2. **Does this codebase already have it?** Reuse the helper, type, or pattern.
3. **Does the standard library have it?** Use it.
4. **Does the platform have it?** A DB constraint, `<input type="date">`, CSS, `Intl`.
5. **Does an installed dependency have it?** Use it. Do not add a new dependency for a few lines.
6. **Only then** write the minimum code that works.

Two rungs work? Take the higher one. Two stdlib options of the same size? Take
the one that is correct on edge cases.

## "Later" is not a requirement

A request that says "we will add X later, plan for it" asks for code that is
easy to extend. It does not ask for the extension point.

| Request says | Build | Do not build |
|---|---|---|
| "PDF receipts now, HTML later" | A `render_pdf_receipt(order)` function, called where the receipt is sent | A `ReceiptRenderer` interface, a renderer registry, a format parameter |
| "CSV export now, JSON maybe" | `write_csv(rows, path)` | An `Exporter` base class with one subclass |
| "Make it configurable" with no knob named | The value as a named constant | A config class, a file loader, an env override |

The extension point is cheap to add when the second case arrives. At that
moment you know what the two cases share. Today you are guessing. An interface
with one implementation fixes the guess in place, and the second
implementation must then fit it or change it.

Say what you skipped and when to add it, in one line:

```
skipped: renderer interface. Add it with the second format.
```

## Pick the default, do not stall

When a request leaves a detail open and a sensible default exists, pick the
default, build it, and name it in the reply. Ask only when no default is safe:
a destructive action, money, or security.

<Bad>
```
Question 1: Where must the receipt PDF be stored?
A) local disk  B) S3  C) a storage interface with an in-memory fake (recommended)
```
</Bad>

<Good>
```
Receipt PDF written to RECEIPT_DIR on local disk, then attached to the mail.
skipped: S3, HTML receipts. Tell me if receipts must go to S3.
```
</Good>

## Do not invent requirements

A parameter the user did not state is not a requirement you must satisfy with
new machinery. "Add caching" does not ask for expiry. "Add retries" does not ask
for a policy object.

- Use the stdlib form that matches the literal request. Example:
  `@functools.lru_cache(maxsize=None)` for "cache this".
- If the literal form has a real risk, name it in one line instead of building
  the fix: `lru_cache never expires. Rates go stale until restart. Need expiry?
  Say how long.`
- Build the expiry, eviction, or policy only when the user states it or the
  code already has it elsewhere.

## Mark a deliberate shortcut

When a simplification cuts a real corner, mark it with a `ceiling:` comment. The
comment names the limit and the trigger to upgrade. Use the fixed word so
`grep -rn 'ceiling:'` finds every one.

```python
# ceiling: one global lock. Use per-account locks if throughput matters.
```

## When not to simplify

Never cut:

- Input validation at a trust boundary.
- Error handling that prevents data loss.
- Security measures and accessibility basics.
- Anything the user asked for explicitly. If they insist on the full version,
  build it. Do not argue again.
- Calibration or tuning for physical inputs, such as clocks and sensors.

Never shorten the reading. The ladder shortens the solution, not the
understanding. The smallest diff in the wrong place is a second bug.

## Reviewing for over-engineering

In any review, list over-engineering as findings, not as optional polish. Each
finding is one line: location, tag, what to cut, what replaces it.

| Tag | Meaning |
|---|---|
| `delete:` | Dead code, unused flexibility, speculative feature. Replacement: nothing. |
| `stdlib:` | Hand-rolled code the standard library ships. Name the function. |
| `native:` | Code or dependency doing what the platform does. Name the feature. |
| `yagni:` | Interface with one implementation, factory with one product, config nobody sets. |
| `shrink:` | Same logic, fewer lines. Show the shorter form. |

```
upload.py:L8: delete: compression_level, never read. Remove the field.
upload.py:L15-L40: yagni: StorageBackend ABC and BackendFactory, one product. Call S3Storage directly.
upload.py:L52-L60: stdlib: hand-rolled chunks(). itertools.batched(items, n).
net: -30 lines possible.
```

Unused flexibility is deleted, not completed. A config field that nothing reads
means "remove the field", never "implement the feature so the field is used".

Correctness and security findings still belong in the same review. List them in
their own section.

For a whole repository, apply the same tags and rank the findings, biggest cut
first. Nothing to cut: say `Lean already.`

## Rationalizations

| Excuse | Reality |
|---|---|
| "The interface gives the later cases a place to go." | Later has no code yet. Add the place when the second case arrives and you know its shape. |
| "A choice now can be wrong, so I define an interface only." | An interface with a fake behind it ships nothing. Pick the stdlib option, name it, and move on. |
| "The user said plan for it." | Planning means a small function that is easy to change. It does not mean the abstraction. |
| "`lru_cache` never expires, so I wrote a TTL cache." | Nobody asked for expiry. Use `lru_cache` and name the staleness risk in one line. |
| "The field is unused, so implement what it configures." | Unused config is a deletion finding. Adding the feature grows the diff to justify dead code. |
| "The ABC and factory are harmless. Optional cleanup." | One-product factories are findings. Every reader pays for them. |
| "I'll ask first, to be safe." | A question with a safe default is a stall. Build the default. Ask in the same reply. |

## Red flags

Stop and climb the ladder again if you are about to:

- write an interface, base class, factory, or registry with one implementation;
- add a config field, env variable, or constant that the request did not name;
- write a cache, retry, or validation class where a stdlib call exists;
- ask the user to choose between options when one is a safe default;
- mark an unused abstraction as "optional" in a review.
