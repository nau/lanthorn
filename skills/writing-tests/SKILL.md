---
name: writing-tests
description: Use when writing or changing a test, when the code under test is private, internal, unexported, or underscore-prefixed, when a test cannot reach a branch through the public interface, or when someone proposes making a function public, adding InternalsVisibleTo or @testable, or using reflection so a test can reach it.
---

# Writing Tests

## Overview

`superpowers:test-driven-development` says when to write a test and how the
red-green-refactor cycle runs. Its `writing-good-tests.md` says what a test
asserts. Neither says which boundary a test targets, or what to do when the
function you want to test is private. This skill adds that decision. It is an
addition, not a fork. It runs alongside TDD, never instead of it.

**REQUIRED SUB-SKILL:** `superpowers:test-driven-development`. Read its
`writing-good-tests.md` before you write or change any test.

Core principle: **a test targets the public interface of the unit under test.
A private function is an implementation detail. If it deserves its own tests,
it deserves to be its own unit.**

## When to use

- You are about to write or change a test.
- The function you want to test is private, internal, unexported, or has a
  leading underscore.
- A test cannot reach a branch through the public interface.
- Someone proposes to make a function public, drop an underscore, add
  `InternalsVisibleTo` or `@testable import`, or use reflection "for the tests".

## "Public" means the unit, not the library

Two different boundaries hide behind one word.

| Word | Meaning | Test target? |
|---|---|---|
| **Public** | Visible to other code in the same codebase. A module's exported function. | Yes |
| **Published** | Shipped to external consumers. The library's documented surface. | Yes, but not the only one |

A test may target anything public. It needs no published API. An internal
module's exported function is a legitimate test target even if the package never
ships it. Most arguments about "only test the public API" end here: the API in
question is the unit's, not the product's.

## Choose the boundary

Take the first row that matches.

| The function you want to test | Do this |
|---|---|
| Is exported from its module | Test it directly. |
| Is private, and the unit's exported function reaches every case you need with a small helper | Test through the exported function. A wrapped error still carries its cause. Assert on the cause. |
| Is private, and reaching it through the exported function needs fixtures the function itself never touches: files, sockets, clocks, a full config | Extract it into its own module. Export it there. Test that module. |
| Is private, and covering it needs a table of many inputs | Same. Extract, export, test the table against the new module. |
| Already sits at a level a test in the same module or package reaches (table below) | Test it from there. Do not change the declaration. |
| Is legacy code nobody understands yet | Write a characterization test. Say so in the test name. Reach in if you must. |

Rows three and four are the design signal. The urge to test a private function
directly means the enclosing unit does two jobs. Extraction is the fix, not a
workaround. Keep one test through the original entry point afterwards. It proves
the wiring and the error wrapping, which the extracted module cannot.

Extraction is a refactor. Do it inside the TDD refactor step: existing tests
green before, green after, then write the new tests against the new module.

## Never widen visibility for a test

Making a function public because a test wants it puts it on the supported
surface. Every caller may now depend on it. You cannot make it private again
without a breaking change.

Importing the private name from a test is the same coupling, minus the honest
declaration. The tests now break on every rename, move, or inlining of the
helper, and they pass while the composition is broken.

Use the access a test in the same module or package already has.

| Language | What a same-module test reaches |
|---|---|
| Rust | `#[cfg(test)] mod tests { use super::*; }` in the same file. A child module sees the parent's private items. |
| Go | A `_test.go` file in the same package (`package foo`, not `package foo_test`) sees unexported names. |
| Java | Package-private, no modifier. Test class in the same package, different source root. |
| Kotlin | `internal`. The test source set is in the same module. |
| Scala | `private[pkg]`. Test in the same package. |
| C# | `[assembly: InternalsVisibleTo("Project.Tests")]` on the production assembly. |
| Swift | `@testable import Module` reaches `internal`, not `private`. |
| TypeScript, JavaScript, Python, Elixir | None. Module scope is the boundary. Extract. |

