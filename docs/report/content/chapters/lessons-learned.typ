= Reflections & Lessons Learned <lessons-learned>

== Technical Lessons

=== Process-oriented Architecture

*Camunda BPMN has a steep learning curve, but the payoff is real.* Learning how to model gateways, timers, compensation, and message correlations in Camunda was initially demanding. Once this hurdle was overcome, however, the modeler supported development very well because it made the flow explicit and reduced the implementation effort for individual tasks. A good example was the confirmation email, where Camunda's mail connector replaced what would otherwise have required a custom SMTP client in application code.

*The concepts are intuitive in theory, but implementation multiplies the decisions.* Patterns such as orchestration, compensation, and event-driven communication feel straightforward when discussed in lectures or on paper. The complexity appeared during implementation, where many additional degrees of freedom had to be resolved: event structure, correlation keys, error handling, retries, and service boundaries all influenced each other. Precise decision-making turned out to be essential, because without it, it is easy to get lost in technically plausible but incompatible alternatives.

*Dual-write problems are easy to overlook.* The need for the transactional outbox (ADR-0014) only became obvious once we considered what happens when the application crashes between a database commit and a Kafka publish. Before that, the inline publish felt natural and correct. The lesson is that dual-write hazards hide behind code that looks straightforward — they only surface under failure conditions that are rare but not impossible.

*Idempotency must be designed in, not patched on.* The duplicate portfolio update we observed during integration (documented in @results) confirmed that at-least-once delivery is not a theoretical edge case. It happens during normal restarts and rebalancing. Retrofitting idempotency after the fact required rethinking the transaction boundary around `upsertHolding()`. Designing consumers as idempotent from the start would have been simpler than fixing the bug after discovery.

=== Stream-processing Architecture

*Stream-processing topologies become complex quickly once state enters the design.* Stateless filters and translators were easy to reason about, but bid/ask matching, OHLC windows, and portfolio valuation introduced state stores, repartitioning, joins, grace periods, suppression, and reprocessing questions. The main lesson was to draw the topology and its keys before writing code; otherwise small contract decisions later force larger topology changes.

*Compacted topics are useful caches, not just transport channels.* The FX-rate, coin-metadata, user-display-currency, and portfolio-value topics showed how Kafka can hold the latest value per key and let services rebuild local state without synchronous lookups. This pattern made the read paths simpler, but it also forced us to think carefully about topic cleanup policy, keys, and whether each event represented a durable latest fact.

*Event time is a correctness boundary, not only metadata.* OHLC aggregation, market-scout summaries, and bid/ask matching all became easier to reason about once processing was driven from payload timestamps instead of arrival time. Custom timestamp extractors made replay deterministic and made late-event behavior explicit, but they also forced every event contract to define which timestamp actually carries business meaning.

*Kafka Streams DSL and Processor API solve different kinds of problems.* The DSL was concise for filters, translators, table joins, windows, and aggregates. The bid/ask matcher showed its limit: a normal stream-stream join would produce candidate pairs, but it could not express allocation, price-time priority, and "each ask only once" semantics cleanly. Dropping to the Processor API was the right trade-off because it kept the streaming-join semantics while making the state and ordering rules explicit.

*Interactive Queries couple read paths to runtime placement.* The portfolio valuation endpoint demonstrated how powerful it is to read directly from a materialized state store. At the same time, it made the single-instance assumption visible: a production deployment would need metadata lookup and request forwarding for keys hosted on another instance. Interactive Queries are therefore a useful pattern, but they should be introduced with an explicit scaling story.

*Single-broker Kafka obscures real durability semantics.* Running the experiments on multi-broker clusters and then developing against a single-broker Docker Compose stack created a subtle gap: `acks=all` behaved identically to `acks=1` in development, so producer reliability issues were invisible locally. We only recognized this fully when documenting the configuration for the report. For future projects, even a two-broker local cluster would surface replication behavior earlier.

