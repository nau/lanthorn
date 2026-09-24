---
name: writing-logs
description: Use when adding or changing a log statement, choosing a log level, naming a log event or field, deciding what a log message should say, reviewing logging in a diff, or when a service's logs must be read by an on-call operator or by an AI agent during an incident.
---

# Writing Logs

## Overview

No superpowers skill covers logging. This skill is an addition, not a fork.

Standards stop at the envelope. RFC 5424 defines severities. OpenTelemetry
defines field names. OWASP defines what must never appear. No standard defines
what a line says. This skill does.

**Core rule: a log line is a constant event name plus fields. Never put a value
in the event name or in the prose.**

## When to use

- You add or change a log statement.
- You choose a level, an event name, or a field name.
- You review logging in a diff.

## 1. Shape every event

Emit these parts, in this order.

| Part | Rule |
|---|---|
| Time | RFC 3339, UTC, milliseconds: `2026-09-21T14:03:11.204Z`. |
| Level | `error`, `warn`, `info` or `debug`. A field, never a `[WARN]` prefix. |
| Event name | Constant dotted name. Pass it as the logger's message argument. |
| Correlation | `trace_id` or `request_id`. Take it from the incoming request or message. Generate one only if none arrived. Bind it once per unit of work. |
| Subject | The ids and quantities the event is about: `tenant_id`, `charge_id`, `amount_cents`. |
| Outcome | `outcome`, `duration_ms`, `error.type`. Required when a unit of work ends. |
| `detail` | Optional prose for the operator. Constant text. |

```
ts=2026-09-21T14:03:11.204Z level=info event=charge.completed trace_id=01JBQ8VF tenant_id=acme charge_id=ch_8812 outcome=declined decline_code=insufficient_funds amount_cents=4200 duration_ms=412
```

Pass the event name as the message argument. Pass the values as fields.

| Stack | Call |
|---|---|
| Scala, JVM (SLF4J 2) | `log.atInfo().addKeyValue("outcome", outcome).log("charge.completed")` |
| TypeScript (pino) | `log.info({ outcome }, "charge.completed")` |
| Rust (tracing) | `info!(outcome = %outcome, "charge.completed")` |

- The logger writes the name under its own key, such as `msg` or `message`. Do
  not repeat the name in a second field.
- Never name a field `msg` or `message`. Loggers reserve these keys for the
  message argument. A second field with the same key overwrites it or writes a
  duplicate key.
- Write JSON to the collector and key=value to a terminal. Never build a line by
  string interpolation.
- Write one event on one physical line.

## 2. Name the event

- Use `<noun>.<verb_past>`. Lowercase. Dots between namespaces, underscores
  inside a word: `charge.created`, `psp.call.retried`.
- Use the past tense. You log what happened.
- Take the verb from this set before you invent one: `created` `updated`
  `deleted` `started` `completed` `failed` `rejected` `declined` `retried`
  `skipped` `expired` `evicted` `throttled`.
- Read the existing vocabulary before you add a name:

  ```bash
  rg -o "[\"'][a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+[\"']" --no-filename | sort | uniq -c | sort -rn
  ```

  The output also contains other dotted strings, such as package names. Check
  it by eye.
- Use one name for one thing. Then `rg -F charge.declined src/` finds the call
  site, and `grep -F charge.declined` finds every occurrence in the logs.

## 3. Choose the level by the health of this service

| Level | Use when | Example |
|---|---|---|
| `error` | A human must act. It pages, or it goes to a triage queue. | `db.write.failed` |
| `warn` | The service handled it, but it is degraded. | `psp.call.retried` |
| `info` | A state change that the audit trail needs. The default for the canonical event. | `charge.created` |
| `debug` | Detail for development. Off in production. | `psp.request.sent` |

- Log expected, handled outcomes at `info`: a 404, a validation failure, a
  declined card. The service did its job.
- If you cannot say what an operator must do about an `error`, use a lower level.
- Emit only these four levels. The transport can carry the eight of RFC 5424.

## 4. Write `detail` for the operator

The operator did not write the code. `psp.call.retried` means nothing to them.

- Add `detail` to each event where a human must decide something. Omit it on
  other events.
- State the consequence in plain words:
  `detail="payment provider slow, charge will retry"`.
- Spell out acronyms in `detail`. Keep the short form in names and fields.
- Keep `detail` constant. Values go in fields.
- Add `runbook=<url>` to each event that pages.

## 5. Log each failure once

**Log where you handle. Throw where you fail.**

- A function that throws or returns an error does not log. It adds context to
  the error and keeps the cause.
- The boundary that handles the error logs it once.
- If no caller handles the error, you are the boundary. A job entry point, a
  consumer loop, or `main` logs its own failure.

