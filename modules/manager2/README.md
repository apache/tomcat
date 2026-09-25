# Tomcat Manager2

`manager2` is an experimental, self-contained replacement for the classic
`/manager` and `/host-manager` web applications, extended with live runtime
monitoring. It is a single web application deployed at `/manager2` that
combines:

- **Web application management** — list, deploy (server-side path or upload),
  start, stop, reload, undeploy, and session management (list, detail,
  attributes, invalidate).
- **Virtual host management** — list, add, start, stop, remove and persist
  hosts.
- **Runtime monitoring** — JVM, memory and thread-pool gauges, per-connector
  worker statistics, per-application detail, plus diagnostics (memory leaks,
  global resources, VM info, thread dump, SSL ciphers/certificates).

The interface is a dependency-free JavaScript SPA (no JSPs, no build step)
backed by a small JSON API. This module is experimental: it is not included
in the default distribution and its API is not yet stable.

## Layout

```
modules/manager2/
  build.xml                 Standalone Ant build (jar, war, deploy, test)
  build.properties.default  Version + main-build location properties
  resources/MANIFEST.MF     Jar manifest (bundle metadata)
  src/main/java/org/apache/tomcat/manager2/
    AppsApiServlet.java     /api/apps/*, /api/ssl/*, /api/leaks,
                            /api/resources, /api/diagnostics/*
                            (extends HTMLManagerServlet)
    HostsApiServlet.java    /api/hosts/* (extends HostManagerServlet)
    StatusApiServlet.java   /api/info, /api/csrf, /api/status, /api/status/*
    StatusHistory.java      Rolling history of status samples, collected in
                            the background and served by /api/status/history
    StatusSnapshot.java     MBean collection for the status endpoints
    CsrfFilter.java         Per-session CSRF token (X-CSRF-Token header)
    HeadersFilter.java      Content-Security-Policy / Referrer-Policy
    HomeServlet.java        / + SPA deep links: login gate / SPA shell
    LoginServlet.java       /login: renders the login page, keeps the
                            post-login redirect at the app root
    LogoutServlet.java      /logout: invalidates the session
    ErrorServlet.java       /error: renders the 403/404 pages
    Html.java               Template rendering (base element injection)
    Api.java, Json.java, Constants.java, LocalStrings.properties
  src/test/java/org/apache/tomcat/manager2/
    TestManager2Webapp.java Integration tests (TomcatBaseTest)
  webapp/
    index.html              SPA shell
    login.html              Login page template (served by LoginServlet)
    error-403.html          Error page templates (served by ErrorServlet)
    error-404.html
    css/manager2.css        Design system (light/dark, responsive)
    js/*.js                 SPA (vanilla ES modules, no dependencies)
    WEB-INF/web.xml         Servlets, filters, constraints, login-config
    META-INF/context.xml    Privileged context + hardened cookie processor
```

The servlet package is `org.apache.tomcat.manager2` rather than
`org.apache.catalina.manager2`: classes whose names start with
`org.apache.catalina` are always loaded by the container class loader
(`DefaultInstanceManager`), which would make the web application's own jar
unreachable for them.

## Requirements

- A main Tomcat build with `${tomcat.home}/output/build/lib` populated
  (run `ant` in the main tree first).
- Ant, and a JDK matching the main build.
- For `ant test`: the main tree's `output/testclasses` (from `ant test` or a
  full build) and the JUnit/HAMCREST jars in `${user.home}/tomcat-build-libs`.

## Building

```sh
cd modules/manager2
ant            # produces output/manager2.jar and output/manager2.war
ant deploy     # additionally copies manager2.war into output/build/webapps
ant test       # builds, deploys and runs the integration tests
```

`build.properties` (local, not committed) can override the properties from
`build.properties.default`, in particular `tomcat.home`/`tomcat.build` if the
main build lives elsewhere.

The WAR is self-contained: the servlets ship in `WEB-INF/lib/manager2.jar`.
The context is configured via `META-INF/context.xml` to run privileged
(required by the management servlets) with a hardened `Rfc6265CookieProcessor`
(`SameSite=Strict`) and, for convenience in development, a
`RemoteCIDRValve` allowing loopback only.

## Using

Deploy `manager2.war` (or the unpacked directory) and create users with the
usual `manager-gui` role in `conf/tomcat-users.xml`; `manager-status` grants
read-only access to the status endpoints. Log in at `/manager2/` (HTTP FORM
authentication). State-changing API calls require the per-session
`X-CSRF-Token` header, which is returned in the `X-CSRF-Token` response
header of every API response.

The Dashboard charts are driven by `GET /api/status/history`. The web
application collects a sample in the background (on the server utility
executor, from deployment time because the `StatusApi` servlet is
load-on-startup) and keeps the samples of the configured window, so the
charts always show the last window of server activity regardless of when
the page was opened. The collection period and the window are configured
with the `tickMs` and `windowMs` init parameters of the `StatusApi` servlet
(defaults: `2000` ms and `600000` ms, i.e. 300 samples; `tickMs` must be at
least `500` and `windowMs` at least one tick).

## Testing

`ant test` runs `TestManager2Webapp`, a `TomcatBaseTest` that deploys the
built WAR into a throw-away Tomcat instance and drives it over HTTP:

- FORM login flow (including a bad-password case)
- CSRF token issuance and rejection of mutations without a token
- Role separation (`manager-status` is read-only)
- Application list, stop/start/undeploy lifecycle and a deploy from a
  server-side WAR
- Session list/detail/invalidate against a JSP-created session
- Status, status history, workers and per-application detail endpoints
- Hosts endpoint
