# Event Processing Architecture

## Definition

**Event processing** performs operations on events *as they arrive*. Typical
operations include filtering, transforming, aggregating, and joining event streams.

## High-Level Components

### Event Producers (Sources)
- Systems
- Sensors
- Applications
- Business processes

### Intermediary Processing
Processing layer between sources and sinks. May also feed back into itself
(processors can act as sources for downstream processors).

### Event Consumers (Sinks)
- Applications
- Dashboards
- Actuators
- Data stores
- Business processes

**Key point**: sources and consumers are separate — producers do not know
consumers directly.

## Event Processing Network (EPN)

Event processing is typically composed of multiple processing steps. These steps
together form an **event processing network**.

> **EPN** = a set of producers, processors, and consumers connected by **event channels**.

Think of it as a pipeline:

```
Event Producer → Event Channel → Event Processing Agents → Event Channel → Event Consumer
```

Event Processing Agents perform operations such as:
- `Project`, `Filter`, `Translate (Map)`
- `Join`, `Group`, `Aggregate`

Events flow through multiple processing stages forming a pipeline. Multiple
producers can feed the same topic/channel; multiple agents can fan out to
different consumers.

## Source

Lecture 7, HSG ICS. Based on Etzion & Niblett, *Event Processing in Action* (Manning 2010).