== Process Lessons

=== Process-oriented Architecture

*ADRs were even more important here than in ASSE.* Because implementation decisions were tightly coupled across services, documenting them close to the point of decision was essential. A choice made in one service often constrained or informed the design of another, so the rationale had to stay visible and reusable. Kafka event contracts are the simplest example: once we settled on how events should be structured and exchanged, that decision had to be applied consistently across multiple services.

*The lecture concepts transferred well to a self-chosen project.* One of the most valuable aspects of this project was applying the lecture concepts to a topic we chose ourselves. This freedom made the work more engaging, while also forcing us to implement the patterns under realistic constraints instead of only discussing them abstractly.

*Choosing the right saga pattern requires understanding the wait semantics.* The distinction between the Parallel Saga for onboarding and the Fairy Tale Saga for order placement was not immediately obvious. It only became clear once we analyzed the nature of the wait: onboarding waits for a deterministic human action (clicking a link), while order placement waits for a non-deterministic market event (a price match). The wait semantics dictated the coupling model, which in turn dictated the saga style. This selection process was one of the most instructive parts of the project.

=== Stream-processing Architecture

*Stream-processing work needed a stronger up-front vocabulary.* During the second half of the course, we had to become stricter about names such as raw order-book depth, ask quote, matchable ask, opportunity, localized price, OHLC bar, and portfolio value. Without this vocabulary, it was too easy to call every market-data event a "price stream" and blur important ownership boundaries. The context documents and ADRs helped keep stream contracts precise.

*The unit of planning shifted from services to topologies.* In the process-oriented half, most discussions started with BPMN processes, workers, and bounded contexts. In the stream-processing half, useful planning started with input topics, keys, timestamps, state stores, repartition steps, and output topics. This changed the way we estimated work: a small feature could imply several contracts, internal changelog topics, test fixtures, and replay scenarios.

*Raw and derived streams should be separated deliberately.* Splitting Binance partial-book ingestion from the Market Order Scout topology added one deployable, but it made `crypto.scout.raw` a replayable boundary and kept derived stream logic out of the WebSocket adapter. The same principle applied to the portfolio valuation stream: the source topic remained the durable fact, while the state store and compacted output were rebuildable projections.

*Workflow timers and stream windows must be designed together.* The order-matching flow crossed both halves of the course: Camunda waited for a match, while Kafka Streams evaluated bids and asks within an event-time window. Aligning the 30 s validity window, 5 s processing and retention margin, and `PT35S` BPMN rejection timer made the boundary deterministic. Designing these values separately would have created race conditions between late stream events and the workflow timeout path.

== Teamwork Lessons

*Clear service ownership reduces coordination overhead.* Assigning each bounded context to a single team member, with shared responsibility for architecture and integration, worked well for a two-person team. Each person could develop their services independently, meeting only to align on event schemas and BPMN process contracts. The `shared-events` module served as the explicit interface between areas of ownership.

*Integration issues surface late.* Despite clear contracts, the majority of bugs appeared during integration: message deserialization mismatches, incorrect Zeebe correlation keys, and compensation events missing required fields. Running the full Docker Compose stack earlier and more frequently, rather than testing services in isolation, would have caught these issues sooner.

*Derived-event splitting creates more data and more contracts than expected.* Splitting raw order-book snapshots into ask quotes, matchable asks, opportunities, and summaries made the architecture cleaner, but it also multiplied schemas, topics, and retention choices. The split was still worthwhile because each topic had a clear consumer and lifecycle, but it made the operational surface larger than a single direct stream would have been.

*Two people, many deployables.* The scope was ambitious for a two-person team within one semester. The decision to add dedicated services for onboarding, reference data, and market-scout processing, while architecturally sound, added deployment and monitoring overhead. In retrospect, these trade-offs were worthwhile for demonstrating the patterns cleanly, but we underestimated the time required for BPMN process debugging, stream processing, and compensation testing.
