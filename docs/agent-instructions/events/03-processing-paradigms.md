# Processing Paradigms

Three paradigms for data processing, compared by interaction model, latency,
and typical use case.

## Request–Response (most common)

- **Low latency** (milliseconds)
- Client requests → system responds
- **Blocking** interaction
- Examples: point-of-sale systems, credit card processing

## Batch Processing (data warehouse style)

- **Scheduled** processing
- **High throughput**
- Results available **later**
- Examples: data warehouses, business intelligence reporting

## Stream Processing (focus of this course)

- **Continuous** processing
- **Non-blocking** interaction
- Reacts to events in **real time**
- Examples: fraud detection, monitoring, pricing

## Databases vs. Stream Processing

The query/data relationship is **reversed** between the two paradigms.

### Databases
- Data is **stored**
- Queries **run on demand** (user issues a query against stored data)

### Stream Processing (reactive)
- Queries **run continuously**
- Results **produced when events arrive** (events flow through standing queries)

Mental model:
- DB: *static data, dynamic queries*
- Stream processing: *static queries, dynamic data*

## Source

Lecture 7, HSG ICS. DB vs. stream processing framing from course
*Complex Event Processing* by Mathias Weske.
