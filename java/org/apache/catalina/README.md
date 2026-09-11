# org.apache.catalina

## Overview

The `org.apache.catalina` package implements the Catalina Servlet container,
which is responsible for hosting web applications and managing their lifecycle.

Catalina provides the core implementation of the Jakarta Servlet
specification and forms the heart of Apache Tomcat.

---

## Responsibilities

This package includes components responsible for:

- Managing the server hierarchy
- Deploying web applications
- Processing servlet requests
- Managing sessions
		  
							 
- Lifecycle management
- Container event processing

---

## Container Hierarchy

```
Server
 └── Service
      ├── Connector
      └── Engine
            └── Host
                  └── Context
                        └── Wrapper (Servlet)
```

---

## Important Interfaces

| Interface | Purpose |
|------------|---------|
| Container | Base interface for Catalina containers |
| Context | Represents a web application |
| Host | Represents a virtual host |
| Engine | Top-level request processor |
| Pipeline | Request processing chain |
| Valve | Request interceptor |
| Lifecycle | Start/stop components |

---

## Common Entry Points

- StandardServer
- StandardService
- StandardEngine
- StandardHost
- StandardContext

---

## Related Packages

- org.apache.coyote
- org.apache.tomcat.util
- org.apache.naming
- org.apache.jasper

---

## See Also

- BUILDING.txt
- RUNNING.txt
- Architecture documentation