Only the Rust and Go rows reach an item declared `private`. Every other row
names a level the declaration must already have. Changing `private` to
`private[pkg]`, `internal`, or package-private so a test can reach it is
widening, and the row above does not apply. Extract instead.

Python enforces nothing. A leading underscore is a promise, not a lock. The rule
does not change: the underscore marks exactly what a test must not depend on.

## Example

A scheduler reads a job config file, parses its `delay`, and enqueues a job.
The parser has four error branches and six accepted forms. The entry point wraps
every parser error in a generic `ConfigError`.

<Bad>
```python
# scheduler.py
def _parse_duration(text: str) -> int: ...          # 4 error branches

def load_and_schedule(path, queue, clock) -> Job:
    config = _read(path)                            # file I/O
    try:
        delay = _parse_duration(config["delay"])
    except ValueError as exc:
        raise ConfigError(f"{path}: invalid config") from exc
    ...

# test_scheduler.py
from scheduler import _parse_duration               # reaches into the module

def test_rejects_negative(self):
    with self.assertRaises(ValueError):
        _parse_duration("-5m")
```
</Bad>

The parser is pure. The test needs a temp file per case to reach it through
`load_and_schedule`, so the test reaches in instead. That is row three: fixtures
the function never touches. Extract.

<Good>
```python
# duration.py                                       # new module
def parse(text: str) -> int: ...                    # moved unchanged, now public

# scheduler.py
import duration
        delay = duration.parse(config["delay"])

# test_duration.py
from duration import parse

class ParseTest(unittest.TestCase):
    def test_accepts_hours_minutes_seconds(self):
        cases = [("1h30m", 5400), ("90m", 5400), ("1h 30m", 5400), ("0s", 0)]
        for text, seconds in cases:
            with self.subTest(text=text):
                self.assertEqual(parse(text), seconds)

    def test_rejects_malformed_text(self):
        for text in ["", "-5m", "5x", "1m1h"]:
            with self.subTest(text=text):
                with self.assertRaises(ValueError):
                    parse(text)

# test_scheduler.py keeps one test with "delay": "5x" through load_and_schedule.
# It asserts ConfigError and that __cause__ is a ValueError. It proves the
# wiring. It does not repeat the table.
```
</Good>

`parse` is now the public interface of a small unit. Nothing outside `scheduler`
gained access it did not already have. The scheduler's own tests shrank to what
the scheduler does: read, wrap, enqueue.

## Common mistakes

Each row is a self-check. Find one in your draft, fix it before you run.

| Mistake | Fix |
|---|---|
| `from mod import _helper` in a test | Reach the case through the exported function, or extract the helper. |
| `export` or `public` added with a comment "for tests" | Revert. Use the language table, or extract. |
| `(obj as any).helper`, reflection, or an `export const __test__ = {...}` bag | Same. It is the import mistake in disguise. |
| One test per private helper, each mirroring the helper's body | Keep one test per behaviour of the unit. |
| Every parser case driven through the file reader | Extract the parser. Keep one test through the reader for the wiring. |
| All tests target internals, none crosses the public entry | Keep at least one test through the public entry. It proves the composition. |
| "Only test the public API" read as "only test the published API" | The unit's public surface counts. Test the module. |
| Extraction started while a test is red | Refactor on green only. |

## Rationalizations

| Excuse | Reality |
|---|---|
| "The underscore is a convention. Tests in the same codebase are a normal consumer." | The convention marks what tests must not depend on. It has no other purpose. |
| "Testing through the wrapper tests the wrapper, not the parser." | Then the parser is a unit. Extract it and test it as one. |
| "Nothing else imports it, so there is no reason to widen it." said while importing it from a test | The test import is the coupling. Widening is only the honest version of the same mistake. |
| "I would reach in only if the public entry could not reach a branch." | A branch the public entry cannot reach is the extraction signal, not a licence. |
| "The rename edits production code before a release. The import touches nothing." | The import binds the tests to a private name, which is a constraint on production code that nobody declared. Extraction is a move and an import, done on green. It removes the constraint instead of hiding it. |
