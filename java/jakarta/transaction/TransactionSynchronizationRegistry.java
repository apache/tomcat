/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.transaction;

/**
 * Provides a resource and transaction registry for resource managers.
 * Allows resource managers to associate resources with the current
 * transaction and register synchronization callbacks.
 */
public interface TransactionSynchronizationRegistry {
    /**
     * Returns the unique key identifying the current transaction.
     *
     * @return the transaction key, or {@code null} if there is no active transaction
     */
    Object getTransactionKey();

    /**
     * Associates a resource with the given key in the current transaction context.
     *
     * @param key the key to associate with the resource
     * @param value the resource object to associate
     */
    void putResource(Object key, Object value);

    /**
     * Returns the resource associated with the given key in the current transaction context.
     *
     * @param key the key of the resource to retrieve
     * @return the associated resource, or {@code null} if no resource is found
     */
    Object getResource(Object key);

    /**
     * Registers an interposed synchronization callback with the current transaction.
     *
     * @param sync the synchronization callback to register
     */
    void registerInterposedSynchronization(Synchronization sync);

    /**
     * Returns the status of the current transaction.
     *
     * @return the transaction status, one of the {@link Status} constants
     */
    int getTransactionStatus();

    /**
     * Marks the current transaction so that the only valid outcome is a rollback.
     */
    void setRollbackOnly();

    /**
     * Returns whether the current transaction has been marked for rollback only.
     *
     * @return {@code true} if the transaction is marked for rollback only
     */
    boolean getRollbackOnly();
}
