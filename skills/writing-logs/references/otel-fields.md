# OpenTelemetry field names

Reuse a name from here before you invent one. A field that matches the
convention is queryable by any backend and by anyone who has read the spec. A
field you invented is queryable by the person who invented it.

Checked against semantic conventions 1.44.0. Names do move between versions, and
several of these replaced an older spelling. Verify against the version your
collector pins before you rely on one.

## Naming rules

- Namespaces are delimited by a dot. Words inside one component are joined by an
  underscore: `http.response.status_code`.
- Names are lowercase.
- Singular for one entity, plural only when the value is an array.
- `otel.*` is reserved by the specification.
- Do not add your own attribute inside an existing convention namespace. An
  `http.tenant_id` will collide the day the spec defines one. Use your own
  namespace, or a reverse domain (`com.acme.shop_id`) for anything you publish.

## Log record

| Field | Meaning |
|---|---|
| `error.type` | The class or code of the error. The field alerts are built on. |
| `exception.type`, `exception.message`, `exception.stacktrace` | The raised exception. `stacktrace` is one escaped string. |
| `log.record.original` | The unparsed line, when you ingested text. |
| `log.record.uid` | Unique id for the record, for deduplication. |
| `log.file.path`, `log.iostream` | Where the record was read from. |

## Request and response

| Field | Meaning |
|---|---|
| `http.request.method` | `GET`, `POST`. Uppercase. |
| `http.response.status_code` | Integer. Not `status`, not `code`. |
| `http.route` | The template, `/charges/{id}`. Low cardinality. |
| `url.path`, `url.query`, `url.full` | The actual URL parts. Strip credentials. |
| `client.address`, `server.address`, `server.port` | Peers. |
| `network.protocol.version` | `1.1`, `2`. |
| `user_agent.original` | Raw UA string. |

## Database, messaging, RPC

| Field | Meaning |
|---|---|
| `db.system.name` | `postgresql`, `redis`. Replaced `db.system`. |
| `db.namespace`, `db.collection.name`, `db.operation.name` | What was touched, and how. |
| `db.query.text` | The statement. Parameterized, never interpolated. |
| `messaging.system`, `messaging.destination.name`, `messaging.message.id` | Queue and message. |
| `rpc.system`, `rpc.service`, `rpc.method` | RPC call. |

## Identity and context

| Field | Meaning |
|---|---|
| `user.id` | Opaque id. Not an email, not a name. |
| `session.id` | Hash it if it is a live session token. |
| `feature_flag.key` | The flag that changed the path. |
| `code.function.name`, `code.file.path`, `code.line.number` | Call site. Replaced `code.function`, `code.filepath`, `code.lineno`. |

## Resource, set once on the logger

These belong on the logger at startup, not on a call site. Every line carries
them for free.

| Field | Meaning |
|---|---|
| `service.name`, `service.version`, `service.instance.id` | Which build of which service emitted this. |
| `deployment.environment.name` | `production`, `staging`. Replaced `deployment.environment`. |
| `host.name`, `container.id`, `k8s.pod.name`, `cloud.region` | Where it ran. |

Trace correlation is not an attribute. `trace_id` and `span_id` are top-level
fields on the log record in the data model. If your logger writes them as plain
attributes, use exactly those two names so a backend can still join them.
