# manager2 — Design of a modern Tomcat manager web application

Status: proposal
Date: 2026-09-11

## 1. Overview

`manager2` is a new web application, deployed at `/manager2`, that replaces the
human-facing parts of the three existing management web applications:

| Existing webapp | Servlet | What manager2 inherits |
|---|---|---|
| `webapps/manager` | `HTMLManagerServlet` (`/html/*`) | web application lifecycle, deployment, session management, SSL & leak diagnostics |
| `webapps/host-manager` | `HTMLHostManagerServlet` (`/html/*`) | virtual host lifecycle and configuration persistence |
| `webapps/manager` | `StatusManagerServlet` (`/status/*`) | JVM, connector and per-application runtime statistics |

Goals:

1. **One webapp** for webapp management, host management and monitoring,
   instead of two webapps with three different dated UIs.
2. **Clean, modern, responsive UI** — a single page application with no build
   toolchain, no third-party JS dependencies, that works on desktop, tablet
   and phone.
3. **Live-updating runtime statistics** — graphs and tables that refresh
   automatically while the page is open.
4. **Fully secured** — HTTP FORM authentication, CSRF protection on every
   state-changing operation, hardened security headers, role-based
   authorization, audit logging.

Non-goals (staying with the existing webapps):

- The scriptable *text* API (`/text/*`, `ManagerServlet`) and the JMX proxy
  (`/jmxproxy/*`) are untouched. `manager2`'s JSON API becomes the modern
  scriptable interface, but the legacy endpoints keep working for existing
  automation.
- No multi-node/cluster management; scope is the same as today: the `Host`
  the webapp is installed in (for webapps) and the `Engine` it belongs to
  (for hosts).

## 2. Feature parity

Everything a user can do today in the three HTML interfaces must be possible
in manager2, mapped as follows:

**From HTMLManagerServlet**

| Feature | manager2 page / API |
|---|---|
| List contexts (path, version, display name, state, sessions, docBase) | Applications page / `GET /api/apps` |
| Start / stop / reload / undeploy a context | row actions, confirm modal |
| Deploy from WAR/config on the server (path, version, config, war, replace) | Deploy wizard, "From file on server" tab |
| Deploy by WAR upload (multipart, 50 MiB cap) | Deploy wizard, "Upload" tab with progress bar |
| Session list (11 sort columns), session detail, attributes | Applications detail, Sessions tab |
| Invalidate selected sessions / expire idle sessions | Sessions tab |
| Remove a single session attribute | session detail drawer |
| SSL connector ciphers / certs / trusted certs | Diagnostics page |
| Reload SSL host config (all or one `tlsHostName`) | Diagnostics page |
| Find reloaded-context memory leaks (`findleaks`) | Diagnostics page |
| JNDI global resources (`/resources?type=`) | Diagnostics page |
| VM info, thread dump | Diagnostics page |
| Server info panel (version, JVM, OS, host, IP) | persistent header strip |
| `showProxySessions` behaviour (backup/StoreManager proxy sessions) | servlet init-param, kept |

**From HTMLHostManagerServlet**

| Feature | manager2 page / API |
|---|---|
| List virtual hosts (name, aliases) | Hosts page / `GET /api/hosts` |
| Add host (name, aliases, appBase, manager, autoDeploy, deployOnStartup, deployXML, unpackWARs, copyXML) | "Add host" modal |
| Start / stop / remove a host | row actions (own host protected, as today) |
| Persist configuration to `server.xml` (StoreConfigLifecycleListener) | Hosts page button |

**From StatusManagerServlet**

| Feature | manager2 page / API |
|---|---|
| JVM memory (free/total/max) + per memory-pool table (type, init, committed, max, used) | Dashboard (live) / `GET /api/status` |
| Per-connector thread pool (max, current, busy, keep-alive) | Dashboard (live) |
| Per-connector aggregates (request count, error count, bytes in/out, avg & max processing time) | Dashboard (live) |
| Per-socket (RequestProcessor) live table (stage, time, bytes, remote addr, vhost, method + URI + query + protocol) | Monitoring page (live) |
 | Per-application detail (state, start time, startup time, TLD scan time, session manager stats, JSP monitor stats, per-servlet wrapper stats) | Applications detail, Metrics tab / `GET /api/status/apps/{path}` |
 | `?XML=true` / `?JSON=true` machine output | replaced by the versioned JSON API |

**New (no legacy equivalent)**

