/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * <p>
 * This package provides support for pooling of ManagedConnections. A managed
 * connection is responsible for managing a database connection in a
 * transactional environment (typically called <i>Container Managed</i>).
 * A managed connection operates like any other connection when no global
 * transaction (a.k.a. XA transaction or JTA Transaction) is in progress.
 * When a global transaction is active a single physical connection to the
 * database is used by all ManagedConnections accessed in the scope of the
 * transaction. Connection sharing means that all data access during a
 * transaction has a consistent view of the database. When the global
 * transaction is committed or rolled back the enlisted connections are
 * committed or rolled back.
 * </p>

 * <p>
 * This package supports full XADataSources and non-XA data sources using
 * local transaction semantics. non-XA data sources commit and rollback as
 * part of the transaction but are not recoverable in the case of an error
 * because they do not implement the two-phase commit protocol.
 * </p>
 */
package org.apache.tomcat.dbcp.dbcp2.managed;
