# Events and Data Streams

## What is an Event?

An event is anything that we can observe occurring at a particular point in time.

Cross-domain examples:
- **Manufacturing**: Machine 21-X breaks its drill at 15:27:37 on April 15, 2019
- **Travelling**: Patricia Smith checks in to Hotel Paradise at 19:27 December 17, 2018
- **Retail**: User Peter77 adds glasses to shopping basket at 13:21 January 5, 2019
- **Pharmacy**: Pharmacy GetWell opens at 08:30 January 10, 2020

## Data Stream (a.k.a. Event Stream / Streaming Data)

A data stream is an **unbounded sequence of events**.

Implications of unboundedness:
- The stream may have started before we began observing it
- The stream continues indefinitely into the future
- You do not know the end → cannot store the entire data → **retention** is required

Additional stream characteristics:
- **Ordered** — inherent notion of which event occurred before/after others
- **Immutable** — events cannot be modified after they occur
- **Replayable** — replayability is a desirable property (enables reprocessing)

## Event Schema

Events produced by a source share a common structure called an **event schema**. This
schema is repeated for every event in the stream — events in a stream must always
look the same.

Example — environmental sensor in a smart factory, schema per event:
- `timestamp`
- `temperature (t)`
- `humidity (h)`
- `air pressure (p)`
- `air quality index (iaq)`

Each sensor measurement is one event; the sequence of sensor measurements forms the
data stream that can be processed as events arrive.

## Source

Lecture 7, *Stream Processing – Introduction and Key Concepts*, HSG ICS.
References: Dean & Crettaz, *Event Streams in Action* (Manning 2019);
Etzion & Niblett, *Event Processing in Action* (Manning 2010).