| Feature | manager2 page / API |
|---|---|
  | Browse and edit the whole live server configuration (services, engines, hosts, contexts, wrappers, valves, connectors, executors, aliases, lifecycle listeners, realms with sub realms, the context sub components — manager with its session id generator, resources, loader, cookie processor — the cluster and its full channel (membership, sender + transport, receiver, interceptors, deployer, manager template, cluster valves and listeners) on any container, the JNDI naming resources of the server and of each context, TLS: SSL host configurations with their certificates, and the upgrade protocols of a connector): property editing, structural add/remove, start/stop/restart of any component with a lifecycle, persistence to `server.xml` | Configuration page / `GET /api/config/*` (the legacy manager required hand-editing `server.xml` and a restart for anything beyond the host manager's "persist" button; TLS, realms and JNDI entries apply live, without a restart) |

## 3. Architecture

```
                +----------------------------------------------------------+
                |  webapps/manager2 (the WAR from modules/manager2)        |
  Browser  <--> |  index.html (SPA shell)   /login (LoginServlet)          |
  (same-origin  |  css/  js/  (static, no build step, no 3rd-party JS)     |
   fetch)       +------------------------+---------------------------------+
                                        | session cookie + X-CSRF-Token
                     +------------------+-------------------------+
                     |  ServletContainer (filters)                |
                     |  CsrfFilter  ->  HttpHeaderSecurityFilter  |
                     |  security-constraints on /api/* (FORM auth) |
                     +------------------+-------------------------+
                                        |
        +------------------------------+---------------------------------+
        |  org.apache.tomcat.manager2  (WEB-INF/lib/manager2.jar)        |
        |                                                                      |
        |  HomeServlet       /  + SPA deep links: login gate + shell          |
        |  LoginServlet      /login  +  /logout (LogoutServlet) + /error      |
        |  AppsApiServlet    extends ManagerServlet                          |
        |    /api/apps/*        reuses deploy/start/stop/reload/undeploy/     |
        |                         expire/sessions/ssl/leaks logic             |
        |  HostsApiServlet   extends HostManagerServlet                      |
        |    /api/hosts/*       reuses add/remove/start/stop/persist          |
         |  StatusApiServlet  standalone                                     |
         |    /api/*            StatusSnapshot: same MBean queries as          |
         |                         StatusManagerServlet/StatusTransformer     |
         |                         but serializes typed JSON                  |
         |  ConfigApiServlet    standalone (direct container API)              |
         |    /api/config/*       live component tree, attribute updates,      |
         |                         structural add/remove, storeconfig         |
         +---------------------------------------------------------------------+
                                        |
                              JMX MBeanServer (ThreadPool,
                              GlobalRequestProcessor, RequestProcessor,
                              WebModule, Manager, JspMonitor, Wrapper)
```

Key decisions:

1. **Reuse, do not fork.** The new API servlets extend the existing
   `ManagerServlet` / `HostManagerServlet` (the same pattern
   `HTMLManagerServlet` already uses) and call the same protected methods.
   Container wiring (`ContainerServlet.setWrapper`) is inherited, so all of
   the existing safety logic — context name validation, `pathCheck`
   canonical-path containment, `tryAddServiced` races guard, "cannot
   undeploy the context running this servlet", "cannot stop/remove the host
   running this servlet" — is inherited for free. The one exception is
   `ConfigApiServlet`: it works on the whole `Server` tree (services,
   engines, connectors, executors, valves) which those servlets do not
   expose, so it is a standalone `HttpServlet` on the direct container
   APIs with its own guards (same "cannot touch the component hosting this
   webapp" rules, plus `LAST_SERVICE` / `BASIC_COMPONENT`).
2. **JSON built from typed objects, not parsed text.** Read-only endpoints
   build their JSON from the container/MBean API directly (as
   `StatusManagerServlet` already does). Mutating endpoints invoke the
   inherited protected methods with a `StringWriter` and wrap the result in
   a stable JSON envelope; the localized text is carried in `message` for
   display.
3. **No JSP.** The webapp contains static HTML/CSS/JS plus servlets. This
   removes a whole class of rendering surface and keeps the bundle small.
4. **No new Java dependencies** (JDK + existing Tomcat/Jakarta APIs only)
   and **no JS dependencies** (hand-written ES modules), so the Ant build
   stays self-contained and the attack surface stays minimal.
5. **i18n** — operation result messages reuse the existing
   `org.apache.catalina.manager` StringManager bundles (already translated to
   10 locales). New UI-only strings live in a JS dictionary module
   (English first; server messages are shown as-is and already localized).

## 4. JSON API

Base path: `/manager2/api`. All responses are `application/json;
charset=UTF-8`, `Cache-Control: no-store`. Mutations return an envelope:

```json
{ "ok": true, "message": "Deployed web application: /docs" }
```

Errors use `{"ok": false, "message": "...", "error": "DEPLOY_FAILED"}` with a
machine-readable `error` code and non-2xx status.

| Method | Path | Role(s) | Purpose |
|---|---|---|---|
| GET | `/api/info` | any authenticated | server version, JVM, OS, host name/IP, uptime |
| GET | `/api/csrf` | any authenticated | current CSRF token |
| GET | `/api/hosts` | manager-gui | hosts: name, aliases, appBase, state, started (true while the host is accepting applications - the same `getState().isAvailable()` test the classic host manager uses) |
| POST | `/api/hosts` | manager-gui | add host: name, aliases[], appBase, manager, autoDeploy, deployOnStartup, deployXML, unpackWARs, copyXML |
| POST | `/api/hosts/{name}/start` | manager-gui | start host |
| POST | `/api/hosts/{name}/stop` | manager-gui | stop host |
| DELETE | `/api/hosts/{name}` | manager-gui | remove host |
| POST | `/api/hosts/persist` | manager-gui | persist configuration to server.xml |
| GET | `/api/apps?host={name}` | manager-gui | list contexts: path, version, displayName, state, sessions, docBase, self |
| POST | `/api/apps/{path}/start` | manager-gui | start context (`?version=`) |
| POST | `/api/apps/{path}/stop` | manager-gui | stop context |
| POST | `/api/apps/{path}/reload` | manager-gui | reload context |
| DELETE | `/api/apps/{path}` | manager-gui | undeploy context |
| POST | `/api/apps/{path}/expire` | manager-gui | expire idle sessions: `{"idle": 30}` |
| POST | `/api/apps/deploy` | manager-gui | deploy from server: `{path, version, config, war, replace}` |
| POST | `/api/apps/upload` | manager-gui | multipart WAR upload: `war`, `path`, `version`, `replace` |
| GET | `/api/apps/{path}/sessions?sort=&order=&cursor=` | manager-gui | session list, server-side sorted, cursor-paginated |
| GET | `/api/apps/{path}/sessions/{id}` | manager-gui | session detail incl. attributes |
| POST | `/api/apps/{path}/sessions/invalidate` | manager-gui | `{"ids": [...]}` |
| DELETE | `/api/apps/{path}/sessions/{id}/attributes/{name}` | manager-gui | remove one session attribute |
| GET | `/api/status` | manager-gui, manager-status | compact live snapshot (see below) |
| GET | `/api/status/workers` | manager-gui, manager-status | live per-socket (RequestProcessor) table |
| GET | `/api/status/system` | manager-gui, manager-status | instant CPU and memory snapshot (cores, CPU loads, load average, thread counts; physical memory, swap, heap, non-heap, memory pools) |
| GET | `/api/status/apps/{path}` | manager-gui, manager-status | detailed per-app: state, times, sessions, JSPs, servlets |
| GET | `/api/ssl/ciphers` | manager-gui | SSL ciphers per connector |
| GET | `/api/ssl/certs` | manager-gui | SSL certs per connector |
| GET | `/api/ssl/trusted` | manager-gui | SSL trusted certs per connector |
| POST | `/api/ssl/reload` | manager-gui | `{"tlsHostName": ...}` (omit = all) |
| GET | `/api/leaks` | manager-gui | reloaded-context memory leak candidates |
| GET | `/api/resources?type={fqcn}` | manager-gui | global JNDI resources as `name:class` lines (the human readable status header the classic manager renders first is stripped) |
| GET | `/api/diagnostics/vminfo` | manager-gui | VM info text |
| GET | `/api/diagnostics/threaddump` | manager-gui | thread dump text |
| GET | `/api/logs` | manager-gui | server log files (JULI): name, size, modified, detected format (text/JSON) |
| GET | `/api/logs/file?name=&lines=&level=&search=` | manager-gui | parsed + filtered tail of one server log file: the most recent `lines` (default 500) matching records, ordered most recent first (time, level, thread, source, message, throwable), level counts |
| GET | `/api/logs/download?name=` | manager-gui | the full, unfiltered raw server log file (`Content-Disposition: attachment`) |
| GET | `/api/access-log` | manager-gui | access log files: name, size, modified, format, configured pattern, available fields |
 | GET | `/api/access-log/file?name=&lines=&method=&status=&user=&session=&search=` | manager-gui | parsed + filtered tail of one access log file: records, status-class counts, method counts |
 | GET | `/api/access-log/download?name=` | manager-gui | the full, unfiltered raw access log file (`Content-Disposition: attachment`) |
 | GET | `/api/users?name=` | manager-gui | configured `UserDatabase` JNDI resources (name, id, type, readonly, writable) plus users (username, fullName, hasPassword, roles, groups, effectiveRoles), groups (groupname, description, roles, members) and roles of the selected database |
 | POST | `/api/users` | manager-gui | `{"username", "password", "fullName"?, "roles"?, "name"?}` create a user (409 when it exists); new roles are created on the fly |
 | DELETE | `/api/users/{username}?name=` | manager-gui | remove a user (404 when absent; 400 `SELF_REMOVAL` for the signed-in account) |
 | POST | `/api/users/{username}/password` | manager-gui | `{"password", "name"?}` change a user's password (stored as provided, same semantics as `tomcat-users.xml`) |
 | POST | `/api/users/{username}/roles` | manager-gui | `{"roles", "name"?}` replace the roles of a user |
 | POST | `/api/groups` | manager-gui | `{"groupname", "description"?, "roles"?, "name"?}` create a group (409 when it exists) |
 | DELETE | `/api/groups/{groupname}?name=` | manager-gui | remove a group and detach it from all users |
 | POST | `/api/groups/{groupname}/members` | manager-gui | `{"members", "name"?}` replace the members of a group (400 `UNKNOWN_GROUP_MEMBER` when a listed user does not exist) |
 | POST | `/api/groups/{groupname}/roles` | manager-gui | `{"roles", "name"?}` replace the roles of a group |
  | POST | `/api/roles` | manager-gui | `{"rolename", "description"?, "name"?}` create a role (409 when it exists) |
  | DELETE | `/api/roles/{rolename}?name=` | manager-gui | remove a role and detach it from all users and groups (404 when absent; 400 `SELF_ROLE_REMOVAL` when the signed-in account holds the role, directly or through a group) |
  | GET | `/api/config/tree` | manager-gui | the live component tree below `Server`: `{"tree": {id, type, className, name, state?, self?, children[]}}`; `self: true` on the context hosting this webapp; a context carries its `manager` (with the manager's `sessionIdGenerator`), `resources`, `loader` and `cookieProcessor` as children (a running context always has all of them); the `Server` and each context carry a single `namingResources` node (the `NamingResourcesImpl`) whose children are the JNDI entries, keyed by JNDI name — `resource`, `resourceLink` (context only), `resourceEnvRef`, `environment`, `ejb`, `localEjb`, `serviceRef`; an engine, host or context that owns a cluster carries a single `cluster` child (an inherited parent cluster is not a child) whose children are the `channel` (holding `membership`, `sender` — with a `transport` child for a replication transmitter —, `receiver` and the repeatable `interceptor` nodes), the repeatable `clusterValve`, the `clusterManager` (with its `sessionIdGenerator`), the repeatable `clusterListener` and the repeatable `listener` |
| GET | `/api/config/node/{id}` | manager-gui | one node: `id`, `type`, `name`, `className`, `state`, `self`, `lifecycle` (whether the component implements `Lifecycle` and therefore supports the start/stop/restart operations), `affectsSelf` (whether a start or stop of the component would interrupt access to this web application itself — the server, the service/engine/host/context that route it, the connector that serves it and the wrappers of its context; only a restart is allowed for it), `acceptsListener` (whether a lifecycle listener can be added), `acceptsSubRealm` (realm nodes only: whether the realm is a `CombinedRealm` and can hold sub realms), `sslEnabled` (connector nodes) / `isDefault` (SSL host config nodes), `global` (naming resources nodes: `true` for the server, `false` for a context), the bean's attributes as `properties[]` (`name`, `type`, `description`, `writable`, `value`, and `param` for the free-form JNDI entry parameters) and a `children[]` summary. Every JNDI entry type lists the string parameters it has set (the `ResourceBase` property map, the RefAddr keys the JNDI factories consume) as `param` properties; a JNDI `resource` additionally lists the closed option set (RefAddr keys) of its effective first-party factory (the explicit `factory` parameter, or the factory `ResourceFactory` dispatches the type to, e.g. `javax.sql.DataSource` → `BasicDataSourceFactory`) as `param` properties |
| POST | `/api/config/attribute` | manager-gui | `{"id", "name", "value", "confirm"?}` — set one writable property on the live component (the change takes effect immediately); `name`/`path`/`defaultHost` additionally require `confirm` to equal the current value; a TLS attribute of a running, TLS enabled connector is re-validated by reloading the affected host configuration and the previous value is restored (400 `UPDATE_FAILED`) when the new value does not validate. For a JNDI entry, editing an attribute (or a `param` — an empty value removes it) is applied by removing and re-adding the entry (the `NamingContextListener` reacts to the property-change events to rebind the live JNDI environment); the previous state is restored (400 `UPDATE_FAILED`) when the re-add fails (e.g. the new JNDI name is already in use) |
 | POST | `/api/config/child` | manager-gui | `{"parent", "type", ...}` — add a `service` (created together with an engine of the same name), `host`, `context` (docBase auto-created), `wrapper`, `valve`, `connector`, `executor`, `alias`, `realm` (any container, or a `CombinedRealm` for a sub realm; instantiated from `className`), a context sub component — `manager`, `resources`, `loader` or `cookieProcessor` (parent: a context) or `sessionIdGenerator` (parent: a manager; all instantiated from `className` and **replacing** the current instance, since a context/manager holds exactly one of each) — or `listener` (any parent whose component implements `Lifecycle`; instantiated from `className` and registered, not started); a `cluster` (parent: an engine, host or context) is instantiated from `className` (default `SimpleTcpCluster`) and attached via `setCluster`, which starts the channel and applies the cluster defaults (the addition is rolled back + 400 `START_FAILED` when it cannot start), and its sub components are added by `className` with a sensible default — `channel` (default `GroupChannel`, replacing the current one), `membership` (default `McastService`, parent: the channel), `sender` (default `ReplicationTransmitter`, parent: the channel), `receiver` (default `NioReceiver`, parent: the channel), `interceptor` (parent: the channel, repeatable), `clusterValve` (a `Valve` that is a `ClusterValve`, parent: the cluster, repeatable; 400 `INVALID_CLASS` otherwise), `deployer` (default `FarmWarDeployer`, parent: the cluster), `clusterManager` (default `DeltaManager`, parent: the cluster), `transport` (default `PooledParallelSender`, parent: the sender) and `clusterListener` (parent: the cluster, repeatable); the single-valued slots replace the current instance; structural components are started immediately and the addition rolled back when the start fails. Replacing a context's `manager` or `loader` on a running context stops the old instance and starts the new one (rolled back + 400 `START_FAILED` when it does not start); replacing the `resources` of a running context is refused (400 `CONTEXT_RUNNING` — stop the context first); replacing the `manager` or `loader` of this webapp's own context is refused (403 `SELF_COMPONENT` — it would destroy the admin session / the running classes). A `sslHostConfig` (parent: a connector) optionally carries an initial `certificate` object; on a running connector the TLS configuration is validated and applied without a restart (400 `ADD_FAILED` + rollback otherwise); the first certificate is required for a running connector (400 `INVALID_VALUE`). A further `certificate` is added with the crypto type in the `type` field (`RSA`, `DSA`, ...). An `upgradeProtocol` (parent: a connector) is instantiated from `className` (default `org.apache.coyote.http2.Http2Protocol`, the only `UpgradeProtocol` shipped with Tomcat) and registered through `AbstractHttp11Protocol.addUpgradeProtocol`; a connector whose protocol handler is not the HTTP/1.1 variant does not accept one (400 `BAD_PARENT`), a class that is not an `UpgradeProtocol` is 400 `INVALID_CLASS`, a second protocol with the same name (e.g. a second `h2`) is 409 `DUPLICATE`; an upgrade protocol is only referenced when the connector is initialised, so no live activation is attempted — the protocol becomes active the next time the connector is restarted. A JNDI entry (`parent`: a `namingResources` node) — `resource`, `resourceLink` (refused at the server level, 400 `BAD_PARENT`), `resourceEnvRef`, `environment`, `ejb`, `localEjb` or `serviceRef` — requires `name` and `jndiType`; a `resourceLink` additionally requires `global`; a `factory` (a `resource` parameter / `resourceLink` attribute) must be loadable (400 `INVALID_CLASS`); a `params` object carries the entry's generic string parameters (the `ResourceBase` property map) for any entry type — validated against the closed option set of a first-party factory for a `resource`, free-form otherwise; a JNDI name already in use is 409 `DUPLICATE`, a missing `jndiType` 400 `MISSING_FIELD`; the entry is registered and bound in the live JNDI environment at once |
 | DELETE | `/api/config/child` | manager-gui | `{"id", "confirm"?}` — remove a component (containers are stopped recursively); `confirm` must equal the component's display name for `host`/`context`/`service`/`engine`/`connector`/`executor`/`wrapper`/`valve`/`sslHostConfig`/`upgradeProtocol`/`realm`/`cluster` and for the JNDI entries (`resource`/`resourceLink`/`resourceEnvRef`/`environment`/`ejb`/`localEjb`/`serviceRef`, whose JNDI name is unbound from the live JNDI environment); 400 `LAST_SERVICE`, 403 `SELF_COMPONENT`, 400 `BASIC_COMPONENT`, 400 `NOT_EMPTY`, 400 `LAST_REALM` (the container would be left without a realm), 400 `REQUIRED_COMPONENT` (the context's `manager`/`resources`/`loader`/`cookieProcessor`, a manager's `sessionIdGenerator` and the `namingResources` node itself are required and cannot be removed), 400 `SSL_DEFAULT` and 400 `SSL_LAST_CERTIFICATE` guards apply; removing the last SSL host configuration of a running, TLS enabled connector switches it back to plain HTTP; a `cluster` (which stops the cluster and its channel) and its single-valued children (`channel`, `membership`, `sender`, `receiver`, `deployer`, `clusterManager`, `transport`, `clusterListener` and the static `member`) can be detached from their parent, but a `clusterValve` or `interceptor` has no removal API on a running cluster and is refused (400 `REMOVE_NOT_SUPPORTED`) |
  | POST | `/api/config/lifecycle` | manager-gui | `{"id", "op"}` — `start`, `stop` or `restart` one component (a restart is a stop, when the component is running, followed by a start); not every change takes effect until the affected component is restarted, so the operation makes the restart explicit; 400 `NOT_A_LIFECYCLE` when the component does not implement `Lifecycle`, 400 `INVALID_OP` for an unknown operation, 400 `START_FAILED` / `STOP_FAILED` when the component does not start (stop), 403 `SELF_COMPONENT` for a `start` or `stop` of a component that would interrupt access to this web application itself (the server, the service/engine/host/context that route it, the connector that serves it and the wrappers of its context) — a `restart` of such a component remains allowed: the client's connection may be interrupted during the operation, but the component is running again at the end and the client reconnects (re-logging in when the admin session was reset by the restart) |
  | GET | `/api/config/store/preview` | manager-gui | `{"xml", "files", "restartsManager"}` — the resulting `server.xml`, the external context files that would be rewritten, and whether the save restarts the manager; read-only, the external files are captured in memory and nothing is written |
 | POST | `/api/config/store` | manager-gui | persist the live state (`{ok, file, backup}`): a timestamped backup of the previous `conf/server.xml` is kept and each context is written back to its current location — inline in `server.xml` if defined inline, its own file otherwise (mirroring regular StoreConfig) |

Node ids are path-based: `server/service/Catalina/engine/Catalina/host/localhost/context/+manager2`.
Each segment is the component's name; a context's path is encoded (`/` →
`+`, the root context is a bare `+`). Clients percent-encode each segment
when building a URL so the container's single path decode yields the
(id) segments the server expects.


`GET /api/status` (the poll endpoint, kept small — typically 1–2 KB):

```json
{
  "ts": 1726012345678,
  "jvm": {
    "heapUsed": 201326592, "heapCommitted": 402653184, "heapMax": 805306368,
    "nonHeapUsed": 117440512,
    "pools": [ { "name": "G1 Eden Space", "type": "GENERATION",
                 "init": 0, "committed": 285212672, "max": -1, "used": 184549376 } ]
  },
  "connectors": [
    { "name": "http-nio-8080",
      "threads": { "max": 200, "current": 10, "busy": 3, "keepAlive": 2 },
      "requests": { "count": 1520, "errors": 4,
                    "bytesReceived": 912345, "bytesSent": 8923456,
                    "processingTime": 1450, "maxTime": 480 } }
  ],
  "apps": [ { "path": "/docs", "host": "localhost", "state": "RUNNABLE",
              "activeSessions": 7 } ]
}
```

Rate values (requests/s, bytes/s) are computed client-side from counter
deltas between polls; the server exposes monotonic counters and the
server-side timestamp, so restarts and clock skew are handled.

## 5. User interface

### 5.1 Shell

- Top bar: Tomcat logo (the `tomcat.svg` mascot from the root webapp, also
  used as `favicon.ico` and on the login page) + product name, server
  identity (version, host, uptime), global search, user menu (logout),
  theme toggle.
- Left navigation (collapses to bottom tab bar under 900 px — slightly
  above the old 768 px breakpoint so iPad portrait and small tablets get
  the phone layout): Dashboard,
  Applications, Hosts, Configuration, Users, Monitoring, Diagnostics,
  Logs, Access log. Each item has a single-path 24×24 icon (holes — server
  LEDs/slots, the gear bore, beetle seam/spots, file text lines — filled
  with `fill-rule: evenodd`); the active item takes a per-tab accent hue
  (dashboard blue, apps violet, hosts teal, configuration slate, users
  pink, monitoring green, diagnostics amber, logs cyan, access log indigo,
  with lighter values in the dark theme) on icon, label and soft
  background. The navigation is always an icons-only 60 px rail (the
  labels are kept as `aria-label`): a 220 px rail of text is too costly at
  every width, and nine labels are not legible at phone widths. In
  landscape viewports, hovering the rail — or landing keyboard focus in
  it — pops the sidenav out at full width (220 px) and label over the
  content as a `position: fixed` flyout; the content is pinned to the
  second grid column, so the grid and the view are never resized. Portrait
  viewports keep the rail collapsed, and the bottom tab bar is icons-only
  as well.
- History-API routing (deep links work), one `index.html`, no full page
  reloads. `login.html` is the FORM-login page (see §7); it POSTs to
  `j_security_check` and redirects back to the original URL.

### 5.2 Pages

**Dashboard** (default view)
- KPI cards: heap used (gauge vs max), busy threads (gauge vs max) per
  connector, total active sessions, request rate, error rate.
- Live line charts (2 s cadence, ~5 min rolling window): heap used /
  committed, busy threads per connector, request rate, error rate,
  bytes in/out per connector. The rate / bytes series are derived from
  counter deltas, so the first sample (no baseline yet) is not plotted -
  those charts start with the first measurable rate instead of an
  inaccurate 0.
- Context state strip: one dot per application (green running / red stopped
  / grey), click-through to Applications.

**Applications**
- Table: host, context path (link), version, display name, state, active
  sessions, docBase. Filter box, sortable columns.
- Row actions: start / stop / reload / undeploy (confirm modal, undeploy
  asks for typing the context path), sessions.
- Deploy wizard (modal, two tabs): *Upload* (drag-and-drop or file picker,
  progress bar, optional version + replace) and *From server* (path,
  version, WAR file/dir, context config file, replace).
- Detail view (route `/apps/{host}/{path}`) with tabs:
  - *Overview*: state, paths, version, links.
  - *Sessions*: sortable table (creation time, id, last accessed, max
    inactive interval, new, locale, user, used time, inactive time, TTL),
    row click opens a drawer with attributes (remove attribute, invalidate
    session), bulk invalidate, "expire idle ≥ N minutes".
  - *Metrics*: per-servlet table (request count, processing time, max time,
    error count, load time, class-load time), JSP stats, session manager
    stats, startup/TLD-scan times.

**Hosts**
- Table: name, aliases, appBase, state.
- "Add host" modal with all parameters from the legacy add form
  (name, aliases, appBase, manager, autoDeploy, deployOnStartup,
  deployXML, unpackWARs, copyXML).
- Row actions: start/stop/remove (own host shown as protected, as today).
- "Persist configuration to server.xml" button with confirmation.

**Configuration**
- Two-pane layout (component tree card + detail card, stacked below
  1100 px). Header: *Reload* and *Save to server.xml*.
- Tree: the live component tree below `Server` (services, engines,
  hosts, contexts, wrappers, valves, connectors, executors, aliases,
  listeners, realms, the context sub components (manager, resources,
  loader, cookie processor; the manager's session id generator under
   the manager), the TLS branch of a connector (SSL host
   configurations with their certificates), and the upgrade protocols
   of a connector). Each row: a type badge, the name, a state badge
   for lifecycle components, and a "this app" badge on the context
   hosting this webapp. Nodes expand/collapse; the
  `Server` node starts expanded. A container only shows the realm it
  owns; an inherited parent realm is not a child (the same
   comparison storeconfig uses to decide whether to write a `<Realm>`
   element). A `realm` node shows its sub realms when it is a
   `CombinedRealm` (e.g. a `LockOutRealm` wrapping a
   `UserDatabaseRealm`). A running context always shows its four sub
   components (the defaults — `StandardManager`, `StandardRoot`,
   `WebappLoader`, `Rfc6265CookieProcessor` — are created at context
   start). The `Server` and each context also show a `namingResources`
   node (the JNDI environment, `NamingResourcesImpl`) whose children are
   the JNDI entries, shown under their JNDI names. An engine, host or
   context that owns a cluster shows a `cluster` node (an inherited
   parent cluster is not a child); the cluster shows its `channel` (with
   the `membership`, `sender` + `transport`, `receiver` and the
   repeatable `interceptor` nodes), the repeatable `clusterValve`, the
   `clusterManager`, and the repeatable `clusterListener` and `listener`
   nodes. The channel's sub components (`GroupChannel`, the
   `McastService` membership, the `NioReceiver`, the `PooledParallelSender`
   transport and the interceptors) have no modeler descriptor, so their
   common knobs are exposed through an explicit attribute list (the same
   mechanism as the TLS components).
- Detail: the class name, the component's attributes rendered as an
  editable property list (boolean → checkbox, numeric → numeric input,
  `String` → text input, `String[]` → comma separated input; non
  writable attributes shown read-only) with a per-property *Apply*,
  and a children chip row for drilling down. Changes are applied
  immediately to the running server; writing back the current value is
  suppressed client-side ("No changes to apply."). The attributes
  `name`, `path` and `defaultHost` are treated as risky and require a
  type-to-confirm.
- *Lifecycle*: components that implement `Lifecycle` (the node detail
  reports this as `lifecycle`) show **Start** / **Stop** / **Restart**
  buttons in the detail card header, since not every change takes
  effect until the affected component is restarted. Start is disabled
  while the component is running; Stop while it is stopped. Start and
  Stop are additionally disabled (Restart stays enabled) for the
  components that would interrupt access to this page (the node detail
  reports this as `affectsSelf`: the server, the service/engine/host/
  context that route it, the connector that serves it and the wrappers
  of its context). Restarting such a component interrupts the
  connection mid-operation; the page then polls the server back,
  reconnects, and re-logs in when the restart reset the admin session
  (server, service, engine, host or the context itself). Stop asks for
  a plain confirmation; Restart for typing the component's display
  name.
- *+ Add* (shown for nodes that can have children) opens a modal with
    the child types valid for the selected node (server → service;
    service → connector, executor; engine → host, realm, valve, cluster;
    host → context, alias, realm, valve, cluster; context → wrapper,
    realm, manager, resources, loader, cookieProcessor, valve, cluster;
    manager → sessionIdGenerator; connector → sslHostConfig,
    upgradeProtocol; sslHostConfig → certificate; cluster → channel,
    deployer, clusterValve, clusterManager,
    clusterListener; channel → membership, sender, receiver, interceptor;
    sender → transport; clusterManager → sessionIdGenerator;
    namingResources → resource, resourceLink (context only),
    resourceEnvRef, environment, ejb, localEjb, serviceRef; and a
    `CombinedRealm` node → realm for a sub realm) and
  the type's fields. In addition, a `listener` type is offered for
  every node whose component implements `Lifecycle` (all structural
   types except `alias`, and except the cluster's sub components — only
   the `cluster` node itself, not its `channel`/`clusterManager`/...,
   offers a `listener`; for a `listener` node the server reports this
  as `acceptsListener`, since a listener's class may or may not be a
  `Lifecycle`; likewise a realm node offers the `realm` child only when
  it reports `acceptsSubRealm`, since only a `CombinedRealm` holds sub
  realms). A `valve`, `listener` or `realm` is instantiated from the
  class name (public no-arg constructor, server class loader) and
  registered with the parent; unlike the structural types a `listener`
  is not started. A realm added to a container is started by the
  container (which wires it up and drives its lifecycle); a sub realm
  is given the combined realm's container and a distinct realm path
  (for JMX naming) and started while the combined realm is running.
  Nesting is capped at 3 realm levels, the same bound the XML parser
  applies. Structural components are started immediately; when the
  start fails the addition is rolled back server-side and a controlled
  error is returned.
- *TLS*: adding an `sslHostConfig` to a running connector switches it
  to TLS without a restart: the endpoint's SSL implementation is
  initialised from the configuration (validating the certificates) and
  new connections handshake immediately (`createChannel` decides per
  connection). The certificate's keystore is validated before the
  change is applied; an invalid keystore is rejected and nothing is
  changed. Editing a TLS attribute on a running, TLS enabled connector
  reloads the affected host configuration at once and reverts the
  value when the new one does not validate. Removing the last SSL host
  configuration switches the connector back to plain HTTP; because
  NIO channels are pooled, the pool is emptied when the SSL flag
  changes so no stale (in)secure channel is handed out.
 - *Upgrade protocols*: adding an `upgradeProtocol` to a connector
   registers it through `AbstractHttp11Protocol.addUpgradeProtocol`.
   The default class is `org.apache.coyote.http2.Http2Protocol` (the
   only `UpgradeProtocol` shipped with Tomcat); the form field carries
   the class name, a connector whose protocol handler is not the
   HTTP/1.1 variant does not accept one (400 `BAD_PARENT`), and a
   second protocol with the same name is refused (409 `DUPLICATE`).
   An upgrade protocol is only referenced when the connector is
   initialised, so the addition does not change a running connector
   and no live activation is attempted: the protocol becomes active
   the next time the connector is (re)started. The HTTP/2 protocol
   exposes its settings as editable properties (the timeouts,
   `maxConcurrentStreams`, `maxConcurrentStreamExecution`,
   `initialWindowSize`, the header/trailer limits, the overhead frame
   tracking factors and thresholds, `useSendfile`,
   `allowSchemeMismatch`, `initiatePingDisabled`,
   `discardRequestsAndResponses` and `drainTimeout`; the list is
   explicit, the class has no modeler descriptor — the same mechanism
   as the TLS components); setting changes take effect when the
   connector is (re)started, like the protocol itself.
   `maxHeaderSize` and `maxTrailerSize` are shown read-only (they are
   set on the HTTP/1.1 protocol handler).
 - *Realms*: a container holds at most one realm of its own (adding a
   second is 409 `DUPLICATE`). Removing a directly attached realm is
   only allowed when the container falls back to a parent realm
   (400 `LAST_REALM` otherwise — an engine always keeps a realm). A sub
   realm is removed from its combined realm and stopped. Realm
   attributes (e.g. `allRolesMode` of a `MemoryRealm`) are edited
   through the same property list; invalid values are rejected by the
   realm's own setter (400 `SET_FAILED`).
 - *Context sub components*: a context always holds exactly one
   `manager`, one `resources` root, one `loader` and one
   `cookieProcessor` (the defaults are created at context start), and a
   manager holds its `sessionIdGenerator`. "Add" for these types is
   therefore a **replace**: the current instance is stopped (where it
   has a lifecycle) and a new instance of the chosen class is wired in
   and started (on a running context/manager; rolled back +
   400 `START_FAILED` when it does not start). Replacing the `resources`
   of a running context is refused (400 `CONTEXT_RUNNING`) — the
   context must be stopped first. Replacing the `manager` or `loader`
   of this webapp's own context is refused (403 `SELF_COMPONENT`).
   These components are required and cannot be removed (400
   `REQUIRED_COMPONENT`) — only replaced. Their attributes are edited
   through the same property list: the `manager` (`StandardManager`)
   and the `resources` (`StandardRoot`) through their modeler
   descriptor; the `loader` (`WebappLoader`), the `cookieProcessor`
   (`Rfc6265CookieProcessor`) and the `sessionIdGenerator`
    (`StandardSessionIdGenerator`) have no modeler descriptor, so their
    attribute list is defined explicitly by the servlet (the same
    mechanism as the TLS components).
  - *JNDI naming resources*: the `namingResources` node of the `Server`
    (global, `<GlobalNamingResources>`) and of a context (the context's
    JNDI environment) exposes its JNDI entries — `resource`,
    `resourceLink` (context only; not part of the global environment),
    `resourceEnvRef`, `environment`, `ejb`, `localEjb` and `serviceRef` —
    each keyed by its JNDI name. The `namingResources` node itself is
    required and cannot be removed (400 `REQUIRED_COMPONENT`); the
    entries are added/removed and the node's `global` flag drives the
    client (a server node offers no `resourceLink`). Adding an entry
    registers and binds it in the live JNDI environment at once (the
    `NamingContextListener` reacts to the add); removing one unbinds it.
    An entry's attributes are edited through the property list. Every
    entry type extends `ResourceBase`, which carries a generic map of
    string parameters (the RefAddr keys the JNDI factories consume at
    lookup time); those parameters are shown in the entry detail for
    **all** the entry types and can be added (an "Add parameter" form),
    edited, or removed (cleared) from the property list. For a
    `resource` the closed option set (RefAddr keys) of its effective
    first-party factory is additionally rendered as typed fields — the
    four shipped factories (`BasicDataSourceFactory`,
    `MemoryUserDatabaseFactory`, `DataSourceUserDatabaseFactory`, and the
    `PerUserPool`/`SharedPool` data sources) render their options as
    typed fields, while any other factory (or an extra key) is a
    free-form parameter. Editing an attribute or a `param` re-binds the
    entry by removing and re-adding it (so the change is visible to the
    live JNDI environment without a restart); renaming a JNDI name is a
    risky change (type-to-confirm).
  - *Remove* asks for typing the component's display name and is
   disabled for the self component. Server-side guards additionally
   reject: the last service (`LAST_SERVICE`), the service/engine/host/
   context hosting this webapp (`SELF_COMPONENT`), the basic (first)
   valve of a pipeline (`BASIC_COMPONENT`), an engine that still
   contains hosts (`NOT_EMPTY`), a directly attached realm whose
   container would be left without a realm (`LAST_REALM`), a context's
   `manager`/`resources`/`loader`/`cookieProcessor` or a manager's
   `sessionIdGenerator` (`REQUIRED_COMPONENT` — required, replace
   instead), the default
   SSL host configuration of a running, TLS enabled connector while
   other configurations remain (`SSL_DEFAULT`) and the last
   certificate of a running, TLS enabled connector (`SSL_LAST_CERTIFICATE`).
 - *Save to server.xml* fetches the preview (the resulting `server.xml`
   plus the external context files that would be rewritten, shown in a
   wide modal) and, on confirmation (typing `server.xml`), persists the
   live state: a timestamped backup of the previous `server.xml` is
   kept. Contexts keep their current storage location, mirroring regular
   StoreConfig behaviour — a context backed by its own file
   (`META-INF/context.xml` or `conf/Catalina/.../context.xml`) is written
   back to that file, a context defined inline stays inline in
   `server.xml`. The preview is read-only (external files are captured in
   memory, never written). A context restarts when its own file is
   written, so a save that rewrites this webapp's own file restarts the
   manager and resets the admin session; the dialog warns about this when
   it applies (`restartsManager` in the preview response).

**Users**
- Manages the users, groups and roles of the `UserDatabase` JNDI resources
  configured for the server (the default `server.xml` configures the file
  based `MemoryUserDatabase` that backs `conf/tomcat-users.xml`). Databases
  are discovered through the server's global JNDI naming context; when more
  than one is configured a selector in the header chooses which to operate
  on (all API calls accept a `name` field/parameter).
- Header card: database type, `read-only` / `not writable` / `writable`
  badge, and the JNDI name (plus id).
- When the database is read-only a warning banner explains that
  `readonly="false"` must be set on its `Resource` in `server.xml`, and all
  mutation controls are disabled.
- Users table: user name (+ full name), roles (badges; roles inherited
  through groups shown dimmed with a `+`), groups, row actions *Roles* /
  *Password* / *Remove* (remove asks for typing the user name). "Add user"
  modal: user name, password, full name, roles (comma separated with
  suggestions).
- Groups table: group name, roles, members, row actions *Members* / *Roles* /
  *Remove*. "Add group" modal: group name, description, roles.
- Roles table: role name, description, the users and groups that hold the
  role, row action *Remove* (removal asks for typing the role name and
  detaches the role from all users and groups). "Add role" modal: role name,
  description. Roles can also still be created implicitly when assigned to a
  user or group; removing the last user or group that uses a role leaves the
  (now unused) role defined, matching `UserDatabase` semantics.
- Security notes: passwords are stored exactly as provided (same semantics as
  the `password` attribute of `tomcat-users.xml`) and are never returned by
  the API; the signed-in account cannot remove itself, nor a role it holds
  (directly or through a group); group members must
  exist; name values are restricted to a safe character set because they are
  persisted in comma separated lists and used as URL path segments. Every
  mutation is followed by `save()` so the change is persisted to the storage
  of the database.

**Monitoring**
- CPU and memory status cards (5 s cadence): an instant snapshot from
  `GET /api/status/system`, not a chart. The CPU card shows the system and
  JVM process CPU load as percentage bars, plus cores, 1-minute load
  average and live/daemon/peak thread counts; the memory card shows
  physical memory and swap usage, the JVM heap (used / max bar, committed)
  and non-heap usage as bars and facts, and a per-memory-pool table
  (used, committed, max). Values the platform does not expose (CPU loads
  before the first monitoring interval, the load average on Windows,
  physical/swap memory on a JVM without the HotSpot management MBean)
  render as "-".
- Live workers table (5 s cadence): stage, processing time, bytes
  sent/received, remote address (forwarded + actual), virtual host,
  request line. Only *active* workers are listed — stages P (parsing,
  blue), S (service, green) and F (finishing, amber), each with its own
  bullet colour; R (ready), K (keep-alive) and unknown stages are idle
  one way or another and are left out so the table stays short; the
  header badge reports "N active · X idle".
- Connector detail cards with the same data as the Dashboard, plus
  max-processing-time history chart.

**Diagnostics**
- SSL: ciphers / certs / trusted certs tables per connector; "reload SSL
  host configs" (all, or single `tlsHostName`).
- Memory leaks: "check now" button, list of suspect contexts.
- JNDI resources: type filter, tree.
- VM: VM info, thread dump (monospace viewer, download as .txt).

**Logs**
- File selector over the JULI server log files (`catalina`, `localhost`,
  `manager`, `host-manager`, `catalina.out`), max-lines selector (500-5000),
  refresh and download buttons (the download saves the full, unfiltered raw
  file of the current selection).
- Filters: severity (the levels actually present in the file) and free-text
  search.
- Table: time, level (coloured badge), thread, source, message. Row click
  opens a drawer with the full record, including the stack trace.
- Both the plain one-line format and the JSON log format
  (`org.apache.juli.JsonFormatter`) are handled; the format is detected per
  file from the first line.

**Access log**
- File selector over the access log files, max-lines selector, refresh and
  download buttons (the download saves the full, unfiltered raw file of the
  current selection).
- Filters that are shown depend on the fields the configured format
  provides: method, status (class `1xx`-`5xx` or exact code), user, session
  ID and free-text search. A filter is only offered when its field is part
  of the configured format (e.g. no session-ID filter when the pattern has
  no `%S`).
- The pattern based format is parsed with the pattern of the configured
  `AccessLogValve` (falling back to the common and combined patterns when no
  valve is configured), and the JSON format
  (`org.apache.catalina.valves.JsonAccessLogValve`) as one JSON object per
  line. Both formats expose the same field names, and the method / path /
  query / protocol are derived from the request line when only `%r` is
  logged.
- Table: host, user, time, the method / path / query / protocol columns
  (derived from `%r` when the format logs only the request line), status
  (coloured badge), size, session ID (when logged). The raw request line
  is not a column — it repeats the derived columns and is by far the
  widest; it stays available in the record drawer, which a row click
  opens with the full record.

### 5.3 Design system

- Plain CSS with custom properties (no framework); light + dark themes
  (system preference default, manual toggle, persisted in
  `localStorage`).
- System font stack; 12-column grid; 8 px spacing scale; single accent
  colour; states (success/warning/danger) with colour + icon (never colour
  alone). The accent is otherwise neutral: per-tab hues are reserved for
  the active navigation item (see §5.1) so the current section is
  recognisable at a glance, and are never used for content.
- Responsive. Phone mode below 900 px (bottom tab bar, management tables
  (applications,
  hosts, users, sessions, connectors, servlets, …) convert to stacked
  cards, each cell labelled with its column header; kebab menus, full-width
  forms and modals) — 900 px rather than the classic 768 px so iPad
  portrait and small tablets get it too. Independently, high-volume tables
  (server logs, access log, active workers) get the scrollable treatment —
  size to content so columns are never squeezed or wrapped, first column
  pinned while the table scrolls horizontally — up to 1150 px, because
  below that even the thirteen access-log columns do not fit without
  squeezing and a horizontal scroll is the better trade; above it the
  ordinary desktop tables take over (which also keeps ordinary desktop
  windows, however short, on the desktop treatment). The log pages never
  pin their first column at any width in that range — the pinned Time
  column would dominate the viewport and leave a sliver for the scrolling
  content, so the whole log table scrolls.
   The log pages' long text columns (messages) are given most of
  the viewport (90vw portrait, 100vw landscape) because with
  `table-layout:auto` any extra table width flows into the only wrapping
  column, keeping rows to one or two lines for information density.
   Charts re-flow to a single column; row actions move into an overflow
   (kebab) menu; form fields and modals go full width.
- The side navigation is an icons-only 60 px rail at all widths where it
  is shown; in landscape viewports it pops out at full width and label on
  hover/focus as a fixed flyout that never resizes the view (see §5.1).
- Accessibility (WCAG 2.1 AA): semantic landmarks, visible focus states,
  keyboard-operable modals/drawers (focus trap, `Esc` closes),
  `aria-live="polite"` toasts, live chart updates announced at reduced
  frequency, contrast ≥ 4.5:1.

## 6. Live updating

- **Default transport: HTTP polling.** Dashboard 2 s, Monitoring 5 s,
  Applications list 10 s; all polling pauses when `document.hidden` and
  resumes on focus. Intervals are servlet init-params
  (`pollIntervalStatus`, …) and client-configurable.
- **Why not WebSockets/SSE first:** polling is proxy/LoadBalancer-safe,
  stateless, and the payload is tiny; the MBean attribute reads done per
  poll are the same ones the legacy status page did per full page load.
  A `GET /api/stream` (Server-Sent Events) endpoint is specified as an
  optional future extension behind an init-param.
- **Charting:** hand-written canvas module (`js/charts.js`): rolling
  ring-buffer per series, auto-scaling y-axis, 1 s/5 s/15 min window
  switcher, hover readout, optional log scale for byte counters. Line
  charts, area charts and gauges only — no external chart library.
- **Data integrity:** every snapshot carries `ts`; the client discards
  out-of-order duplicates and renders a gap marker if a poll is missed.
  Counter deltas that go backwards (JVM/connector restart) reset the rate
  calculation instead of producing negative rates.

## 7. Security

### 7.1 Authentication — HTTP FORM

`WEB-INF/web.xml` (replacing the legacy `BASIC` login-config):

```xml
<login-config>
  <auth-method>FORM</auth-method>
  <realm-name>Tomcat Manager2 Application</realm-name>
  <form-login-config>
    <form-login-page>/login</form-login-page>
    <form-error-page>/login?error=1</form-error-page>
  </form-login-config>
</login-config>
```

- Works with any configured Realm (default file realm, JDBC, LDAP, …);
  credentials are sent once over the session, not in every request header.
- The login page is rendered by `LoginServlet` (not served as a static
  resource) for three reasons:
  - it injects a `<base>` element, because the page can be displayed at
    arbitrary URLs (the browser keeps the URL of the request that triggered
    the forward) and the relative CSS link would otherwise break;
    - it normalizes the FORM-authentication *saved request* so the
      post-login redirect lands inside the application instead of at the
      last unauthenticated request, which for a single-page application is
      frequently a CSS or JS file or a JSON API call. Concretely: when the
      login page is being rendered for an SPA route (the deep-link gate in
      `HomeServlet` forwards with the route URI still on the request), the
      saved request is pointed at that route, so the user returns to exactly
      the page they were on; any other saved request (an API call, an asset,
      ...) is replaced with a GET of the context root (`/`). The root URL
      (not `/index.html`) keeps the SPA router on its dashboard route;
      `HomeServlet` serves the shell there.
  - it creates the session (and records its ID, mirroring what the
    authenticator does when it changes the session ID) so the login form
    submission is tied to a session even when the browser arrived at the
    login page without one.
- `LoginServlet` also sets the `FormAuthenticator` landing page to the
  context root (`/`) as a safety net for logins without a saved request.
- The session cookie (`JSESSIONID`) is `HttpOnly`; deployments are
  expected to serve manager2 over TLS so the cookie is `Secure`. The
  context uses the RFC 6265 cookie processor with `sameSiteCookies="strict"`
  (see §8).

### 7.2 Authorization — per-endpoint roles

Same role names as the legacy webapps (no new roles to document; a
`manager2-*` naming scheme is an open question, §12):

```xml
<security-constraint>
  <web-resource-collection>
    <web-resource-name>Read-only status</web-resource-name>
    <url-pattern>/api/status</url-pattern>
    <url-pattern>/api/status/*</url-pattern>
    <url-pattern>/api/info</url-pattern>
    <url-pattern>/api/csrf</url-pattern>
  </web-resource-collection>
  <auth-constraint>
    <role-name>manager-gui</role-name>
    <role-name>manager-status</role-name>
  </auth-constraint>
</security-constraint>

<security-constraint>
  <web-resource-collection>
    <web-resource-name>Manager API</web-resource-name>
    <url-pattern>/api/*</url-pattern>
  </web-resource-collection>
  <auth-constraint>
    <role-name>manager-gui</role-name>
  </auth-constraint>
</security-constraint>
```

`manager-status` users get read-only monitoring; `manager-gui` users get
everything.

The SPA shell and its static assets (`/`, `/index.html`, `/css/*`, `/js/*`,
`/img/*`, the login and error pages) are **not** protected with a security
constraint. A constraint with the URL pattern `/` matches *every* request in
the context (not just the context root), so protecting the shell with `/`
would also catch the login page's own CSS and JS: they would be sent through
FORM authentication (leaving the login page unstyled) and would poison the
saved request (sending the browser to a CSS file after login). Instead:

- `HomeServlet` gates the SPA entry point (`/`) and the SPA deep-link routes
  (`/apps`, `/hosts`, `/configuration`, `/users`, `/monitoring`,
  `/diagnostics`, `/logs`, `/access-log`, `/apps/*`): it forwards
  unauthenticated visitors to the login page and authenticated users get the
  shell rendered from `index.html` as a template, preserving the requested
  URL so deep links survive a reload. The template rendering (rather than a
  plain forward to the static file) injects a `<base>` element (the
  `<!-- MANAGER2_BASE -->` placeholder, see `Html`): a *multi-segment* deep
  link such as `/apps/localhost/myapp` would otherwise make the browser
  resolve the shell's relative asset URLs (`js/main.js`, `css/manager2.css`)
  against the deep path (`/apps/localhost/js/main.js`), which the server
  answers with the shell HTML and the browser then refuses as a script.
- The context root *without* a trailing slash (e.g. `/manager2`) is
  redirected (302) to the trailing-slash form by `HomeServlet`. Without the
  redirect the browser would resolve the page's relative URLs (`css/*`,
  `js/*`, `images/*`) against the server root instead of the context,
  breaking the page. The mapper's own context-root redirect
  (`Context#setMapperContextRootRedirectEnabled`, on by default) cannot do
  this here: it only applies when *no* servlet is mapped to the context
  root, but `HomeServlet` is (via the empty URL pattern, which the mapper
  registers as the exact match `/`), so the mapper always finds a wrapper
  before that redirect branch is reached.
- The JSON API is the only place where data lives and the only place that is
  constraint-protected. The SPA itself reacts to an unauthenticated API
  response (the container forwards the XHR to the login page) by performing a
  full navigation to the *current* URL (not the application root): the server
  then gates that page to the login page at the same URL, and a successful
  login returns the user to the page they were on. A short guard suppresses a
  second bounce within a few seconds (falling back to the root) so a bad
  credential cannot spin a reload loop. Note that error responses (4xx/5xx,
  including the container's HTML error page) are surfaced to the page and are
  *not* treated as an unauthenticated condition.

  This matters when the session is lost while the user is mid-application
  (e.g. the webapp is redeployed, which destroys all in-memory sessions): the
  user is re-prompted for credentials at the page they were on rather than
  being dropped at the root.

### 7.3 CSRF protection

Synchronizer-token pattern, implemented in a small `CsrfFilter`
(`org.apache.tomcat.manager2.CsrfFilter`) mapped to `/api/*`:

1. On first API access (or after login) the filter generates a 128-bit
   `SecureRandom` token (32-char hex) and stores it in the `HttpSession`.
2. The token is delivered to the SPA in the **`X-CSRF-Token` response
   header of every API response** (so it is always fresh after a session
   change) and also via `GET /api/csrf`. It is never placed in URLs or
   query strings.
3. Every non-safe request (`POST`, `PUT`, `DELETE`, `PATCH`) must carry the
   token in an `X-CSRF-Token` request header. The SPA's `api.js` wrapper
   reads the header from the last response and attaches it automatically;
   missing or mismatched token → `403` with `error: "CSRF"` and an audit
   log entry.
4. Defence in depth: all mutation endpoints require
   `Content-Type: application/json` (except the multipart upload) and the
   webapp sets no `Access-Control-Allow-Origin` headers, so a cross-origin
   page cannot make a simple cross-site request succeed — the browser
   preflight is rejected. The token covers the cases browsers do not
   (e.g. same-site subdomain hosting, non-browser clients that should be
   rejected).
5. The filter additionally requires an authenticated session for unsafe
   methods (redundant with the security constraints, but it gives a clean
   403/401 split and a single audit point).

This is functionally equivalent to the `CsrfPreventionFilter` the legacy
webapp already applies to `/html/*`, but adapted to a header-based JSON
client instead of a hidden form field.

### 7.4 Security headers

`HttpHeaderSecurityFilter` mapped to `/*`, with the new default
`hstsEnabled=true` (overridable, like the legacy app but safer by
default) plus an explicit CSP:

```
Content-Security-Policy: default-src 'self'; script-src 'self';
  style-src 'self'; img-src 'self' data:; connect-src 'self';
  frame-ancestors 'none'; base-uri 'self'; form-action 'self'
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
```

No inline scripts/styles exist, so no `'unsafe-inline'` is needed.
Note: `style-src 'self'` also makes the browser **drop style attributes
set through `setAttribute('style', ...)`** (they count as inline style
and are blocked, with a console violation) — while the CSS object model
(`el.style.cssText = ...`) is unaffected. The `el()` helper therefore
applies a `style` attribute through `node.style.cssText`; any new code
must keep doing so or the style is silently ignored.

### 7.5 Request-safety invariants (inherited from the legacy servlets)

- WAR upload: 50 MiB `multipart-config` cap, `.war` suffix validation,
  submitted file name reduced to its base name, destination confined to
  the host's `appBase` via canonical-path containment check, update path
  writes `*.war.tmp` first to avoid auto-deploy races, per-context
  `tryAddServiced` guard against concurrent deployments.
- Server-side deploy: WAR/config paths are confined to `appBase` /
  `conf` via the same containment checks.
- Context name validation (leading `/`, no `..`, version syntax) as in
  `ManagerServlet.validateContextName`.
- The servlet refuses to undeploy/reload the context it runs in; the host
  it runs in cannot be stopped or removed. manager2 must therefore be
  deployed in its own context (default: `/manager2`) — documented.
- All output is JSON produced by the API layer; there is no HTML
  rendering of user/server data, so no escaping surface. The thread-dump
  and VM-info endpoints return plain text in a JSON string, rendered in a
  read-only `<pre>` (never `innerHTML`-ed from data).

### 7.6 Audit logging

Every mutation is logged at INFO by the API layer, including: timestamp,
authenticated principal, remote address, action, target (host/context),
parameters that are not secrets, and outcome (ok/error code). This is
strictly more auditable than the legacy `debug`-level logging and is the
single place to hook external audit sinks later.

### 7.7 Known limitation

Tomcat does not change the session id at successful FORM login by default,
so session-fixation protection relies on the login happening in a fresh
anonymous session. The howto will recommend serving manager2 over TLS with
`SameSite=Lax` session cookies; a session-id-change valve is listed as a
possible follow-up rather than a requirement.

## 8. Packaging and build

**Design decision (implemented):** everything lives in a standalone module
at `modules/manager2` with its own Ant build and packaging, instead of being
merged into the main tree's `catalina.jar` + `webapps/` copy. The module is
fully decoupled: it compiles against the main build's jars, produces its own
`manager2.jar` + `manager2.war`, and its `deploy` target drops the WAR into
the main build's `webapps/` directory.

Module layout:

```
modules/manager2/
  build.xml                 standalone Ant build (jar, war, deploy, test)
  build.properties.default  version + main-build location
  resources/MANIFEST.MF     jar manifest
  src/main/java/org/apache/tomcat/manager2/
    AppsApiServlet.java       extends HTMLManagerServlet
    HostsApiServlet.java      extends HostManagerServlet
    StatusApiServlet.java     status endpoints
    StatusSnapshot.java       MBean collection → JSON model
    LogsApiServlet.java       /api/logs + /api/access-log (list, tail, filters, raw download)
    LogParser.java            JULI text/JSON log lines, access log pattern→regex
    AccessLogSupport.java     access log field names, normalization
    UsersApiServlet.java      /api/users + /api/groups + /api/roles
                               (UserDatabase JNDI discovery,
                               user/group/role management, save)
    CsrfFilter.java           CSRF token issue/verify
    HeadersFilter.java        CSP / Referrer-Policy
    Strings.java              StringManager creation that finds the web app's
                              LocalStrings bundle regardless of the initializing
                              thread's context class loader
    Api.java, Json.java, Constants.java
    LocalStrings.properties
   src/test/java/org/apache/tomcat/manager2/
     TestManager2Webapp.java
     TestManager2Config.java
  webapp/                   SPA shell, login, css, js, WEB-INF/web.xml,
                            META-INF/context.xml (privileged context)
```

Notes:

1. The package is `org.apache.tomcat.manager2`, not
   `org.apache.catalina.manager2`: `DefaultInstanceManager` always loads
   `org.apache.catalina*` classes with the container class loader, which
   would make the web app's own jar unreachable.
2. The WAR is self-contained (`WEB-INF/lib/manager2.jar`), following the
   convention of the other `modules/` web apps.
3. No main-tree build changes at all; no new Ant dependencies, no new jars,
   no license/NOTICE changes (zero third-party code).

`conf/` changes: none required to *use* the webapp; existing role names
(`manager-gui`, `manager-status`) are reused. To make the Users page
*persist* changes, the `UserDatabase` resource must be writable: the file
based database defaults to read-only, so `readonly="false"` has to be added
to its `<Resource>` definition in `server.xml` (and the server restarted).
Until then the page still shows the current users, groups and roles, the
mutation controls are disabled, and a banner explains what to configure.

## 9. Testing

New `modules/manager2/src/test/java/org/apache/tomcat/manager2/
TestManager2Webapp.java` (`TomcatBaseTest`, run by the module's `ant test`),
modelled on the existing `TestManagerWebapp` but exercising the new flows:

1. **FORM auth flow** with `SimpleHttpClient`: unauthenticated `GET
   /manager2/api/apps` → redirect to `login.html`; `POST
   j_security_check` with the session cookie → 302 back; authenticated
   call → 200. The context root without a trailing slash (`/manager2`)
   is a 302 redirect to `/manager2/` (see §7.2).
2. **CSRF flow**: `POST /api/hosts` (or any mutation) without token → 403
   `CSRF`; with the `X-CSRF-Token` header → 200. Re-login invalidates the
   old token.
3. **Authorization**: user with only `manager-status` can `GET
   /api/status` but gets 403 on `/api/apps` and all mutations;
   unassigned user gets 401/redirect on everything.
4. **Functional parity** (same test WARs as `TestManagerWebapp`): list,
   deploy (server-side + upload), start/stop/reload/undeploy, session
   list/sort/detail/invalidate/attribute-removal, expire idle, global JNDI
   resources (asserts the classic manager's human readable status header is
   stripped from the `name:class` lines).
5. **Hosts**: add (all parameters), list (asserts the running default host
   is reported `started: true`, not matched on the raw state name), stop,
   start, remove, persist.
6. **Status**: `GET /api/status`, `/api/status/workers`,
   `/api/status/apps/{path}` — assert the JSON contract (key presence,
   types, connector names) rather than exact values; counters
   monotonically increase across two polls.
 7. **Headers**: `X-Frame-Options: DENY`, CSP, `X-CSRF-Token` presence on
    responses, `Cache-Control: no-store` on API responses.
 8. **Users**: with no `UserDatabase` JNDI resource configured, `GET
    /api/users` and mutations return 404 `USER_DATABASE_MISSING`. With a
    file based `MemoryUserDatabase` registered on the global naming context
    (via `Tomcat.enableNaming()` + `ContextResource`): list (users, groups,
     roles, `readonly`/`writable` flags), create user with roles, change
     password, replace roles, create group, set members, replace group
     roles, create role with description, remove role (detaching it from
     the users that hold it), remove group/user — each verified against the
     persisted XML file; duplicate names → 409, unknown members → 400
     `UNKNOWN_GROUP_MEMBER`, unknown user/group/role → 404, self removal →
     400 `SELF_REMOVAL`, self role removal (a role held by the signed-in
     account) → 400 `SELF_ROLE_REMOVAL`; a read-only database
     (`readonly` attribute not set) rejects all mutations with 400
     `USER_DATABASE_READONLY`; `manager-status` gets 403 on the users API.
 9. **Logs**: list (JULI + access log files, detected format), tail with
    level / method / status / user / session / free-text filters (text and
    JSON formats, pattern driven access log fields), the raw download
    (`/api/logs/download`, `/api/access-log/download`) returns the full
    unfiltered file and rejects path traversal, and the returned
    records are asserted to be the most recent matching lines ordered from
    most recent to least recent. `manager-status` gets 403.
10. **Deploy wizard (browser E2E)**: the modal opens with only the
    *Upload* pane visible; *From server* shows only its pane; switching
    back to *Upload* shows the complete upload form again (regression for
    the "truncated form" report, which had two causes: the server pane
    lacked an initial `display:none`, and the CSP blocked `style`
    attributes set via `setAttribute` — see §7.4).
11. **Configuration** (`TestManager2Config`): tree shape (the service,
    engine and host are resolved from the returned tree by type — their
    names depend on how the test instance was created); node details
    (id/type/className/properties, the `self` flag on the self context,
    404 for unknown nodes); attribute round-trip (read, update, read
    back, restore) plus guards (non-writable attribute → 400 `READ_ONLY`,
    unknown attribute → 404 `ATTRIBUTE_NOT_FOUND`, value that does not
    convert to the attribute type → 400 `INVALID_VALUE`, the self
    context's `path` → 403 `SELF_COMPONENT` with or without a confirm);
     add + remove of all eight structural child types (service,
     connector, executor, host, alias, context, wrapper, valve) with the
     whole branch visible in the tree in between, and the guards
     (unsupported type → 400 `UNSUPPORTED_TYPE`, wrong parent → 400
     `BAD_PARENT`, duplicate service name → 409 `DUPLICATE`, invalid
     connector port → 400 `INVALID_VALUE`, executor
     `minSpareThreads > maxThreads` → 400 `INVALID_VALUE`, last service →
     400 `LAST_SERVICE`, self service/host/context → 403
     `SELF_COMPONENT`, basic valve → 400 `BASIC_COMPONENT`); add +
     remove of a `listener` on a `Lifecycle` parent (visible in the tree,
     node detail resolves, non-listener or unknown class → 400
     `INVALID_CLASS`, alias parent → 400 `BAD_PARENT`); `store/preview`
     returns the XML without writing
    anything; `store` writes `conf/server.xml` containing the live state
    (including a just-added alias) and keeps a timestamped backup.
     Unauthenticated tree request → the login page; `manager-status` →
     403; a mutation without a CSRF token → 403. The root context (empty
     path) is addressed by a bare `+` segment and displayed as `/`: its
     node detail and its child components (a wrapper added through the API)
     both resolve. TLS on a dedicated service (so it never is the connector
     of this webapp): guards (wrong parent → 400 `BAD_PARENT`, no
     certificate → 400 `INVALID_VALUE`, unloadable keystore → 400
     `ADD_FAILED` + rollback, unknown certificate type → 400
     `INVALID_NAME`, certificate outside an SSL host configuration →
     400 `BAD_PARENT`); add an SSL host configuration with its
     certificate (the connector serves real TLS, verified with a TLS
     handshake, and reports `sslEnabled`), node details of the host
     configuration and the certificate (including the `protocols`
     property, compared order-independently), attribute update
     (`protocols`) and read-only protection (`type`, `hostName`),
     adding and removing a second certificate, the
     `SSL_LAST_CERTIFICATE` guard, the TLS branch in the stored
     `server.xml` (including the keystore path), the duplicate host
     name guard (409 `DUPLICATE`), and removal (the connector serves
     plain HTTP again, verified with a plain request). Realms: the
     engine realm is shown and an inherited realm is not; node detail
     (modeler properties, `acceptsSubRealm` false for a plain realm);
     add a realm to a host (unknown class → 400 `INVALID_CLASS`, a
     second one → 409 `DUPLICATE`), attribute update (`allRolesMode`,
     invalid value → 400 `SET_FAILED`), a `LockOutRealm` on a context
     with sub realms (`acceptsSubRealm` true, adding two, both shown,
     node detail of a sub realm, the combined realm and its sub realms
      in the stored `server.xml`), removing a sub realm and the
      combined realm, the `LAST_REALM` guard on the engine realm, and
      removing the host realm (falling back to the engine realm).
      Context sub components on a dedicated context: the manager (with
      its session id generator), resources, loader and cookie processor
      are shown; node detail (the manager and resources through their
      modeler descriptor, the loader, cookie processor and session id
      generator through the explicit attribute list); attribute updates
      (`maxActive`, `allowLinking`, `delegate`, `sameSiteCookies`,
      `sessionIdLength`, invalid value → 400 `SET_FAILED`); replace via
      add (a fresh instance with the defaults is in place, the
      manager/loader keep their `STARTED` state, the session id
      generator is re-created), wrong class → 400 `INVALID_CLASS`,
      wrong parent → 400 `BAD_PARENT`, replacing the self context's
      manager or loader → 403 `SELF_COMPONENT`, replacing the
      resources of a running context → 400 `CONTEXT_RUNNING` and on a
      stopped context → 200 (started again afterwards), and the
       `REQUIRED_COMPONENT` guard on removal of all five types.
       JNDI naming resources (with naming enabled via `Tomcat.enableNaming()`):
       the server's global `namingResources` node (`global: true`) and a
       context's (`global: false`); add a global `UserDatabase` with a
       first-party factory (the closed factory options are shown as `param`
       properties with their values, a not-set option is listed with a null
        value) and verify it is bound in the live global naming context (and
        loaded its users); add an `environment`, `ejb`, `localEjb` and
        `serviceRef` (all shown in the tree); the generic string parameters
        of the `environment`, `ejb` and `serviceRef` entries (the
        `ResourceBase` property map) are shown as `param` properties and can
        be added, edited and removed (cleared); the guards (duplicate JNDI name
       → 409 `DUPLICATE`, missing `jndiType` → 400 `MISSING_FIELD`,
       `resourceLink` at the server level → 400 `BAD_PARENT`, unloadable
       factory → 400 `INVALID_CLASS`, removing the node → 400
       `REQUIRED_COMPONENT`); a parameter update re-binds the entry (the new
       value is reported and the live lookup still resolves); renaming a JNDI
       name requires a confirm and re-binds it (the old name is unbound, the
       new name is bound); a free-form parameter is added and then cleared
       (empty value removes it); the global entries round-trip to
       `<GlobalNamingResources>` in the store preview (including the
       `<ServiceRef>`); and a context-level `resourceLink` (shown with its
       `global`) and a `resource` whose type dispatches to the default
       data-source factory (bound in the context's live environment, its
       factory options editable as parameters, the pool size update re-binds
       it, duplicate → 409, removal unbinds it).

    The browser E2E for this page (login, tree, property edit, add/remove
    with confirm, save preview, no console errors) is driven over CDP and
    is not part of `ant test`.

Test-environment notes (discovered while implementing):

- The programmatic test instance has no global `conf/web.xml` and
  `setAddDefaultWebXmlToWebapp(false)` is set, so the test webapps must
  declare their own `default`/`jsp` servlets and welcome files in
  `web.xml`; `StandardContext.stop()` resets all wrappers and only
  re-creates them from `web.xml` on start, which is what makes
  stop/start/undeploy testable at all.
- The test instance's default host has no `HostConfig`, so the
  `Catalina:type=Deployer,host=...` MBean (the `Deployer` the deploy API
  uses) is absent; the test attaches one as a host lifecycle listener.
- `SimpleHttpClient` request parts must keep their CRLF terminators and the
  client must re-`connect()` before every request (`Connection: Close`).
- JNDI naming is disabled by default in the programmatic `Tomcat`, so the
  users tests call `Tomcat.enableNaming()` before start; the test then
  registers a `MemoryUserDatabase` on the server's global naming resources
  exactly like the `<Resource>` of the default `server.xml` does.
- The `StringManager` cache is JVM-wide and the first creation for a
  package wins; a class initialized by a non-request thread (e.g. the
  filter initialization during context start) would otherwise cache a
  manager that cannot see the web app's `LocalStrings` bundle. `Strings`
  creates the manager with the web app's own class loader as the context
  class loader, so the first creation always finds the bundle.
- The programmatic `Tomcat` API installs a private internal realm
  (`Tomcat$SimpleRealm`) on the engine that `storeconfig` cannot
  serialise (`StoreAppender` instantiates the realm class via its public
  no-arg constructor), and names the default service *and* engine
  `Tomcat` rather than `Catalina`. `TestManager2Config` therefore swaps
  in a `MemoryRealm` — the same type the production `server.xml` uses —
  loaded from a minimal `conf/tomcat-users.xml` written into the test's
  temporary `catalina.base` (a bare `MemoryRealm`, such as the one
  `addService` creates for new services, requires that file to exist to
  start), and resolves every node path from the returned tree instead of
  hard-coding service/engine names.
- `SimpleHttpClient` cannot read chunked responses: a response larger
  than the connector's 8 KB buffer that has no content length is
  committed mid-write and sent with `Transfer-Encoding: chunked`, which
  the client then reads as raw framing. The manager2 API therefore
  always sets the content length on its JSON responses (the body is
  already built in memory), which the node-details test exercises
  (the context node detail is well over 8 KB).
- `org.apache.tomcat.util.json.JSONParser` (the shared request-body
  parser) recognised the JSON escape sequences of a string token but
  never resolved them, so any parsed string kept its backslashes
  (`"a\"b"` parsed as `a\"b`). This was fixed in the parser (both the
  generated class and the `.jjt` grammar): the string body is now
   unescaped (`\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`). The
   configuration store-preview test is the first assertion that parses
   a response whose value contains quotes (the stored `server.xml`),
   and it failed until the fix.
 - `storeconfig` parsed a `<ServiceRef>` (a JNDI `serviceRef` entry) but
   could not store it back: `NamingResourcesSF` did not write the services
   and the registry had no `<ServiceRef>` description. A two-line core
   change (a `ServiceRef` registry description for `ContextService`, and
   storing `findServices()` in `NamingResourcesSF.storeChildren`) closes
   the round-trip, so a `serviceRef` added through the Configuration page
   is written to `server.xml` like the other JNDI entries. The other six
   entry types already round-tripped.

Frontend: a manual smoke-test checklist in the docs (login, each page,
deploy upload, live chart behaviour, mobile widths); the JS is small
enough that a lint pass (`--check` via a CI node step, optional) plus the
integration tests above gives adequate coverage without a JS test
harness.

Mobile checklist (portrait 360/390/414/820 px and landscape 667/812/932 px,
both themes, plus mid-width windows at 901/1024 px): no page-level
horizontal overflow; below 900 px management tables render
as labelled stacked cards and row actions collapse into the kebab menu
(kebab opens, closes on outside tap and `Esc`, disabled actions stay
disabled); high-volume tables (logs, access log, workers) scroll
horizontally with the first column pinned (log pages: never pinned, the
whole table scrolls) and single-line rows below 1150 px, not squeezed; tabs scroll when crowded; page-head, log and diagnostics
controls go full width; modals show stacked full-width buttons; toasts
appear above the bottom nav; the bottom nav keeps all nine items as icons
only (labels as `aria-label`, active item in its per-tab hue); inputs are
16 px at ≤480 px (no iOS
focus zoom); the Configuration detail scrolls into view after a tree
selection; a multi-segment deep link (e.g. an application detail) reloads
to the correct page (the base-element fix above).
