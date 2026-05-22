# Tomcat TODO & FIXME Report

> Generated: 2026-05-09
> Total items documented: ~120 (after deduplication of repetitive patterns)

---

## Table of Contents

- [CRITICAL FIXMEs](#critical-fixmes)
- [High-Priority TODOs](#high-priority-todos)
- [Medium-Priority TODOs](#medium-priority-todos)
- [Low-Priority / Cosmetic TODOs](#low-priority--cosmetic-todos)
- [Documentation TODOs](#documentation-todos)
- [Test Code TODOs](#test-code-todos)
- [Auto-Generated Stub TODOs](#auto-generated-stub-todos)

---

## CRITICAL FIXMEs

These are bugs, correctness issues, or missing functionality that may affect production behavior.

### 1. DeltaManager Session Replication Issues (7 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 1.1 | `DeltaManager.java:692` | Sessions with same ID replaced without notification | Fire a `sessionReplace` notification event before overwriting; update all listeners (SSO, clustering) to handle the replace event | 2-3 days | High |
| 1.2 | `DeltaManager.java:694` | SSO handling incorrect with session replacement | Coordinate with `SingleSignOn` valve to invalidate/recreate SSO tokens on session replace. Requires cross-component changes. | 3-5 days | Very High |
| 1.3 | `DeltaManager.java:723` | No way to inform SSO and other session ID caches of replacement | Add a callback interface (e.g., `SessionIdChangeListener`) that SSO and other components register with. DeltaManager invokes on replace. | 3-5 days | High |
| 1.4 | `DeltaManager.java:728` | Existing session should be re-grabbed instead of overwritten | Before adding the new session, serialize and re-broadcast the existing session to preserve its state, then merge. | 2-3 days | High |
| 1.5 | `DeltaManager.java:842` | `cluster.send()` blocks deploy thread when `waitForAck` is enabled | Move the send+wait into a dedicated thread or use async send with a Future/CompletableFuture. | 1-2 days | Medium |
| 1.6 | `DeltaManager.java:852` | At sender ack mode, only state transfer is checked; resend is problematic | Implement proper resend logic with sequence numbers and timeout-based retry. | 2-3 days | High |
| 1.7 | `DeltaManager.java:863` | `EVT_GET_ALL_SESSIONS` events not handled in queued processing | Route `EVT_GET_ALL_SESSIONS` through the same messageReceived path after state transfer completes. | 1 day | Medium |

**Summary:** These 7 items are interrelated. The session replication replacement logic is fundamentally incomplete. A holistic redesign of the `deserializeSessions` and state transfer flow is needed.

**Total estimated effort: 14-24 days, Very High difficulty**

---

### 2. ResolverImpl SSL Rewrite Variables (8 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 2.1 | `ResolverImpl.java:153` | `SSL_SESSION_RESUMED` - session resumption state not available | Expose session resumption flag from `SSLSession` via `Ssl` handler interface. Requires SSLEngine change. | 2-3 days | High |
| 2.2 | `ResolverImpl.java:155` | `SSL_SECURE_RENEG` - secure renegotiation not available from SSLHostConfig | Add `isSecureRenegotiation()` to `SSLHostConfig` and surface through `Ssl` interface. | 1-2 days | Medium |
| 2.3 | `ResolverImpl.java:157` | `SSL_COMPRESS_METHOD` - compression method not available | TLS compression is deprecated (RFC 6520). Consider returning empty string or "NONE" and documenting. | 0.5 day | Low |
| 2.4 | `ResolverImpl.java:159` | `SSL_TLS_SNI` - SNI hostname not available from handshake | Capture SNI during handshake in `Ssl` handler. Already available via `SSLEngineResult` or custom extension. | 1 day | Medium |
| 2.5 | `ResolverImpl.java:198` | `SSL_CLIENT_SAN_OTHER_*` OID resolution incomplete | Parse OID from the key string and match against SAN otherName entries in `resolveAlternateName`. | 1-2 days | Medium |
| 2.6 | `ResolverImpl.java:200` | `CERT_RFC4523_CEA` - CertificateExactAssertion not implemented | Build the CEA string per RFC 4523 from `certificate[0]` using SHA-1 hash of the cert. | 1-2 days | Medium |
| 2.7 | `ResolverImpl.java:202` | `SSL_CLIENT_VERIFY` - verification state not available | Expose verification result from the `Ssl` handler. Requires changes to SSL post-handshake processing. | 1-2 days | Medium |
| 2.8 | `ResolverImpl.java:215` | `SSL_SERVER_SAN_OTHER_*` OID resolution incomplete | Same as 2.5 but for server certificate SAN entries. | 1-2 days | Medium |

**Summary:** Most items require plumbing SSL/TLS handshake details through the `Ssl` interface to make them available for rewrite rules. Item 2.3 can be resolved immediately by returning "NONE".

**Total estimated effort: 8-14 days, Medium difficulty**

---

### 3. RewriteValve Missing Map Types (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 3.1 | `RewriteValve.java:736` | `dbm:` rewrite map type not implemented | Implement a `DbmRewriteMap` class using Berkeley DB Java Edition (JE) or SQLite as a backend. Or provide a JDBC-based generic map. | 3-5 days | High |
| 3.2 | `RewriteValve.java:739` | `dbd:` / `fastdbd:` rewrite map types not implemented | Implement `DbdRewriteMap` using Apache DBCP to connect to a database and look up rewrite values. Requires config for DSN, user, password, query. | 3-5 days | High |

**Summary:** These are Apache HTTPD compatibility features. The codebase already has a `RewriteMap` interface; implementations just need to be built.

**Total estimated effort: 6-10 days, High difficulty**

---

### 4. ChannelCoordinator Synchronization Issue (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 4.1 | `ChannelCoordinator.java:163` | Race condition: receiver started but local member not yet available | Add a synchronization barrier or callback between `clusterReceiver.start()` and `getLocalMember(false)`. Use a `CountDownLatch` or wait for the receiver's ready signal. | 1-2 days | High |

**Summary:** This is a concurrency bug that could cause `null` member references during cluster startup.

**Total estimated effort: 1-2 days, High difficulty**

---

### 5. PersistentManagerBase LRU Algorithm (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 5.1 | `PersistentManagerBase.java:804` | Sessions swapped to disk in arbitrary order, not LRU | Sort sessions by `getLastAccessedTime()` before selecting which to swap. Swap the least-recently-used sessions first. | 1 day | Low |

**Summary:** Simple fix. Sort the sessions array by access time before the swap loop.

**Total estimated effort: 1 day, Low difficulty**

---

### 6. DBCP DelegatingStatement Double-Close (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 6.1 | `DelegatingStatement.java:145` | ResultSets may be closed twice (bug 17301) | Track which ResultSets have already been closed by the delegate. Only close traced ResultSets that the delegate did not close. | 1-2 days | Medium |
| 6.2 | `DelegatingPreparedStatement.java:180` | Same double-close issue (DBCP-10) | Same fix as 6.1, applied to the prepared statement variant. | 1-2 days | Medium |

**Summary:** Both files have the same issue. A shared fix in the parent delegation logic would address both.

**Total estimated effort: 2-4 days, Medium difficulty**

---

### 7. ManagedBean / BaseModelMBean Issues (3 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 7.1 | `BaseModelMBean.java:520` | `removeAttributeChangeNotificationListener` removes ALL notifications for a listener | Track per-attribute listener registrations. Only remove the listener for the specified attribute name. | 1 day | Medium |
| 7.2 | `ManagedBean.java:606` | Method signature from `opInfo` not used for reflection lookup | Use the signature from `opInfo` to locate the method, falling back to the MBean descriptor signature. | 0.5-1 day | Low |
| 7.3 | `ManagedBean.java:614` | Methods declared in superinterfaces not found by reflection | Walk the class hierarchy and all implemented interfaces when searching for the method. | 0.5-1 day | Low |

**Summary:** JMX MBean infrastructure issues. 7.1 is a behavioral bug; 7.2 and 7.3 are limitations.

**Total estimated effort: 2-3 days, Low-Medium difficulty**

---

### 8. PooledConnectionImpl Error Notification (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 8.1 | `PooledConnectionImpl.java:383` | When pooled connection is reused without closing previous Connection, pool is not notified of error | Call `connectionEventListener.connectionErrorOccurred()` before throwing the SQLException, so the pool can remove the bad connection. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 9. JspRuntimeLibrary RequestDispatcher Issue (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 9.1 | `JspRuntimeLibrary.java:1160` | Cannot use `request.getRequestDispatcher()` for relative paths inside includes due to Catalina spec issue | Revisit after Servlet spec clarification. If Catalina now handles this correctly, switch to the simpler approach. Otherwise, keep current workaround. | 0.5-1 day | Low |

**Summary:** This is a spec-compatibility workaround. Low priority unless the spec is clarified.

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 10. Jasper Generator Tag Handler Validation (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 10.1 | `Generator.java:3890` | Reserved property names `pageContext`, `bodyContent`, `parent` not filtered during tag handler introspection | Add a `Set<String>` of reserved names and skip them in the property descriptor loop. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 11. JMX Ant Task Issues (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 11.1 | `JMXAccessorTask.java:565` | Cannot transfer values from Ant project reference store | Support Ant `refid` syntax in the value attribute. Look up referenced project properties during conversion. | 1-2 days | Medium |
| 11.2 | `JMXAccessorEqualsCondition.java:77` | URL and host/parameter not validated before JMX access | Add validation of connection parameters before attempting JMX access. Throw `BuildException` for invalid config. | 0.5 day | Low |

**Total estimated effort: 1.5-2.5 days, Low-Medium difficulty**

---

### 12. OpenSSL Engine Limitations (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 12.1 | `OpenSSLEngine.java:1304` | `getLocalCertificates()` returns empty array - not available in OpenSSL API | Use `SSL.getPeerCertificateChain()` equivalent for local cert chain, or cache the certificate during handshake initialization. | 2-3 days | High |
| 12.2 | `panama/OpenSSLEngine.java:1617` | Same limitation in Panama-based OpenSSL engine | Same fix as 12.1, applied to the Panama variant. | 2-3 days | High |

**Summary:** Both are API limitations in OpenSSL. May require caching at handshake time.

**Total estimated effort: 4-6 days, High difficulty**

---

## High-Priority TODOs

### 13. ThreadPoolExecutor AQS Migration (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 13.1 | `ThreadPoolExecutor.java:466` | `completedTasks` not in lock word; should use `AbstractQueuedLongSynchronizer` | Refactor Worker to embed `completedTasks` into AQS state word for CAS-based updates. Requires careful synchronization redesign. | 5-7 days | Very High |

**Summary:** This is a deep concurrency optimization that mirrors the JDK `ThreadPoolExecutor` design.

**Total estimated effort: 5-7 days, Very High difficulty**

---

### 14. AbstractEndpoint AsyncStateMachine (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 14.1 | `AbstractEndpoint.java:101` | `ASYNC_END` state should be removed; needs new state in AsyncStateMachine | Design a cleaner state machine that eliminates the need for `ASYNC_END`. Requires audit of all state transitions. | 3-5 days | High |

**Total estimated effort: 3-5 days, High difficulty**

---

### 15. WebSocket Server Close Frame Processing (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 15.1 | `WsRemoteEndpointImplServer.java:106` | Close frame processing should be non-blocking | Implement async close handshake: queue the close frame and let the NIO selector handle the send without holding the socket lock. | 3-5 days | High |

**Total estimated effort: 3-5 days, High difficulty**

---

### 16. WebSocket Masking Location (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 16.1 | `WsFrameBase.java:1055` | Masking should move to `sendMessagePart` method | Move the masking logic from the current location into `sendMessagePart` for cleaner separation of concerns. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 17. SocketWrapperBase Write Interest Enforcement (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 17.1 | `SocketWrapperBase.java:511` | `isReadyForWrite()` restriction not enforced in `registerWriteInterest()` | Add a state guard in `registerWriteInterest()` that throws `IllegalStateException` if called when a pending write callback hasn't fired. | 1 day | Medium |

**Total estimated effort: 1 day, Medium difficulty**

---

### 18. WebSocket POJO Handler Accessibility (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 18.1 | `PojoMessageHandlerBase.java:102` | `setAccessible(true)` needed in some cases though method should be accessible | Investigate the specific cases where accessibility fails. If it's a module/system issue, document and keep. Otherwise remove. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 19. TLS 1.0 Priming Read (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 19.1 | `OpenSSLEngine.java:664` | TLS 1.0 requires additional priming read - reason unknown | Investigate whether this is a TLS 1.0 protocol quirk or an OpenSSL version-specific bug. If protocol-related, document and keep. | 1-2 days | Medium |

**Summary:** TLS 1.0 is deprecated. May be safe to remove if TLS 1.0 support is dropped.

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 20. Rfc6265CookieProcessor HTTP/2 Review (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 20.1 | `Rfc6265CookieProcessor.java:95` | Cookie byte expectation should be reviewed for HTTP/2 | HTTP/2 headers are HPACK-compressed but decoded to strings. Verify the `T_BYTES` check is still correct after HPACK decoding. | 1 day | Medium |

**Total estimated effort: 1 day, Medium difficulty**

---

### 21. Cookie Name Validation Per-Context (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 21.1 | `Rfc6265CookieProcessor.java:122` | Cookie name validation is global, not per-Context | Move validation into the `CookieProcessor` interface. Allow per-Context configuration of validation strictness. Spec-compliant but breaking. | 2-3 days | High |

**Total estimated effort: 2-3 days, High difficulty**

---

### 22. EL ReflectionUtil Refactoring (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 22.1 | `ReflectionUtil.java:455` | `isCoercibleFrom` uses try/catch instead of proper type checking | Refactor `ELSupport.coerceToType()` to expose a non-throwing `canCoerce` method, or implement type compatibility check without invocation. | 2-3 days | High |

**Total estimated effort: 2-3 days, High difficulty**

---

### 23. HTTP/2 HPACK Encoder Optimization (3 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 23.1 | `HpackEncoder.java:55` | Huffman threshold value of 5 is arbitrary | Benchmark different threshold values (3, 5, 7, 10) with real-world header data. Choose optimal. | 1-2 days | Medium |
| 23.2 | `HpackEncoder.java:60` | Same Huffman threshold for header names | Same benchmarking as 23.1 for header name lengths. | 1-2 days | Medium |
| 23.3 | `HpackEncoder.java:79` | `HashMap` for dynamic table causes allocations | Implement a custom LRU-backed data structure with pre-allocated nodes to reduce GC pressure. | 2-3 days | High |

**Total estimated effort: 4-7 days, Medium-High difficulty**

---

### 24. HTTP/2 Constants Tuning (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 24.1 | `Constants.java:31` | 1KB default header frame size may not be optimal | Benchmark with various frame sizes under different load patterns. | 1-2 days | Medium |
| 24.2 | `Constants.java:36` | 64-byte ACK frame size may be too large | Measure actual ACK frame sizes in production. Adjust if consistently smaller. | 0.5-1 day | Low |

**Total estimated effort: 1.5-3 days, Low-Medium difficulty**

---

### 25. HTTP/2 Stream Trailer Refactoring (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 25.1 | `Stream.java:644` | Copying Map to MimeHeaders may be inefficient | Investigate whether MimeHeaders can accept a Map directly, or if a lighter-weight intermediate structure is viable. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 26. HTTP/1.1 Connection Close Header (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 26.1 | `Http11Processor.java:421` | `Connection: close` header set explicitly - may be redundant | Verify that the connector already closes the connection in this error path. If so, the header is redundant and can be removed. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 27. AjpProcessor Flush Assertion (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 27.1 | `AjpProcessor.java:996` | Assertion about empty buffers for non-blocking writes not validated | Add an `assert` or debug check in `flush()` to verify buffers are empty. Log warning if not. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 28. AbstractProtocol ALPN Handling (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 28.1 | `AbstractProtocol.java:1269` | OpenSSL 1.0.2 ALPN workaround - replace with proper handshake failure once OpenSSL supports it | Check if minimum supported OpenSSL version now supports ALPN failure. If yes, replace with the commented-out block. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 29. AbstractProcessor Non-Blocking IO Confirmation (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 29.1 | `AbstractProcessor.java:989` | Unclear if socket queue requirement applies without APR | Test with NIO and NIO2 connectors. Document findings. Update comment with confirmed behavior. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 30. WebXml Internationalization Support (20+ items, consolidated)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 30.1 | `WebXml.java:417-591` | Many ignored elements: `description`, `display-name`, `icon`, `init-param/description`, `security-role-ref/description` with language support | Add `LocaleElement` wrapper class for i18n elements. Update all affected classes in `WebXml` to store `List<LocaleElement>` instead of `String`. Update parser, serializer, and all consumers. | 10-15 days | Very High |
| 30.2 | `WebXml.java:1866` | `handler-chains` not serialized in web.xml | Add serialization logic for handler chains in the `appendServiceRef` method. | 1-2 days | Medium |

**Summary:** Item 30.1 encompasses ~20 individual TODOs, all related to the same root cause: lack of i18n support in web.xml descriptor elements. They should be fixed together.

**Total estimated effort: 11-17 days, Very High difficulty**

---

### 31. Jasper Compiler Optimizations (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 31.1 | `Compiler.java:219` | Two-pass parsing for `isELIgnored` could be optimized | Cache directive results from pass 1. Skip re-parsing unchanged includes. Use incremental parsing for modified files. | 2-3 days | High |
| 31.2 | `JspServletWrapper.java:254` | Potential inefficiency between reload and `isOutDated()` check | Audit the reload flow. Check if `isOutDated()` is called redundantly after reload. | 1 day | Medium |

**Total estimated effort: 3-4 days, Medium-High difficulty**

---

### 32. Jasper TLD Validation (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 32.1 | `TagLibraryInfoImpl.java:193` | Duplicate function name validation should move to parsing stage | Add duplicate name detection in the TLD parser (`TaglibXmlParser`) before the `TagLibraryInfoImpl` is constructed. | 1 day | Medium |
| 32.2 | `TagLibraryInfoImpl.java:231` | URL resolution logic for TLD resource paths looks incorrect | Audit the URI resolution logic against JSP spec section 7.3.6.2. Fix any deviations. | 1-2 days | Medium |

**Total estimated effort: 2-3 days, Medium difficulty**

---

### 33. Jasper Jar URL Resolution (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 33.1 | `JspDocumentParser.java:233` | `jar:jar:` URLs from Jar abstraction cannot be resolved by JRE | Implement a custom `EntityResolver` that detects `jar:jar:` URLs and uses `JarFactory` to construct valid `InputSource`. | 2-3 days | High |

**Total estimated effort: 2-3 days, High difficulty**

---

### 34. EL Function Precedence (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 34.1 | `AstFunction.java:88` | Lambda expression vs function name precedence unclear | Define explicit precedence rules: lambda arguments > function mapper > bean property. Document in spec. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 35. Jasper Ant Logging (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 35.1 | `JspC.java:1775` | Uses `System.out` instead of Ant Project log | Pass the Ant `Project` reference through to `initServletContext` and use `Project.log()` for output. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 36. Jasper JspC ClassLoader / Filter Config (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 36.1 | `JspCServletContext.java:206` | `setScanClassPath(false)` requires classloader initialization | Enable classpath scanning by initializing classloader earlier in the JspC lifecycle. | 1-2 days | Medium |
| 36.2 | `JspCServletContext.java:208` | Filter rules hardcoded from system properties | Add Ant task attributes for include/exclude patterns. Pass to `StandardJarScanFilter` constructor. | 1-2 days | Medium |

**Total estimated effort: 2-4 days, Medium difficulty**

---

## Medium-Priority TODOs

### 37. Connection Pool Blocking Queue (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 37.1 | `ConnectionPool.java:88` | `busy` queue uses `BlockingQueue` but blocking is not needed for in-use connections | Replace `BlockingQueue<PooledConnection>` busy with `ConcurrentHashMap<Key, PooledConnection>` for O(1) lookup by connection. | 2-3 days | Medium |

**Total estimated effort: 2-3 days, Medium difficulty**

---

### 38. Connection Pool Statement Facade Optimization (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 38.1 | `ConnectionPool.java:339` | New facade generated every borrow even if connection returned properly | Track a `cleanReturn` flag on pooled connection. Reuse existing facade if flag is set. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 39. Connection Pool Thread Dump Optimization (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 39.1 | `ConnectionPool.java:1273` | Stores full stack trace string; could store `StackTraceElement[]` directly | Change `getThreadDump()` to return `StackTraceElement[]`. Store array and format on-demand for display. Saves memory. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 40. JDBC Pool JNDI Lookup (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 40.1 | `PooledConnection.java:229` | JNDI data source lookup not implemented in reconnect path | Implement JNDI lookup in the reconnection logic. Use `InitialContext.lookup()` with the configured JNDI name. | 1 day | Medium |

**Total estimated effort: 1 day, Medium difficulty**

---

### 41. JDBC Pool Slow Query Eviction (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 41.1 | `SlowQueryReport.java:199` | ConcurrentHashMap for per-pool stats has no eviction | Add a size limit with LRU eviction (e.g., `LinkedHashMap` with `removeEldestEntry`, or a custom bounded concurrent map). | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 42. JDBC Pool JNDI Documentation (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 42.1 | `PoolConfiguration.java:792` | JNDI string rules not documented | Document the expected JNDI name format (e.g., `java:comp/env/jdbc/MyDS`) in the JavaDoc. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty**

---

### 43. DBCP Connection String Edge Case (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 43.1 | `DriverAdapterCPDS.java:811` | `toString()` may leak credentials if connection string contains user/password params | Redact `user=` and `password=` query parameters in the `toString()` output. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 44. DBCP Pool Object toString Enhancements (3 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 44.1 | `DefaultPooledObject.java:330` | `toString()` missing attributes | Add allocation time, last use time, borrow count, validation state to `toString()`. | 0.5 day | Low |
| 44.2 | `PooledSoftReference.java:93` | Same missing attributes | Same fix as 44.1. | 0.5 day | Low |
| 44.3 | `PooledSoftReference.java:94` | State display should be encapsulated in parent | Extract `toString()` state formatting into `DefaultPooledObject` as a protected method. | 0.5 day | Low |

**Total estimated effort: 1.5 days, Low difficulty**

---

### 45. DBCP Pool Config Consolidation (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 45.1 | `BaseObjectPoolConfig.java:303` | `jmxNamePrefix` and `jmxNameBase` could be a single property | For DBCP 3.x, merge into `jmxName` with automatic prefix/suffix handling. Backward-incompatible. | 1 day | Medium |

**Total estimated effort: 1 day, Medium difficulty (defer to 3.x)**

---

### 46. DBCP Java 9+ TimeUnit Conversion (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 46.1 | `PoolImplUtils.java:198` | Manual `TimeUnit` to `ChronoUnit` mapping | When minimum Java version is 9+, replace with `TimeUnit.toChronoUnit()`. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty (defer to Java 9+ baseline)**

---

### 47. DBCP LinkedBlockingDeque Bulk Operations (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 47.1 | `LinkedBlockingDeque.java:1147` | Bulk operations (addAll, removeAll, etc.) not implemented efficiently | Implement bulk operations with batch lock acquisition. Balance throughput vs. fairness. | 2-3 days | High |

**Total estimated effort: 2-3 days, High difficulty**

---

### 48. DBCP Eviction State Handling (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 48.1 | `DefaultPooledObject.java:82` | Object not allocated when in EVICTION state and borrow attempted | Allocate the object and set state to `IN_USE`, bypassing the eviction test result. | 0.5-1 day | Low |
| 48.2 | `PooledObjectState.java:46` | Same - consider allocating and ignoring eviction test | Same as 48.1. These two items are the same fix. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 49. DBCP Eviction Pre-allocation (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 49.1 | `DefaultPooledObject.java:85` | Pre-allocate for performance when `testOnBorrow == true` | When in eviction test and `testOnBorrow` is enabled, pre-create the replacement object in parallel with the validation check. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 50. File Upload Delete Failure Tracking (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 50.1 | `FileUploadBase.java:490` | Failed temp file deletions silently ignored | Add a `List<File>` of failed deletions to the progress tracker. Expose via API for caller cleanup. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 51. MultipartStream Skip Failure (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 51.1 | `MultipartStream.java:202` | When `skip()` fails to skip expected bytes, no action taken | Log a warning at debug level. Consider throwing `IOException` if the stream is in a corrupt state. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 52. DiskFileItem getString Exception (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 52.1 | `DiskFileItem.java:371` | `getString()` should consider throwing `UnsupportedEncodingException` | The charset may be unavailable. Add the checked exception to the signature (API-breaking) or wrap in `UncheckedIOException`. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 53. BCEL Annotation Visibility (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 53.1 | `ElementValue.java:149` | `isRuntimeVisible` flag not parsed for annotation element values | Read the runtime visibility flag from the class file constant pool and store it. Requires `AnnotationEntry` changes. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 54. WebResource Archive Certificate Loading (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 54.1 | `AbstractArchiveResource.java:265` | `getCertificates()` throws `IllegalStateException` if content not read first | Lazy-load certificates from the JAR's `Certificate[]` on first call to `getCertificates()`. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 55. WebResource StandardRoot Refactoring (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 55.1 | `StandardRoot.java:392` | `createWebResourceSet()` factory methods could be extracted | Extract into a `WebResourceSetFactory` utility class. Reduces `StandardRoot` complexity. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 56. LoadBalancerDrainingValve SSO Cookies (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 56.1 | `LoadBalancerDrainingValve.java:202` | Only primary session cookie is cleared; SSO cookies remain | Detect and clear SSO cookies (e.g., `MEMBER_SESSION`) alongside the primary session cookie. | 1 day | Medium |

**Total estimated effort: 1 day, Medium difficulty**

---

### 57. SpnegoAuthenticator GSSContext Statelessness (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 57.1 | `SpnegoAuthenticator.java:257` | Assumption that GSSContext is stateless needs confirmation | Review GSSAPI spec and test with multiple JDK implementations. If stateless, document. If not, add synchronization. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 58. ApplicationHttpRequest Encoding (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 58.1 | `ApplicationHttpRequest.java:861` | Query string encoding should respect `useBodyEncodingForURI` and `URIEncoding` | Access the connector's URI encoding settings through the request's connector reference. Apply correct encoding rules. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 59. ApplicationContext Spec-Breaking Enhancements (3 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 59.1 | `ApplicationContext.java:671` | `addFilter` state check could be relaxed | Remove `checkState()` call or make it conditional on a context flag. Document the deviation from spec. | 0.5 day | Low |
| 59.2 | `ApplicationContext.java:784` | Same for `addServlet` | Same approach as 59.1. | 0.5 day | Low |
| 59.3 | `ApplicationContext.java:1035` | Same for `addListener` | Same approach as 59.1. | 0.5 day | Low |

**Total estimated effort: 1.5 days, Low difficulty**

---

### 60. Tomcat Embedding Improvements (4 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 60.1 | `Tomcat.java:81` | Temp dir should be lazily initialized | Create temp dir only when `getTempDir()` is called or a JSP compilation is triggered. | 1 day | Medium |
| 60.2 | `Tomcat.java:85` | Contexts should work without a base dir for fully programmatic apps | Allow `docBase` to be null. Disable default servlet and static resource handling in this mode. | 2-3 days | High |
| 60.3 | `Tomcat.java:192` | Work dir should be disabled when not needed | Add a `setWorkDir(null)` or `setWorkEnabled(false)` option. Skip work dir creation when no JSPs are deployed. | 1 day | Medium |
| 60.4 | `Tomcat.java:311` | `addContext` should support more configuration options | Add overloads for common embedded scenarios: virtual hosts, loaders, realms, etc. | 2-3 days | Medium |

**Total estimated effort: 6-9 days, Medium difficulty**

---

### 61. HostConfig Watched Resources Pattern Support (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 61.1 | `HostConfig.java:1268` | Wildcard patterns for watched resources not supported | Add pattern matching (e.g., `WEB-INF/*.xml`). On reload check, only compare timestamps of matching files. | 2-3 days | Medium |

**Total estimated effort: 2-3 days, Medium difficulty**

---

### 62. FarmWarDeployer Improvements (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 62.1 | `FarmWarDeployer.java:225` | Second deployment attempt timing issue when app is in service | Add state checking before second deploy attempt. Wait for app to reach `STARTED_PREP` state. | 1-2 days | Medium |
| 62.2 | `FarmWarDeployer.java:470` | Work directory content not cleaned on remove | Call `context.clearWorkDir()` or recursively delete the work directory during undeploy. | 0.5-1 day | Low |

**Total estimated effort: 1.5-3 days, Low-Medium difficulty**

---

## Low-Priority / Cosmetic TODOs

### 63. CGIServlet Feature List (10+ items, consolidated)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 63.1 | `CGIServlet.java:186-199` | Multiple incomplete features: Location headers, header collapsing, POST+Filters, debug code, encoding, refactoring, stdin handling, IOException, documentation, `available()` usage | These are long-standing issues in the CGI servlet. Prioritize based on user reports. Header support and POST handling are most impactful. | 10-20 days | Very High |

**Summary:** The CGI servlet is a best-effort implementation. Each sub-item could be its own ticket.

**Total estimated effort: 10-20 days, Very High difficulty**

---

### 64. Diagnostics Code Quality (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 64.1 | `Diagnostics.java:18` | Source code line length exceeds limits | Run formatter. Break long lines. | 0.25 day | Low |
| 64.2 | `Diagnostics.java:19` | More JavaDoc needed | Add class-level and method-level JavaDoc. | 0.5 day | Low |

**Total estimated effort: 0.75 day, Low difficulty**

---

### 65. MBeanDumper Escape Logic (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 65.1 | `MBeanDumper.java:179` | String escaping for newlines marked as XXX TODO | The current implementation appears functional. Remove the XXX TODO marker and add a brief comment explaining the logic. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty**

---

### 66. Tapestry Session Support (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 66.1 | `SessionUtils.java:113` | Tapestry 4+ session data patterns not recognized | Research Tapestry 4+ session key naming conventions. Add matching patterns to the locale detection logic. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 67. StoreConfig Improvements (7 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 67.1 | `StoreFileMover.java:32` | Encoding not obtained from Registry | Look up encoding from `Registry` at runtime instead of hardcoding. | 0.5 day | Low |
| 67.2 | `StoreContextAppender.java:176` | Default context.xml not interpreted | Parse default `context.xml` and `context.xml.default` and apply defaults during instance generation. | 2-3 days | High |
| 67.3 | `StoreContextAppender.java:178` | Default StandardContext not cached | Cache a prototype `StandardContext` with default configuration for reuse. | 1 day | Medium |
| 67.4 | `StoreContextAppender.java:180` | Duplicate element removal is incomplete | Implement class-based deduplication for listeners and valves. | 1-2 days | Medium |
| 67.5 | `StandardEngineSF.java:58` | Parent realm check may be unnecessary | Verify if `Engine.getParent()` can ever be non-null. If not, remove the dead code. | 0.25 day | Low |
| 67.6 | `StandardContextSF.java:234` | Same parent realm question | Same as 67.5 for Context. | 0.25 day | Low |
| 67.7 | `StandardContextSF.java:307-313` | Relative resources, absolute config file, Windows case sensitivity, digester variable substitution | Four separate sub-items. Each requires careful path handling. | 3-5 days | High |

**Total estimated effort: 8-12 days, Medium-High difficulty**

---

### 68. Tribes ReplicatedMap Features (3 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 68.1 | `ReplicatedMap.java:51` | Periodic sync/transfer thread not implemented | Add a scheduled task that periodically syncs map entries across cluster members. | 2-3 days | High |
| 68.2 | `ReplicatedMap.java:52` | `memberDisappeared` should only change membership, not relocate | Modify `memberDisappeared` handler to update membership set without triggering primary object relocation. | 1-2 days | Medium |
| 68.3 | `LazyReplicatedMap.java:63` | Same periodic sync missing | Same as 68.1 for lazy variant. | 2-3 days | High |

**Total estimated effort: 5-8 days, Medium-High difficulty**

---

### 69. Tribes Thread Safety (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 69.1 | `McastServiceImpl.java:558` | `DatagramSocket.send()` not thread-safe | The code already uses `synchronized (sendLock)`. Verify the lock covers all send paths. If so, remove the TODO. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 70. Tribes ByteBuffer Pooling (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 70.1 | `XByteBuffer.java:87` | No pooling of byte arrays for performance | Implement a `ThreadLocal<byte[]>` pool or use a `ByteBuffer` pool (e.g., `XByteBufferPool`). | 2-3 days | Medium |

**Total estimated effort: 2-3 days, Medium difficulty**

---

### 71. Tribes UDP Keepalive (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 71.1 | `AbstractSender.java:121` | UDP connections always disconnected; keepalive not optimized | Implement UDP keepalive by tracking last activity time and only disconnecting after idle timeout. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 72. Tribes NIO Optimization (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 72.1 | `NioSender.java:442` | Data copied into ByteBuffer instead of wrapped | Use `ByteBuffer.wrap(data, offset, length)` where the data lifetime allows. Profile to verify no lifetime issues. | 0.5-1 day | Low |
| 72.2 | `NioReplicationTask.java:357` | Shared `DatagramChannel` - one per thread may be better | Benchmark single-shared vs. per-thread channel. If per-thread improves throughput, implement thread-local channels. | 1-2 days | Medium |

**Total estimated effort: 1.5-3 days, Low-Medium difficulty**

---

### 73. BaseModelMBean Cleanup (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 73.1 | `BaseModelMBean.java:68` | Catalina MBeans use weird inheritance; should be cleaned up | Audit the MBean class hierarchy. Consolidate common patterns. Remove unnecessary inheritance. | 3-5 days | High |
| 73.2 | `BaseModelMBean.java:135` | Some logic should be moved to `ManagedBean` | Identify the methods that belong in `ManagedBean` and move them. Update callers. | 1-2 days | Medium |

**Total estimated effort: 4-7 days, Medium-High difficulty**

---

### 74. Jasper JspC Filter Rules (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 74.1 | `JspCServletContext.java:208` | Filter rules from system properties instead of Ant config | Already covered in item 36.2. | -- | -- |

---

## Documentation TODOs

### 75. Tribes Documentation (7 files)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 75.1 | `docs/tribes/transport.xml:33` | Empty TODO placeholder | Write transport layer documentation: NIO vs BIO sender, UDP vs TCP, configuration options. | 2-3 days | Medium |
| 75.2 | `docs/tribes/status.xml:33` | Empty TODO placeholder | Document cluster status monitoring: MBeans, statistics, health checks. | 1-2 days | Medium |
| 75.3 | `docs/tribes/setup.xml:33` | Empty TODO placeholder | Write cluster setup guide: single-point vs multi-point, load balancer config, session replication. | 3-5 days | Medium |
| 75.4 | `docs/tribes/membership.xml:33` | Empty TODO placeholder | Document membership service: multicast, static members, dynamic discovery. | 1-2 days | Medium |
| 75.5 | `docs/tribes/interceptors.xml:33` | Empty TODO placeholder | Document all cluster interceptors: fault tolerance, flow control, message dispatch, etc. | 2-3 days | Medium |
| 75.6 | `docs/tribes/faq.xml:33` | Empty TODO placeholder | Compile common clustering questions and answers from mailing lists and JIRA. | 2-3 days | Medium |
| 75.7 | `docs/tribes/developers.xml:33` | Empty TODO placeholder | Developer guide for extending Tribes: custom channels, interceptors, senders. | 2-3 days | Medium |

**Total estimated effort: 13-21 days, Medium difficulty**

---

### 76. Cluster Interceptor Documentation (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 76.1 | `docs/config/cluster-interceptor.xml:315` | Not all interceptors documented | Document each built-in interceptor: `TcpFailureDetector`, `ValveFilter`, `MessageDispatch15`, `DeltaRequest`, `bridge`, `Noop`. | 2-3 days | Medium |

**Total estimated effort: 2-3 days, Medium difficulty**

---

### 77. Docs XSL Subsection Nesting (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 77.1 | `docs/tomcat-docs.xsl:234` | Nested subsection handling in XSLT | Fix the XSLT template to properly handle nested `<subsection>` elements with correct heading levels. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 78. Rewrite Docs Formatting (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 78.1 | `docs/rewrite.xml:530` | Pre-formatted text not wrapping | Investigate the XML/XSL rendering. Fix the `<source>` or `<pre>` element styling. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty**

---

## Test Code TODOs

### 79. HTTP/2 Test Coverage (8 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 79.1 | `TestHttp2Section_4_1.java:32` | Tests for over-sized frames missing | Add tests that send frames exceeding `MAX_FRAME_SIZE` and verify GOAWAY/RST_STREAM. | 1-2 days | Medium |
| 79.2 | `TestHttp2Section_4_1.java:46` | Tests for unexpected flags missing | Add tests for frames with invalid flag combinations. | 1-2 days | Medium |
| 79.3 | `TestHttp2Section_3_2.java:33` | Initial request body size tests missing | Add tests with various body sizes (0, small, large, exceeding initial window). | 1-2 days | Medium |
| 79.4 | `TestHttp2Section_6_9.java:67` | Flow control window change accounting tests missing | Add tests that verify window size updates are always accounted for, even for ignored frames. | 1-2 days | Medium |
| 79.5 | `TestHttp2Section_6_8.java:157` | PUSH_PROMISE header/window processing tests missing | Add tests for PUSH_PROMISE frames that verify headers and window size are processed even when stream is ignored. | 1-2 days | Medium |
| 79.6 | `TestHttp2Section_5_1.java:52` | Reserved local state tests missing | Add tests for sending frames when local state is RESERVED. | 0.5-1 day | Low |
| 79.7 | `TestHttp2Section_5_1.java:53` | Reserved remote state tests missing | Add tests for sending frames when remote state is RESERVED. | 0.5-1 day | Low |
| 79.8 | `TestHttp2Section_5_1.java:112` | Invalid frame tests for remaining states missing | Add invalid frame tests for HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, and CLOSED states. | 1-2 days | Medium |

**Total estimated effort: 6-12 days, Medium difficulty**

---

### 80. HPACK Test Improvement (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 80.1 | `TestHpack.java:39` | Huffman encoding not predictable in tests | Use `HpackHeaderFunction` to force huffman encoding for test headers, ensuring deterministic test results. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 81. OpenSSL Cipher Test Coverage (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 81.1 | `TestOpenSSLCipherConfigurationParser.java:497` | Individual operator tests missing | Add unit tests for each cipher string operator: `+`, `-`, `!`, `@`, colon separator, etc. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 82. OCSP Test Hardcoded Serials (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 82.1 | `TesterOcspResponderServlet.java:221` | Certificate serial numbers hardcoded instead of read from index.db | Parse the OpenSSL CA `index.txt` file to extract serial numbers dynamically. | 1 day | Medium |

**Total estimated effort: 1 day, Medium difficulty**

---

### 83. EL in JSP Escape Test (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 83.1 | `TestELInJsp.java:159` | Single unescaped backslash in attribute values is allowed | Fix the parser to reject unescaped backslashes, or document the behavior as intentional. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 84. WebSocket Client TCP ACK (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 84.1 | `TesterWsClient.java:47` | TCP ACK timing for RST detection is a hope, not a guarantee | Use `SO_LINGER` or a more reliable mechanism to ensure data is flushed before connection close. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 85. AJP Message Test Class (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 85.1 | `TesterAjpMessage.java:27` | Unclear if test utility should be merged into `AjpMessage` | Evaluate whether the additional read methods are general-purpose. If so, add to `AjpMessage` with appropriate access control. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 86. Tribes LoadTest Stubs (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 86.1 | `LoadTest.java:172` | `memberJoined` not implemented | Add logging or metrics collection for member join events. | 0.25 day | Low |
| 86.2 | `LoadTest.java:185` | `memberDisappeared` not implemented | Add logging or metrics collection for member leave events. | 0.25 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 87. Non-Blocking API Test (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 87.1 | `TestNonBlockingAPI.java:945` | Non-blocking writes with NIO connector appear to have issues | Investigate the specific failure mode. May be a connector bug or test timing issue. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

### 88. Annotation Test Type Inconsistency (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 88.1 | `TestContextConfigAnnotation.java:243` | `servletDef` is Boolean, `FilterDef` is String - inconsistent | Investigate why the types differ. If it's a bug, fix the annotation processing. If intentional, document. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

### 89. Example WebSocket Drawboard (5 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 89.1 | `DrawboardEndpoint.java:66` | Endpoint instance reuse not explored | Document the current single-use behavior. If multi-use is desired, make state non-static and thread-safe. | 1-2 days | Medium |
| 89.2 | `DrawboardEndpoint.java:201` | Variable-based solution is a workaround | Replace with a proper state management mechanism. | 0.5-1 day | Low |
| 89.3 | `DrawboardEndpoint.java:211` | Error handling - should connection be closed? | Add connection close on persistent errors. | 0.25 day | Low |
| 89.4 | `DrawboardEndpoint.java:214` | Same as 89.3 | Same fix. | 0.25 day | Low |
| 89.5 | `DrawMessage.java:31` | Color object creation for integer representation | Use a cached color map or accept the allocation cost. | 0.25 day | Low |

**Total estimated effort: 2.5-4.5 days, Low-Medium difficulty**

---

### 90. Example WebSocket Draw Message (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 90.1 | `DrawMessage.java:163` | Axis-aligned rectangles should be drawn as lines | Add a check: if `x1 == x2` or `y1 == y2`, draw a line instead of a rectangle. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty**

---

### 91. Example WebSocket Client Blocking (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 91.1 | `Client.java:109` | `close()` may block if remote doesn't read | Use async close with timeout. Or set `SO_TIMEOUT` before close. | 0.5-1 day | Low |
| 91.2 | `Client.java:206` | `session.close()` blocks in error handler | Move close to a separate thread or use async close. | 0.5-1 day | Low |

**Total estimated effort: 1-2 days, Low difficulty**

---

### 92. Example Async TODO (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 92.1 | `webapps/examples/jsp/async/index.jsp:58` | Incomplete TODO in example | Complete the async example documentation or feature. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty**

---

### 93. Manager Session Detail TODO (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 93.1 | `webapps/manager/WEB-INF/jsp/sessionDetail.jsp:140` | Max Inactive Interval not settable from session detail page | Add a form field and API call to update session max inactive interval. | 1-2 days | Medium |

**Total estimated effort: 1-2 days, Medium difficulty**

---

## Auto-Generated Stub TODOs

These are IDE-generated stub methods with `// TODO Auto-generated method stub` comments. They are low-priority but should eventually be properly implemented.

### 94. JDBC Pool Test Statement Interface (~80 stubs)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 94.1 | `modules/jdbc-pool/src/test/java/org/apache/tomcat/jdbc/test/driver/Statement.java:58-470+` | ~80 unimplemented `Statement` interface methods | Implement each method to delegate to the wrapped statement, or throw `SQLFeatureNotSupportedException`. This is test infrastructure code. | 3-5 days | Medium |

**Total estimated effort: 3-5 days, Medium difficulty**

---

### 95. CompressionResponseStream Stubs (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 95.1 | `CompressionResponseStream.java:274-288` | `isReady()` and `setWriteListener()` not implemented (Servlet 3.1 async) | Delegate to the underlying `ServletOutputStream`. Return `true` for `isReady()`. | 0.5 day | Low |

**Total estimated effort: 0.5 day, Low difficulty**

---

### 96. ByteArrayServletOutputStream Stubs (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 96.1 | `ByteArrayServletOutputStream.java:66-80` | `isReady()` and `setWriteListener()` not implemented (Servlet 3.1 async) | Return `true` for `isReady()` (in-memory buffer is always ready). No-op for `setWriteListener()`. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty**

---

### 97. ExpiresFilter Stubs (2 items)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 97.1 | `ExpiresFilter.java:1067-1078` | `isReady()` and `setWriteListener()` not implemented | Return `false` for `isReady()` (conservative). No-op for `setWriteListener()`. Or delegate to wrapped stream. | 0.25 day | Low |

**Total estimated effort: 0.25 day, Low difficulty**

---

### 98. PoolProperties Clone Stub (1 item)

| # | File:Line | Description | Fix Idea | Effort | Difficulty |
|---|-----------|-------------|----------|--------|------------|
| 98.1 | `PoolProperties.java:1216` | `clone()` uses super.clone() without deep copy | Implement proper deep clone of all mutable fields. | 0.5-1 day | Low |

**Total estimated effort: 0.5-1 day, Low difficulty**

---

## Summary Statistics

| Category | Items | Total Effort (days) | Avg Difficulty |
|----------|-------|---------------------|----------------|
| Critical FIXMEs | ~35 | ~75-120 | High |
| High-Priority TODOs | ~25 | ~50-80 | Medium-High |
| Medium-Priority TODOs | ~20 | ~25-40 | Medium |
| Low-Priority / Cosmetic | ~15 | ~25-45 | Low-Medium |
| Documentation | 11 | ~17-27 | Medium |
| Test Code | ~20 | ~15-25 | Low-Medium |
| Auto-Generated Stubs | ~90 | ~5-8 | Low |
| **Total** | **~196** | **~192-275** | **Mixed** |

### Priority Recommendations

1. **Immediate**: Fix DeltaManager session replication (items 1.1-1.7) - these affect cluster correctness
2. **Short-term**: Fix DBCP double-close (6.1-6.2), BaseModelMBean notification removal (7.1), PersistentManagerBase LRU (5.1)
3. **Medium-term**: SSL rewrite variables (2.1-2.8), HTTP/2 HPACK optimization (23.1-23.3), ThreadPoolExecutor AQS (13.1)
4. **Long-term**: WebXml i18n support (30.1), CGIServlet overhaul (63.1), Tribes documentation (75.1-75.7)
5. **Backlog**: Auto-generated stubs, cosmetic items, test improvements
