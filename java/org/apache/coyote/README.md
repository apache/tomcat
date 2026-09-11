# org.apache.coyote

## Overview

The `org.apache.coyote` package provides the protocol implementation layer
between the network connector and the Catalina servlet container.

It is responsible for translating incoming protocol requests into servlet
requests that Catalina can process.

---

## Responsibilities

- HTTP/1.1 processing
- HTTP/2 support
- AJP protocol
- Request parsing
- Response generation
- Protocol abstraction

---

## Request Flow

```
Socket

↓

ProtocolHandler

↓

Processor

↓

Adapter

↓

Catalina
```

---

## Important Classes

- AbstractProtocol
- Http11Processor
- Adapter
- Request
- Response