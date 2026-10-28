---
name: writing-scala
description: Use when writing, changing, or reviewing Scala code; when choosing a Scala library, such as a JSON codec; when deciding if a Scala function returns Either or Option, or throws; when a Scala project states no code style; when finding the callers or usages of a Scala symbol, or reading a library's source; or when adding a dependency to build.sbt, build.mill, or a scala-cli project.
---

# Writing Scala

## Overview

Use this skill for any Scala work. An agent without it writes reasonable
Scala, but it picks different libraries and error mechanisms from one task to
the next.

Core principle: **the project's own rules win. This skill fills only the gaps
that the project leaves open.**

## Where the rules come from

Read these in order. The first one that states a rule wins.

1. `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md`.
2. `.scalafmt.conf`, and the existing code in the same module.
3. This skill.

Example: the module uses circe everywhere. Keep circe. The JSON table below
applies only when the project has no JSON library.

## Code style when nothing is stated

Use this style only when steps 1 and 2 are silent. A new file in a braceless
project stays braceless.

- Scala 3 syntax.
- Braces for top-level definitions and for multi-line function bodies.
- One-liner or single expression functions/vals may not use braces.
- Indentation for `if`, `match`, `try`, and `for`. Use braces when the block is
  longer than 20 lines.
- `then` in `if`, `do` in `while`.
- never use `end`
- 4-space indent, 100 columns.

```scala
object Pricing {
    def discount(order: Order): Long = {
        if order.items.size > 10 then order.totalCents / 10
        else
            val loyalty = order.customer.years * 50L
            math.min(loyalty, order.totalCents / 20)
    }

    def label(status: Status): String = status match
        case Status.Paid    => "paid"
        case Status.Pending => "waiting"
}
```

## Effects: direct style

Write plain synchronous code by default. A function returns `A`,
`Either[E, A]`, or `Option[A]`.

The project already uses `IO`, `ZIO`, Ox, or `Future`? Use it the way the module
uses it. Do not add an effect library, a `Future`, or an `F[_]` type parameter
that the project does not already have.

## Errors

Pick the mechanism by the situation, not by habit.

| Situation | Mechanism |
|---|---|
| The caller expects it and acts on it: any failed remote call (HTTP error status, timeout, connection refused, DNS failure), bad user input, parse failure, not found, a business rule | `Either[E, A]`, where `E` is an `enum` or sealed trait |
| Unexpected failure that the caller cannot handle: disk full, driver crash | Exception |
| Unrecoverable failure: bad config at startup, missing required resource | Exception. Let it end the process or the request. |
| Bug: a broken invariant or an impossible case | `require`, `assert`, or `IllegalStateException` |
| Failure deep inside a closed module or one function, where threading `Either` adds noise | A private exception inside. Catch it once at the module boundary and return `Either`. |

The error type is never `String`. A caller must be able to `match` on it.

```scala
enum FetchError {
    case HttpStatus(url: String, code: Int, body: String)
    case Timeout(url: String)
    case BadBody(url: String, reason: String)
}

def fetchOrders(page: Int): Either[FetchError, List[Order]] = { ... }
```

Startup config is unrecoverable, so it throws. Do not thread `Either` through
it.

<Bad>
```scala
def loadConfig(path: Path): Either[String, AppConfig] =
  for
    text <- Try(Files.readString(path)).toEither.left.map(e => s"cannot read $path: $e")
    cfg  <- Try(readFromString[AppConfig](text)).toEither.left.map(e => s"bad $path: $e")
  yield cfg
```
</Bad>

<Good>
```scala
/** Throws on a missing or invalid file. main reports it and exits. */
def loadConfig(path: Path): AppConfig = {
    readFromString[AppConfig](Files.readString(path))
}
```
</Good>

A closed module can throw inside and still return `Either` outside:

```scala
object ExprParser {
    private final class Fail(val error: ParseError) extends Exception(null, null, false, false)

    def parse(input: String): Either[ParseError, Expr] = {
        try Right(parseExpr(Cursor(input)))
        catch case f: Fail => Left(f.error)
    }

    private def parseExpr(c: Cursor): Expr = { ... } // throws Fail(ParseError.Unexpected(...))
}
```

Use `Option` for a value that can be absent. Never use `null`. `Option` is not
an error type.

## JSON

