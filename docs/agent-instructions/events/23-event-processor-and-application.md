# Event Processor & Event Processing Application

Two structural concepts: the **unit** (single processor) and the
**composition** (application made of many processors).

## Event Processor

Event Processors are components that:
- **Read events**
- **Process events**
- **Write new events to output streams**

```
                  Event Processor
Input Stream ──► ░░░░░░░░░░░░░░░ ──► Output Stream
(one or more)     processing logic    (one or more)
```

Properties:
- May act as an **Event Source and/or Event Sink**
- **Can be distributed** across multiple nodes
- Should **perform one specific task** (e.g. filter, map)

The one-task principle keeps processors composable — each does one thing
well, and pipelines are built by wiring them together.

## Event Processing Application

> To build a fully-fledged application for data in motion we build an
> **Event Processing Application** by composing one or more Event
> Processors into an interconnected processing topology.

```
                       Event Processing Application
                       ┌──────────────────────────────────────┐
Input Stream  ──────► │ Processor ──► Processor              │
                       │     ↓                                │
                       │ Processor ──► Processor ──► Processor│
                       │     ↓                           ↓    │
                       └─────┼───────────────────────────┼────┘
                             ▼                           ▼
                       Output Stream A             Output Stream B
```

An EPA is a **processing topology of several interconnected event
processors**, potentially with multiple inputs and multiple outputs.

## Design Implication

- Individual processors are small, testable, reusable units
- Applications are the *composition* — they define which processors run
  and how they connect
- Distribution happens at the processor level; the topology is logical,
  the physical execution is spread across instances

## Source

Lecture 8, HSG ICS.
