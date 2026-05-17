# Kafka Streams DSL, Processor API, and ksqlDB

Three ways to express a Kafka Streams processor topology:

1. **Kafka Streams DSL** — high-level Java/Scala API. Declarative,
   composable operators (`filter`, `map`, `groupByKey`, `windowedBy`,
   `aggregate`, …). **~90% of use cases.**
2. **Processor API** — low-level Java/Scala API. You build processors
   directly, manage state stores explicitly, schedule periodic
   functions, and access record metadata.
3. **ksqlDB** — SQL on top of Kafka Streams. Confluent's event
   streaming database (released 2017).

Same underlying runtime in all three cases — ksqlDB compiles SQL down
to Kafka Streams topologies; the DSL compiles to the Processor API.

## DSL vs Processor API

The DSL covers most processors out of the box. Drop down to the
Processor API only when the DSL is not expressive enough.

### Processor API — advantages

- Direct access to **record metadata** (topic, partition, offset,
  headers).
- Ability to **schedule periodic functions** (`Punctuator` — wall-clock
  or stream-time).
- **Fine-grained control over when records are forwarded** — emit
  multiple, delayed, or none.
- Direct, flexible access to **state stores** (any read/write pattern,
  not just what aggregations expose).
- Custom logic that doesn't fit the DSL operator catalog.

### Processor API — disadvantages

- More verbose code — harder to read and maintain.
- Higher barrier to entry for newcomers.
- More error-prone — you can re-invent (badly) what the DSL already
  does, including performance pitfalls.

> Heuristic: start in the DSL. Only escape into the Processor API for
> the specific operator that needs it; keep everything else in the
> DSL.

## ksqlDB

ksqlDB integrates **Kafka Streams + Kafka Connect** behind a single
SQL surface.

- Model data as **streams** or **tables**.
- Use **SQL** for joins, aggregations, filters, transformations,
  windowing.
- **Push queries** — run continuously, emit new results as new data
  arrives. Good fit for event-driven microservices.
- **Pull queries** — point-in-time lookups against a materialized view.
- **Connectors** — declarative integration with external systems via
  Kafka Connect, configured through SQL.

### Architecture

- **ksqlDB Server** — analogous to a Kafka Streams application
  instance; deployed independently of the Kafka cluster; multiple
  servers form a ksqlDB cluster.
- **SQL Engine** — parses SQL and translates it into one or more
  Kafka Streams topologies, then runs them.
- **REST Service** — clients interact with the engine over REST.
- **ksqlDB Clients** — CLI and UI.

### Deployment modes

- **Interactive** — REST + CLI/UI enabled. Submit ad-hoc queries,
  iterate. Good for exploration.
- **Headless** — REST disabled; the cluster runs a fixed `query.sql`
  file. Production / predefined queries only. Configuration changes
  flow through the command topic and metadata.

### When to choose ksqlDB

Rule of thumb: **use ksqlDB when the problem expresses naturally in
SQL.** Specifically:

- Problem fits standard transformations (joins, aggregations, filters,
  windowing).
- Lower barrier — team already knows SQL.
- Fewer code artifacts — topologies live as SQL statements, not JVM
  modules.
- Want one interface for ETL-style integration (connectors) and stream
  transformation.
- Prefer turnkey deployment over building/operating a Kafka Streams
  service.

### When to stay in Kafka Streams

- Complex, custom logic that isn't naturally SQL.
- Need direct Processor API features (custom punctuation, exotic state
  store access patterns).
- Strict typing / unit testability of the topology in JVM code.
- Maximum flexibility, even at the cost of verbosity.

## Side-by-side

|  | Kafka Streams DSL | Processor API | ksqlDB |
|---|---|---|---|
| Language | Java / Scala | Java / Scala | SQL |
| Level | high | low | very high |
| Best for | most stream apps | custom logic | SQL-shaped problems |
| Iteration speed | medium | slow | fast |
| Operability | app you build | app you build | SQL-driven, turnkey |
| Coverage of windows / joins | full | full (manual) | full |

## Source

Lecture 10, HSG ICS. See also Mitch Seymour, *Mastering Kafka Streams
and ksqlDB*, chapters 7–11.