The project already has a JSON library? Use it. If not:

| Need | Library |
|---|---|
| Typed codecs: DTOs, HTTP bodies, config, files with a known shape | jsoniter-scala |
| Scripts, debug helpers, or a JSON tree of unknown shape | ujson |

Do not add circe, upickle's typed `ReadWriter`, play-json, spray-json, or
zio-json. An HTTP client that ships an integration module for another library
is not a reason to add that library. sttp has a jsoniter module:
`com.softwaremill.sttp.client4::jsoniter`.

Derive codecs with `derives ConfiguredJsonValueCodec`. Put the configuration in
one `inline given CodecMakerConfig`. It must come before the first `derives` in
the file, or be imported before it.

```scala
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:<version>
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:<version>

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

// DiscriminatorFieldName(None) encodes a parameterless enum case as a plain string.
// The mapper turns the case Paid into the wire value "paid".
inline given CodecMakerConfig = CodecMakerConfig
    .withDiscriminatorFieldName(None)
    .withAdtLeafClassNameMapper(n =>
        JsonCodecMaker.enforce_snake_case(JsonCodecMaker.simpleClassName(n))
    )

enum Status {
    case Pending, Paid, Shipped
}

// @stringified reads a number that the API sends as a string, such as "1250".
final case class Order(id: String, @stringified amountCents: Long, status: Status)
    derives ConfiguredJsonValueCodec

final case class Page(orders: List[Order], next: Option[Int]) derives ConfiguredJsonValueCodec

val order = readFromString[Order](body) // throws JsonReaderException
```

- `derives` needs the macros artifact at runtime. Use a normal `dep`, not
  `compileOnly.dep`. In sbt, do not put it in `Provided` scope.
- Put `derives` only on the types that you read or write at the top level, such
  as `Order` and `Page`. The macro builds the codecs for nested types, such as
  `Status`, inside them.
- A top-level collection needs its own codec. Write
  `given JsonValueCodec[List[Order]] = JsonCodecMaker.make`. It reuses the
  derived `Order` codec.
- `readFromString` throws. At a boundary where the caller expects bad input,
  catch `JsonReaderException` and return `Left`.
- A parse error message includes a hex dump of the input. The input holds a
  secret, such as an API key? Pass
  `ReaderConfig.withAppendHexDumpToParseException(false)`.

## Types and APIs

- Give every public `def`, `val`, and `given` an explicit return type.
- Wrap domain IDs and quantities in opaque types: `OrderId`, `Sku`, `Port`.
- Use a two-case `enum` in place of a `Boolean` parameter:
  `reserve(order, ReserveMode.DryRun)`, not `reserve(order, true)`.
- Model each state as an `enum` case with its own data, not as `Option` fields.
- A smart constructor returns `Either` with a typed error:
  `Port(value: Int): Either[InvalidPort, Port]`.
- Pass `Clock`, ID generators, and `Random` in. Do not call `Instant.now()` or
  `UUID.randomUUID()` inside logic.

## Collections and mutability

- Use immutable collections by default.
- A local `var` or a mutable builder inside one method is fine.
- Shared state lives in one class as a `private` field. It holds an immutable
  value. Pure functions compute the next value.

## Class as a State monad

A computation that threads state through many steps can use a private class
with mutable fields. A pure function creates one instance, runs it, and returns
the result. Use it for parsers, tokenizers, interpreters, graph walks, and
layout algorithms. Do not thread a tuple through `foldLeft`, and do not add a
`State` monad.

```scala
object WordWrap {

    /** Pure from the outside: the same text and width always give the same lines. */
    def wrap(text: String, width: Int): List[String] = new Run(width).result(text)

    /** One run of the algorithm. Created, used, and dropped inside `wrap`. */
    private final class Run(width: Int) {
        private val done = List.newBuilder[String]
        private val line = StringBuilder()

        def result(text: String): List[String] = {
            text.split("\\s+").filter(_.nonEmpty).foreach(add)
            flush()
            done.result()
        }

        private def add(word: String): Unit = {
            if line.nonEmpty && line.length + 1 + word.length > width then flush()
            if line.nonEmpty then line += ' '
            line ++= word
        }

        private def flush(): Unit = {
            if line.nonEmpty then
                done += line.result()
                line.clear()
        }
    }
}
```