## 6. Emit one canonical event per unit of work

- At the end of each request, job, message, or batch, emit one event. It
  carries `outcome`, `duration_ms`, and every field collected on the way.
- Emit it on every exit path: `finally` in TypeScript and Scala,
  `guaranteeCase` in Cats Effect, a wrapper function or a `Drop` guard in Rust.
- Set `outcome` on every exit path. An unexpected failure sets `outcome=error`
  and `error.type`.
- Emit other lines only for errors at the handling boundary, state changes that
  outlive the unit of work, and `debug` steps.
- Count repeated outcomes on the canonical event: `invoices_skipped=12`. Do not
  log each item.

## 7. Name the fields

- Use `snake_case` inside a namespace and dots between namespaces:
  `http.response.status_code`.
- Use the OpenTelemetry name if one exists. See `references/otel-fields.md`.
- Never add your own field inside an OpenTelemetry namespace such as `http.*`.
- Put the unit in the name: `duration_ms`, `amount_cents`, `size_bytes`.
- Give each field one type everywhere. Count `attempt` from 1 everywhere.
- Put a stack trace in `exception.stacktrace`, as one escaped string.

## 8. Keep values selectable

Operators copy ids with a double-click. Most terminals stop a double-click at
spaces, brackets, and quotes.

- No spaces in values: `outcome=insufficient_funds`.
- No brackets around ids: `charge_id=ch_8812`, not `[ch_8812]`.
- Quote a value only if it contains a space. `detail` is the usual case.
- No trailing period in `detail`.

## 9. Never log secrets or personal data

- Never log card numbers, CVCs, passwords, tokens, API keys, session ids,
  `Authorization` headers, full request or response bodies, or personal data
  beyond an id.
- Log a reference instead: `card_last4=4242`, `api_key_id=ak_31`, or a hash.

## Example

<Bad>
```ts
logger.info(`charge requested for ${req.amountCents} cents`);
if (req.amountCents <= 0) {
  logger.warn("rejected charge: non-positive amount");  // wrong level, then throws
  throw new RangeError("amount must be positive");
}
...
logger.error(`PSP call timed out after ${attempt} attempts, giving up`);  // logged, then thrown
throw err;
...
logger.info(`charge ${charge.id} created`);
```
</Bad>

Values inside the messages, so no message is a stable grep target. The timeout
is logged here and again by the caller. No `duration_ms` or `outcome`, so no
query can answer "how slow is the provider".

<Good>
```ts
import pino from "pino";

const logger = pino();

export async function createCharge(req: ChargeRequest): Promise<Charge> {
  const log = logger.child({ trace_id: req.traceId, tenant_id: req.tenantId });
  const started = performance.now();
  const fields: Record<string, unknown> = {
    amount_cents: req.amountCents,
    card_last4: req.card.number.slice(-4),
  };
  let outcome = "ok";
  try {
    if (req.amountCents <= 0) {
      outcome = "rejected_amount";
      throw new RangeError("amount must be positive");    // throw, do not log
    }
    let resp: PspResponse | undefined;
    for (let attempt = 1; attempt <= 3; attempt++) {      // count from 1
      try {
        resp = await psp.charge(req);
        break;
      } catch (err) {
        if (!(err instanceof TimeoutError)) throw err;
        log.warn(
          { ...fields, attempt, detail: "payment provider slow, charge will retry" },
          "psp.call.retried",
        );
        if (attempt === 3) {
          outcome = "psp_timeout";
          throw err;
        }
      }
    }
    if (resp!.status === 402) {
      outcome = "declined";
      fields.decline_code = resp!.declineCode;
      throw new PaymentDeclined(resp!.declineCode);
    }
    const charge = await persist(req, resp!);
    fields.charge_id = charge.id;
    log.info(fields, "charge.created");                   // outlives the request
    return charge;
  } catch (err) {
    if (outcome === "ok") {                               // unexpected failure
      outcome = "error";
      fields["error.type"] = (err as Error).name;
    }
    throw err;
  } finally {
    log.info(
      { ...fields, outcome, duration_ms: Math.round(performance.now() - started) },
      "charge.completed",
    );
  }
}
```
</Good>

Three names, all constant. One canonical event on every path, with `outcome`
and `duration_ms`. The decline is `info` because the service worked. The card
number leaves as four digits. The caller logs the exception once.

## Check before you commit

1. Every event name is constant, and the event name and `detail` contain no values.
2. No `error` log sits directly before a `throw`, except at the top boundary.
3. Handled outcomes (404, validation, decline) are `info`.
4. Each unit of work emits one canonical event with `outcome` and `duration_ms`.
5. No field is named `msg` or `message`.
6. Field names match OpenTelemetry where a name exists.
7. No secret, token, or personal data appears in any field.
