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
| **Keep** (port to JUnit 5 as-is; fast, pure-unit, no fake DB) | `TestJdbcInterceptorConfigParsing`, `TestInterceptorShortName`, `TestSlowQueryComparator`, `PoolPropertiesTest` |
| **Harvest scenario → rewrite against real DB** | `Bug50571`, `Bug50805`, `Async0IdleTestBug50477` (regression memory), `TestConnectionState` (→ defect #3 repro), `TestGCClose` (GC-close semantics), `MultipleCloseTest`, `EqualsHashCodeTest`, `AlternateUsernameTest` (H2 supports multiple DB users → now a *real* test), `PoolPurgeTest`, `TestValidation`, `TestValidationQueryTimeout`, `TestTimeout`, `TestSuspectTimeout`, `TestGetConnection`, `TwoDataSources`, `JmxPasswordTest` (→ credential-exposure defect), `TestQueryTimeoutInterceptor`, `TestStatementCache`, `TestSlowQueryReport`, `StatementFinalizerTest` |
| **Delete** (benchmarks, sleep-drift, DBCP/c3p0 comparisons, stub plumbing, examples) | `FairnessTest`, `CheckOutThreadTest`, `ConnectCountTest`, `StarvationTest`, `AbandonPercentageTest`, `TestConcurrency`, `TestSizePreservation`, `BorrowWaitTest`, `TestAsyncQueue`, `TestException`, `ProxiedResultSetTest`, `ProxiedStatementTest`, `PoolCleanerTest` (flaky), `CreateTestTable`, `DefaultTestCase`/`DefaultProperties` (DBCP plumbing), `SimplePOJO*Example`, `driver/*` |

Benchmarks, if wanted later, go to a JMH profile; they never gate CI.
The old suite is preserved in git history only.

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
3. **Dead-code coverage.** `MultiLockFairBlockingQueue` is
   unreachable through the pool (instantiation commented out, no
   `lockCount` property); if it is kept, it needs direct unit tests
   in the later phase — not in the functional baseline.

Infrastructure: H2-TCP fixture (per-class server + fresh DB name);
state-hygiene extension (every `DataSource` closed, pool statics and
server session count back to baseline after each test — this kills
the `PoolCleanerTest` flake class permanently); Awaitility instead
of sleeps; `-Pdocker` profile for the Postgres tier.

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
| 10. `MultiLockFairBlockingQueue` ×3 | direct unit tests: cross-stripe starvation, interrupt-ghost-waiter, empty `FairIterator` |

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
   **Done**: `org.apache.tomcat.jdbc.functional` package.
2. **Spec tests** (green gate) for core pool + proxies +
   interceptors, against H2-TCP: acquire/reuse, statement
   execution, transactions, sizing/eviction/lifecycle first; then
   validation variants, fairness queues, interceptor chain
   semantics, JMX with live traffic, XA, JNDI factories.
   **Done except the validation variants**: `InterceptorSemanticsTest`
   (slow/failed query reporting, `QueryTimeoutInterceptor`
   cancellation, `StatementCache`, `StatementDecoratorInterceptor`),
   `JmxMonitorTest` (pool + per-connection MBeans with live
   traffic), `XaConnectionTest` (real 2-phase commit and rollback
   against H2's XA resource), `NamingFactoryTest`
   (`DataSourceFactory`, `GenericNamingResourcesFactory`) and
   `FairBlockingQueueTest` (most-recently-used hand-off, blocking
   retrieval, waiter fairness, documented-unsupported operations)
   join the phase 1 classes: 59 functional tests, ~72 s, full module
   green at 244 tests. Coverage of the module is now 64.8 %
   instruction / 58.9 % branch.
   Still open here: the validation variants (`testOnBorrow`,
   `testOnReturn`, `testWhileIdle`; query vs `isValid()` vs a custom
   `Validator`) — cheapest to write next to the defects that touch
   them. `InterceptorSemanticsTest` alone costs ~60 s: real timeout
   cancellation needs a real multi-second query (H2 has no `SLEEP()`
   and an in-process `CREATE ALIAS` is not cancel-safe), so the
   "< 60 s" budget is understood to exclude it.
3. **Old suite removal**: benchmarks → optional JMH profile;
   harvest the remaining regression scenarios (`Bug50571`,
   `Bug50805`, `Bug50477`) as spec tests; delete `driver/`,
   `DefaultTestCase` DBCP plumbing, DBCP test dependency.
4. **Defect processing** (later): per defect, a reproducer test per
   §4 + the fix; Postgres/kill tier added when the first defect
   needs it (single-session kill, `idle_session_timeout`,
   `max_connections`).
5. **Coverage gate**: `jacoco:check` ratchet (start ≈ green-suite
   reality, target >80 % line on `pool.*`), raised as phases 2–4
   progress.

## 6. Open points

* `defects-pool/` is currently untracked. The reproducer tests cite
  it by name, so the catalog should be committed alongside this
  suite (suggested home: `modules/jdbc-pool/docs/defects/`). Needs a
  decision (it's outside this module, in the repo root).
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
    it (what `XaConnectionTest` asserts).
  * `ConnectionPool.close()` releases idle connections via
    `release()` but checked-out ones via `abandon()`, and only the
    former unregisters the per-connection
    `...,connections=PooledConnection[n]` MBean — a pool closed
    while connections are borrowed leaves those MBeans in the
    platform server.