The pattern holds only when every rule below is true:

- The class is `private`. Only the pure entry point creates it.
- One instance serves one computation. Create it, run it, drop it.
- Never store the instance, return it, or share it with another thread.
- All mutable fields are `private`.
- The entry point returns an immutable value, such as a `List`, not a builder.

The instance must outlive one call, or more than one caller uses it? Then this
pattern does not apply. Use the shared-state rule above.

## Find code by symbol

Grep is enough when a name is unique in the code, or when its hits fit in two
or three files that you can read. Use Metals when one of these is true:

- Grep for the name matches more than one symbol, in more files than you can
  read. Examples: the same method name on other types, an overload, or a common
  name such as `apply`, `add`, or `insert`.
- You need the source, the signature, or the ScalaDoc of a library symbol.
- A reference can reach the symbol without its name: a `given`, an implicit
  conversion, an extension method, a renamed import, or an `export`.

The script is `scripts/metals.sh` in this skill's directory. Before its first
use in a session, read `references/metals.md`. It covers setup, cost, and the
cases where Metals is wrong without an error.

```bash
M=<this skill's directory>/scripts/metals.sh
$M start                                 # once per checkout: import and compile
$M find add method                       # FQCN for a simple name
$M usages shop.Cart.add core             # every reference and the definition, as file:line
$M source cats.data.NonEmptyList core    # library source, from the source jar
$M jar cats.data.NonEmptyList core       # its artifact, version, and sources jar
```

- `start` runs the build tool once to export the build, and compiles it. It
  writes `.metals/` and `.bloop/` in the checkout. The task forbids either? Use
  grep, and read library source from the source jar.
- `usages` exits with code 3 when it cannot trust the answer, for example on an
  inherited member or an `inline` library method. Then use grep. Never report
  "no usages" from Metals alone.
- `metals-mcp` is not on `PATH`? Use grep. Tell the user that Metals lookup
  needs `cs install metals-mcp`. Do not install it without asking.
- Never use Metals to compile or test as a check. The build tool stays the gate.

## Build and tooling

Find the build tool. Run it the way the project instructions say.

| File | Tool |
|---|---|
| `build.sbt` | sbt. Use `sbtn` or `sbt --client` for repeat commands. |
| `build.mill` or `build.sc` | mill |
| `project.scala` or `//> using` lines | scala-cli |

**Versions.** Never write a version from memory. Read `<release>` from
`https://repo1.maven.org/maven2/<group/as/path>/<artifact>_3/maven-metadata.xml`,
or run `cs complete-dep <org>:<artifact>_3:`, for example
`cs complete-dep org.scalameta:munit_3:`. Skip `RC`, `M<n>`, `SNAP`,
`alpha`, and `beta` versions.

**Warnings.** Clean the build first. An incremental compile does not print
warnings again for files it does not recompile. Then compile with these flags
before you say the work is done:

```
-Wunused:all -Wvalue-discard -Wnonunit-statement -deprecation -feature
```

The build does not set them? Pass them for this check only. In scala-cli, add
`-O <flag>` for each flag. Fix every warning in code you wrote or changed. Read
the full compiler output. Do not filter it with `grep -v`.

**Run it.** A clean compile does not prove that a codec or a parser works. Run
the new code once on real or sample input before you say the work is done.

**Format.** `.scalafmt.conf` exists? Run the formatter before you finish:
`scala-cli fmt .`, `sbt scalafmtAll`, or the mill scalafmt task.

## Common mistakes

| Mistake | Fix |
|---|---|
| Added upickle or circe for typed codecs because the HTTP client has a module for it | jsoniter-scala, with the client's jsoniter module |
| `Either[String, A]` | `Either[E, A]` with an `enum` error |
| `Try(...).toEither.left.map(...)` on every line of a startup path | Throw. Report once in `main`. |
| Threw an exception for an HTTP 404 or a timeout that the caller must handle | `Either[FetchError, A]` |
| Braceless code in a project that states no style | The fallback style above |
| Compiled once with default flags, then said "done" | Clean, compile with the warning flags, read the full output, and run the code once. |
| Read 40 grep hits for `add` across six types to find the callers of one | `metals.sh usages shop.Cart.add core` |
| Reported "no callers" because Metals returned nothing | Exit code 3 means untrusted. Grep. |
