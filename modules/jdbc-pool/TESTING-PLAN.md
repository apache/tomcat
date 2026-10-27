# jdbc-pool baseline test suite plan (v2, defect-driven)

Goal: a full baseline test suite for `org.apache.tomcat.jdbc.pool`
run against a **real database**: a green behavioural spec of the
pool's *expected functionality* (getting and reusing connections,
executing statements, transactions, sizing/eviction, lifecycle)
that later defect fixes must not break.

Defect-specific reproducer tests are deliberately **not** part of
the baseline: they will be written while processing each defect of
`defects-pool/` (13 High / 72 Medium / ~60 Low) later, using the
fixtures and oracles this baseline establishes. Section 4 keeps the
High-defect map as the backlog for that later phase.

v1 was an incremental rewrite plan; v2 replaces it: the old suite is
largely dropped, and the defect catalog drives the test catalog.

## 1. Verdict on the existing tests: drop, but harvest first

The old suite (~6 min, 37 classes) has three structural problems:
most tests run against a hand-written stub JDBC driver
(`test/.../driver/`), the runtime is dominated by DBCP-comparison
benchmarks (`FairnessTest`, `ConnectCountTest`, `CheckOutThreadTest`,
`StarvationTest`, ...), and timing tests are sleep-driven and
flaky (the `PoolCleanerTest` static-leak failures reproduce with or
without JaCoCo). Against the defect list, they demonstrate almost
nothing: none of the 10 live High defects is observable through a
stub driver that can't fail mid-transaction, drop sockets, or hold
server-side state.

**Recommendation: delete the suite from the build — after a one-time
harvest.** The old tests are the only encoded memory of historical
regressions; scenario, not code, must survive:

