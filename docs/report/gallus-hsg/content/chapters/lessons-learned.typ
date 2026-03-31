= Reflections & Lessons Learned <lessons-learned>

== Technical Lessons

*Event-driven communication simplifies inter-service coupling but shifts complexity to event design.* Moving all inter-service communication to Kafka eliminated synchronous dependencies and made each service independently deployable. However, the simplicity of "publish and subscribe" is deceptive. Designing event schemas that carry sufficient state (ECST), choosing correct partition keys for ordering guarantees, and configuring acknowledgment levels for durability required more careful thought than a straightforward REST API would have.

*Saga compensation is conceptually simple but mechanically intricate.* The textbook description of compensating transactions — "if step 3 fails, undo steps 1 and 2" — understates the implementation complexity. In practice, we had to handle edge cases around idempotency (what if the compensation event is delivered twice?), timing (what if the entity to compensate does not exist yet because of race conditions?), and observability (how do operators know compensation executed successfully?). The flag-driven completion pattern (ADR-0012) emerged specifically from a debugging session where Zeebe error handling short-circuited the BPMN flow, preventing compensation from executing.

*BPMN provides structure that ad-hoc code cannot.* Before adopting Camunda, the onboarding flow was a series of conditional branches in application code. Modeling it as a BPMN process made the flow visible, testable, and monitorable. The parallel gateway for user and portfolio creation, the event-based gateway for confirmation and timeout, and the compensation boundary events would have been significantly harder to implement correctly in plain code.

*Kafka configuration is deceptively impactful.* The experiments in Exercise 1 demonstrated that a single configuration change — `acks=0` vs. `acks=all`, or `earliest` vs. `latest` — determines whether data is lost permanently or replayed safely. These are not theoretical concerns: we observed actual message loss in controlled experiments. This direct experience informed our production configuration choices more effectively than reading documentation alone.

== Process Lessons

*ADRs are most valuable when written close to the decision.* Early in the project, we made architectural decisions informally and documented them later. The ADRs written at decision time (e.g., ADR-0011 on compensation, ADR-0012 on flag-driven completion) capture the context and alternatives more accurately than those reconstructed weeks later. The "context" section of an ADR is hardest to reconstruct after the fact, because the constraints and trade-offs that felt obvious during the discussion fade from memory.

*Starting with a minimal architecture and evolving it works.* The initial system (Exercise 2) had only two services communicating through a single Kafka topic. Each subsequent exercise added complexity: the transaction service introduced BPMN orchestration, the user service added persistence and confirmation flows, and the onboarding service introduced saga coordination with compensation. This incremental approach allowed us to validate each pattern in isolation before combining them.

*Shared event modules need discipline.* The `shared-events` Maven module ensures compile-time consistency, but it also means that any event schema change triggers a rebuild of all services. We learned to treat event schemas as public APIs: add fields freely, rename or remove fields reluctantly, and always consider backward compatibility even in a monorepo where all consumers update simultaneously.

== Teamwork Lessons

*Clear service ownership reduces coordination overhead.* Assigning each bounded context to a single team member — with shared responsibility for architecture and integration — worked well for a two-person team. Each person could develop their services independently, meeting only to align on event schemas and BPMN process contracts. The `shared-events` module served as the explicit interface between areas of ownership.

*Integration issues surface late.* Despite clear contracts, the majority of bugs appeared during integration: message deserialization mismatches, incorrect Zeebe correlation keys, compensation events missing required fields. Investing in early integration testing — running the full Docker Compose stack rather than testing services in isolation — would have caught these issues sooner.

*Two people, five services.* The scope was ambitious for a two-person team within one semester. The decision to add a dedicated onboarding service (ADR-0010), while architecturally sound, added deployment and monitoring overhead. In retrospect, the trade-off was worthwhile for demonstrating the saga pattern, but we underestimated the time required for BPMN process debugging and compensation testing.