| Disposition | Classes |
|---|---|
| **Keep** (port to JUnit 5 as-is; fast, pure-unit, no fake DB) | `JdbcInterceptorConfigParsingTest`, `SlowQueryComparatorTest`, `PoolPropertiesTest`, `ShouldForceReconnectTest` (`TestInterceptorShortName` moved to the harvest row below: it needs a live pool, and the real `SlowQueryReport` can play its role) |
| **Harvest scenario → rewrite against real DB** | `Bug50571`, `Bug50805`, `Async0IdleTestBug50477` (regression memory), `TestConnectionState` (→ defect #3 repro), `TestGCClose` (GC-close semantics), `MultipleCloseTest`, `EqualsHashCodeTest`, `AlternateUsernameTest` (H2 supports multiple DB users → now a *real* test), `PoolPurgeTest`, `TestValidation`, `TestValidationQueryTimeout`, `TestTimeout`, `TestSuspectTimeout`, `TestGetConnection`, `TwoDataSources`, `JmxPasswordTest` (→ credential-exposure defect), `TestQueryTimeoutInterceptor`, `TestStatementCache`, `TestSlowQueryReport`, `StatementFinalizerTest` |
| **Delete** (benchmarks, sleep-drift, DBCP/c3p0 comparisons, stub plumbing, examples) | `FairnessTest`, `CheckOutThreadTest`, `ConnectCountTest`, `StarvationTest`, `AbandonPercentageTest`, `TestConcurrency`, `TestSizePreservation`, `BorrowWaitTest`, `TestAsyncQueue`, `TestException`, `ProxiedResultSetTest`, `ProxiedStatementTest`, `PoolCleanerTest` (flaky), `CreateTestTable`, `DefaultTestCase`/`DefaultProperties` (DBCP plumbing), `SimplePOJO*Example`, `driver/*` |

Benchmarks, if wanted later, go to a JMH profile; they never gate CI.
The old suite is preserved in git history only.

### Harvest result (done)

Scenario → new class, as harvested. Where the legacy test asked a fake
driver what it remembered, the new test asks the database.

| Legacy scenario | New home |
|---|---|
| `Async0IdleTestBug50477`, `Bug50805`, `TestAsyncQueue` (async borrow must not strand a waiter, accounting stays honest) | `pool/TestAsyncBorrow` (4) |
| `Bug53367`, `FairnessTest`/`StarvationTest` *interrupt* case (an interrupted waiter must not eat a permit) | `pool/TestInterruptedWaiter` (2) |
| `TestValidation`, `TestValidationQueryTimeout`, `TestTimeout` (validation query, timeout, `testOnBorrow`/`Return`/`WhileIdle`, `isValid()`, custom `Validator`) | `pool/TestValidation` (9) |
| `TestGetConnection`, `TwoDataSources`, `Bug50571`, `Bug54225`, `Bug54978` (connect-time setup: `initSQL`, blank `initSQL`, `initSQL` as connect validation, unusable configured isolation, `dbProperties`, side-door close detected) | `pool/TestConnectionSetup` (6) |
| `PoolPurgeTest` | `pool/TestPurge` (2) — re-encoded to the real generational semantics: `purge()`/`purgeOnReturn()` both start a new pool generation, a pre-generation session is closed on return, `purge()` also closes the ones idle right now |
| `MultipleCloseTest`, `EqualsHashCodeTest` (facade identity path) | `pool/TestProxyContract` (4) |
| `StatementFinalizerTest`, `TestStatementCache`, `ProxiedResultSetTest`/`ProxiedStatementTest` back-references | `pool/TestStatementLifecycle` (7) |
| `TestConnectionState` (hygiene half; the leak half stays defect #3 backlog) | `pool/TestSessionState` (3) |
| `AlternateUsernameTest` (now a real test: H2 users, identity read back with `CURRENT_USER`) | `pool/TestAlternateCredentials` (6) |
| `TestInterceptorShortName`, `TestSlowQueryReport` lifecycle/statistics half | `pool/TestInterceptorLifecycle` (3) |
| `JmxPasswordTest` (documented `getPassword()` redaction only) | `pool/TestJmxMonitor` (+1, 5 total) |
| `PoolPropertiesTest` (pure unit: `toString` bracket balance **plus** password masking) | `pool/TestPoolProperties` (2) |
| `TestJdbcInterceptorConfigParsing` | `pool/TestJdbcInterceptorConfigParsing` (4) |
| `TestSlowQueryComparator` | `pool/TestSlowQueryComparator` (5) |
| `ShouldForceReconnectTest` (truth table, expanded to a parameterised case per credential combination) | `pool/TestShouldForceReconnect` (17) |

Deliberately dropped, with reasons:

* `TestGCClose` — a finalizer-on-GC scenario cannot be gated (no
  deterministic trigger). The hazard it gestured at (a stale proxy
  driving somebody else's session) is a High catalog finding and is
  pinned at the facade level by `TestProxyContract`.
* `TestQueryTimeoutInterceptor`, `TestValidation`'s "how many times did
  the driver get asked" cases — the first is already covered by
  `TestInterceptorSemantics`; the second counted stub-driver calls and
  is superseded by session-count and outcome oracles.
* `TestSlowQueryReport`'s JMX-notification counting and `bugs/Bug51582`
  (`SlowQueryReportJmx`) — High defect #7, §4 backlog.
* `bugs/Bug54227` (a session older than `maxAge` must be replaced) was
  already covered before the harvest, by
  `TestPoolEviction#maxAgeRetiresAnOldConnectionBetweenBorrowers`.
* StatementCache hygiene cases H2 cannot observe: `maxFieldSize`
  (always reports 0), `getFetchDirection` (always `FETCH_FORWARD`),
  `isPoolable()` (always false — the interceptor tracks it itself) and
  driver-generated warnings (H2 raises none on `executeUpdate`). The
  observable half — batch, `closeOnCompletion`, `queryTimeout`,
  `maxRows`, `fetchSize` — is in `TestStatementLifecycle`.
* Everything else per the disposition table above (benchmarks, DBCP
  comparisons, sleep-driven timing, stub plumbing, examples).

Two observations made *while* harvesting that are consciously **not**
pinned (they look unintended; see §6): a failed alternate-credential
borrow discards a healthy idle session (the pool shrinks to 0 sessions
instead of keeping the one it had). (The third observation — that
`ProxyConnection`'s own `equals`/`hashCode` below the disposable facade
threw — has since been fixed; the facadeless configuration that reached
it has since been deprecated away — the facade is now always used, and
the handle contract for such configurations is pinned by
`pool/TestFacadeAlwaysUsed`.)

## 2. Database choice (unchanged conclusion)

Two tiers, H2 first:

* **Default: H2 in TCP server mode** (`org.h2.tools.Server`
  started in-process, `jdbc:h2:tcp://...`). Real sockets, real
  server that tests can stop / SIGSTOP / restart to inject failures;
  XA support (`org.h2.jdbcx.JdbcDataSource`, which implements
  `javax.sql.XADataSource`) for the never-tested `XADataSource`;
  multiple DB users for the alternate-credentials tests. No Docker,
  milliseconds startup. **Not** `jdbc:h2:mem:` — in-process
  connections hide exactly the failure modes under test.
* **Docker tier: PostgreSQL via Testcontainers**, `@Tag("docker")`,
  auto-skipped without Docker. Needed where H2 cannot go:
  `pg_terminate_backend()` kills *one* pooled connection while the
  DB lives, `idle_session_timeout`, `max_connections` exhaustion,
  realistic latency.

Baseline principle across both tiers: **assert server-side truth,
not pool-reported stats.** H2's `INFORMATION_SCHEMA.SESSIONS` and
Postgres's `pg_stat_activity` give the number of *physically open*
connections. Nearly every leak/exhaustion defect in the catalog
(ghost waiters, leaked cleaners, aborted acquire, double-use races)
shows up as pool-vs-server session-count divergence, and it is the
only honest oracle for the hygiene fixture ("after teardown, server
shows 0 sessions for our database").

## 3. Suite structure

JUnit 5, three groups:

1. **Spec tests (green gate).** Behaviour of each component against
   the real DB on documented configurations — borrow/return,
   eviction (`minEvictableIdleTimeMillis`, `maxAge` verified via
   server session/PID identity), validation (query vs `isValid()` vs
   custom `Validator`), exhaustion → `PoolExhaustedException`,
   fairness queues, interceptor chain semantics, JMX attributes with
   live traffic, XA commit/rollback, JNDI factories, proxy contract
   (double close, GC close, `equals`/`hashCode`, autoCommit &
   rollback-on-return verified by observing committed data).
2. **Defect reproducers — written later, not in the baseline.**
   While each defect is processed, a reproducer goes into the suite
   written *to the correct behaviour* stated in the report's "Fix"
   line, referencing `defects-pool/defects-<FQCN>.md` and the
   finding ID (F3, F8, …). No `known-defect` tags are needed now
   because the baseline contains no defect tests; once defect
   processing starts, reproducers land alongside (or slightly
   before) their fix, so the suite stays green throughout.
3. **Dead-code coverage.** `MultiLockFairBlockingQueue` was removed
   during defect processing (it was unreachable through the pool and
   its design was unfinished), so it needs no direct unit tests.

Layout: each test class is `Test*.java` and sits in the package of
the code it tests - `org.apache.tomcat.jdbc.pool` (pool behaviour and
the pooled-statement surface, plus the pure-unit configuration cases
ported from the old suite), `pool.interceptor`, `pool.jmx`, `naming`.
The shared fixture is `org.apache.tomcat.jdbc.JdbcPoolBaseTest`, the
one class all behavioural tests extend.

Infrastructure: H2-TCP fixture (per-class server + fresh DB name);
state-hygiene extension (every `DataSource` closed, pool statics and
server session count back to baseline after each test — this kills
the `PoolCleanerTest` flake class permanently); Awaitility instead
of sleeps; `-Pdocker` profile for the Postgres tier.

Fixture details worth knowing before writing a test against it
(`org.apache.tomcat.jdbc.JdbcPoolBaseTest`):

* The database is bootstrapped by an admin connection with
  `SET DB_CLOSE_DELAY -1`. Without it an in-memory database is
  destroyed when the pool closes its last physical session — eviction,
  `purge()` and an alternate-credential reconnect all do that — and the
  next connection silently gets an *empty* database, which can make an
  assertion about committed data pass against a database that was never
  the one the test wrote to. Putting it in the URL is not an option:
  every client re-applies URL settings and a non-admin user then fails
  login with "Admin rights are required for this operation".
* Oracles are `poolSessions()` (the database's own
  `INFORMATION_SCHEMA.SESSIONS` count minus the admin session),
  `setSessionMarker`/`sessionMarker` (an H2 session variable, to prove
  *which* physical session a borrower got) and `adminRowCount`
  (committed data, read over a separate admin connection).
* Fault injection and white-box reach go through the real objects:
  `((javax.sql.PooledConnection) pooled).getConnection()` for the
  physical session, `interceptorOf(pooled, type)` to find an interceptor
  in the chain of a borrowed connection (its invocation handler is the
  head of that chain), and `driverStatement(proxy)` — `unwrap` — for the
  driver's own object behind a pooled statement, which is the honest
  oracle for "this was not prepared on the session twice".

## 4. High-defect map (backlog for the later defect-processing phase — not implemented now)

| Defect (defects-summary.md #) | Reproducer design |
|---|---|
| 1. `DataSourceProxy.close()` vs `createPool()` race | N threads racing first `getConnection()` against `close()`; oracle: server session count == 0 and cleaner registry empty after close |
| 2. `SlowQueryReport.removeOldest()` IOOBE | `maxQueries`=small, burst of distinct SQLs across threads; assert no IOOBE reaches the app and map ≤ capacity |
| 3. `ConnectionState` cross-borrower isolation/readOnly leak (and `rollbackOnReturn` F8) | borrower A sets READ_COMMITTED/readOnly inside a tx and closes; borrower B reads effective isolation/readOnly on the *real DB*; plus uncommitted-work-visible-after-close check |
| 4. `DisposableConnectionFacade` stale-proxy double use | two threads: one holds proxy across `close()`, other re-borrows; assert stale proxy fails, never drives the other's connection |
| 5. `AbstractQueryReport` CCE on failing non-execute methods | failing `setFetchSize` with `ReportState` enabled; assert original `SQLException` survives |
| 6. `StatementFinalizer` unbounded list + silent skip | loop statements through one borrow asserting list size; leak a GC'd open statement and assert the next borrower sees no server-side cursor (H2 `INFORMATION_SCHEMA.SESSIONS`/prepared counts; kill tier on PG) |
| 7. `SlowQueryReportJmx notifyPool=false` drops notifications | 2 concurrent borrowers slow-querying, count JMX notifications == expected |
| 8. `XADataSource` marker silently non-XA | `<Resource type=javax.sql.XADataSource>` with plain driver → expect fail-fast `SQLException`, not silent non-XA; real 2-phase commit/rollback with H2's XA resource |
| 9. `GenericNamingResourcesFactory` silent null-set | JNDI-configured pool with unsupported setter type; assert property unchanged and warning logged |
| 10. `MultiLockFairBlockingQueue` ×3 | resolved by removing the class (dead, unfinished design) — no tests needed |

Medium themes (interceptor `disconnected()` leak chain, async ghost
waiters, `getDbProperties()` password exposure, MBean
re-registration, `maxAge` long→int overflow, interceptor ordering
`QueryTimeoutInterceptor` after `StatementCache`, …) each get their
own reproducer in phase 3; the per-class reports' finding IDs are
the working list.

## 5. Phasing

1. **Harness**: JUnit 5 (5.11.x, Java 8 compatible) + JUnit
   vintage engine for the old suite + H2-TCP fixture + hygiene
   helpers + Await-style polling helper. Old suite's flaky
   `PoolCleanerTest` excluded (already red in full-suite runs;
   superseded by the baseline). Suite-time target: new tests < 60 s.
   **Done**: the fixture as
   `org.apache.tomcat.jdbc.JdbcPoolBaseTest`.
2. **Spec tests** (green gate) for core pool + proxies +
   interceptors, against H2-TCP: acquire/reuse, statement
   execution, transactions, sizing/eviction/lifecycle first; then
   validation variants, fairness queues, interceptor chain
   semantics, JMX with live traffic, XA, JNDI factories.
   **Done**: the phase 1 classes plus `TestInterceptorSemantics`
   (slow/failed query reporting, `QueryTimeoutInterceptor`
   cancellation, `StatementCache`, `StatementDecoratorInterceptor`),
   `TestJmxMonitor` (pool + per-connection MBeans with live
   traffic), `TestXaConnection` (real 2-phase commit and rollback
   against H2's XA resource), `TestNamingFactory`
   (`DataSourceFactory`, `GenericNamingResourcesFactory`) and
   `TestFairBlockingQueue` (most-recently-used hand-off, blocking
   retrieval, waiter fairness, documented-unsupported operations).
   The validation variants (`testOnBorrow`, `testOnReturn`,
   `testWhileIdle`; query vs `isValid()` vs a custom `Validator`,
   `validationQueryTimeout`) arrived with the harvest as
   `TestValidation`.
   `TestInterceptorSemantics` alone costs ~60 s: real timeout
   cancellation needs a real multi-second query (H2 has no `SLEEP()`
   and an in-process `CREATE ALIAS` is not cancel-safe), so the
   "< 60 s" budget is understood to exclude it.
   Added afterwards, because the pool's own caching is core
   functionality: `TestStatementCacheReuse` (6) pins the statement cache
   on the driver's own statement object - a closed statement is handed
   to the next borrower of the same session without a second prepare,
   each session caches its own, the `max` count is counted over the whole
   pool and the statement that does not fit is closed immediately and
   re-prepared on demand, the cache disappears with the physical session
   it was built on, and only the configured statement types are cached.
   Suite totals: 140 tests, ~80 s, 56.9 % instruction / 52.8 % branch.
3. **Old suite removal**: **done**. The harvest (§1 "Harvest result")
   landed first: 75 tests in 12 new classes plus one added
   `TestJmxMonitor` case. The JUnit 4 suite then went away together
   with its stub driver, the DBCP-comparison plumbing
   (`DefaultTestCase`, `DefaultProperties`), the `bugs/` classes, the
   test-only interceptors under `pool/interceptor/` and the two
   `pool/` unit tests they superseded, along with the `junit`,
   `tomcat-dbcp` and `junit-vintage-engine` test dependencies and the
   `PoolCleanerTest` exclude.
   At that point the module stood at 134 tests in about 80 s of test
   time (was ~6 min for 319) and 56.6 % instruction / 52.2 % branch
   coverage. The coverage number is *lower* than the 66.1 % measured one
   commit earlier, and that is the honest reading: the legacy tests ran
   through `DataSourceProxy`, the JMX and naming code and the whole
   interceptor surface with a stub driver attached, touching code
   without ever deciding whether it worked. Phase 5 starts its ratchet
   from the current number (56.9 % / 52.8 %).
4. **Defect processing** (later): per defect, a reproducer test per
   §4 + the fix; Postgres/kill tier added when the first defect
   needs it (single-session kill, `idle_session_timeout`,
   `max_connections`).
5. **Coverage gate**: `jacoco:check` ratchet (start ≈ green-suite
   reality, target >80 % line on `pool.*`), raised as phases 2–4
   progress.

## 6. Open points

* `defects-pool/` is deliberately left **untracked** for now (owner's
  call, and it stays out of the harvest commits). The reproducer tests
  of phase 4 cite it by name, so it will have to be committed before or
  with them (suggested home: `modules/jdbc-pool/docs/defects/`).
* Whether defect reproducers should also assert *log output* (many
  findings are "failed silently"): yes where a fix prescribes a
  log line; use a juli capture appender in the fixture.
* Behaviour observed while writing the phase 2 tests, deliberately
  **not** pinned by the baseline because it looks unintended rather
  than specified — check against `defects-pool/` when the relevant
  defect is processed:
  * closing the `Connection` obtained from an H2 `XAConnection`
    closes the *physical* connection, so the pool discards it
    instead of pooling it; returning only the `XAConnection` keeps
    it (what `TestXaConnection` asserts).
  * `ConnectionPool.close()` releases idle connections via
    `release()` but checked-out ones via `abandon()`, and only the
    former unregisters the per-connection
    `...,connections=PooledConnection[n]` MBean — a pool closed
    while connections are borrowed leaves those MBeans in the
    platform server.
* Found while harvesting, same status (unintended-looking, so not
  pinned):
  * `ProxyConnection`'s own `equals`/`hashCode`, reachable with the
    now-deprecated `useDisposableConnectionFacade=false`, used to throw
    `NullPointerException` (and now answer with the identity of the
    connection handle, as the facade-on configuration does). Fixed
    (the calls are answered before the XA dispatch branch); that
    configuration has since been deprecated away — the disposable
    facade is always used now, so the handling is defensive only —
    and the handle contract for such configurations is pinned by
    `pool/TestFacadeAlwaysUsed`, on top of the facade-level identity
    assertions in `TestProxyContract`.
  * the rest of `ProxyConnection`'s XA dispatch branch was over-
    matching the same way: methods declared by interfaces the handle
    implements but the pool cannot honour – such as
    `javax.sql.PooledConnection`'s `add`/`removeConnectionEventListener`
    on a plain pool – were dispatched at a `null` `XAConnection` and
    escaped as a raw `NullPointerException`. They now fail as an
    `SQLException` naming the method; `pool/TestXaBranchDispatch`
    pins that, with an XA-pool control showing the live dispatch
    still works.
  * a custom `Validator` that reports its failure by throwing — the
    only option, since the SPI declares no checked exception — escaped
    the unguarded call in `PooledConnection.validate()` and stranded the
    pooled connection between the idle and busy queues on borrow and on
    `close()` (a permanently lost slot), while aborting each idle sweep.
    A throw now counts as a failed validation, exactly like the `false`
    the built-in query/`isValid()` paths already return for any
    exception; `pool/TestThrowingValidator` pins the borrow, return and
    idle paths, with the already-safe create path and a plain-`false`
    validator as controls.
  * a borrow with alternate credentials that the database refuses
    leaves the pool having *closed the healthy idle session it
    already had*: size and server session count drop to 0 and the
    next ordinary borrow has to reconnect. The pool stays usable
    (`TestAlternateCredentials` pins that much), but the shrinkage is
    not something to lock in as expected behaviour.
* `TestStatementCacheReuse` pins the documented caching behaviour and
  deliberately stops there. The findings in
  `defects-pool/defects-...interceptor.StatementCache.md` stay unpinned:
  a cached statement keeps answering `getConnection()` with the
  *previous* borrower's facade (the pool rebuilds the JDBC handle on
  purpose, which is why the reuse tests compare the *driver* statement
  instead of the handle), the cache outliving a `reconnect()` whose
  purge hook was skipped, and the pool-wide `cacheSize` counter skewing
  when caching races `disconnected()`.
* `TestQueryTimeoutWithStatementCache` (2) pins the ordering dependency
  between the two: with `StatementCache` listed first, a cache hit skips
  the rest of the chain and the next borrower of the same SQL gets the
  timeout the *first* borrower configured or `0` after a recycle reset,
  not the one it configured - that case is pinned as the documented
  limitation, with the reset itself pinned first because H2 reports the
  session-wide value; the supported order (`StatementCache` last) is
  pinned to keep the configured timeout across a cache hit. Both are
  javadoc/wording fixes in the shipped code, not behaviour changes.
* `build.xml` (the Ant release build of this module) still has a
  `build-test` target that compiles `src/test/java` against a JUnit 4 +
  `tomcat-dbcp` + c3p0/derby/mysql classpath it downloads, and ships
  the result as `tomcat-jdbc-test.jar` in `dist`. That target cannot
  compile the current suite (JUnit 5, no DBCP) and has been stale since
  the functional tests arrived. Needs a decision: give the Ant
  test classpath the JUnit 5 aggregate and H2, or stop building and
  distributing `tomcat-jdbc-test.jar` and let the tests be a Maven-only
  concern.
* Statement-hygiene cases that this database cannot witness at all, so
  no amount of test code would pin them: `setMaxFieldSize` (H2 always
  reports 0), `setFetchDirection` (always `FETCH_FORWARD`),
  `isPoolable()` (always `false`, which is why `StatementCache` tracks
  the hint itself) and driver-generated warnings (none on
  `executeUpdate`). A Postgres tier could observe the first two.
