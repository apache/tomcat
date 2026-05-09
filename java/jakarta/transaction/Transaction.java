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

import javax.transaction.xa.XAResource;

/**
 * Represents an active transaction.
 * Provides methods for committing, rolling back, and managing
 * resources enlisted in the transaction.
 */
public interface Transaction {

    /**
     * Commits the transaction.
     *
     * @throws RollbackException if the transaction has been marked for rollback only
     * @throws HeuristicMixedException if the transaction was heuristically
     *         partitioned, with some resources committed and others rolled back
     * @throws HeuristicRollbackException if the transaction was heuristically rolled back
     * @throws SecurityException if the caller is not authorized to commit
     * @throws IllegalStateException if the transaction is not active
     * @throws SystemException if a system error occurred
     */
    void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException,
            IllegalStateException, SystemException;

    /**
     * Delists the specified XA resource from this transaction.
     *
     * @param xaRes the XA resource to delist
     * @param flag one of {@code TMSUCCESS}, {@code TMFAIL}, or {@code TMSTARTEPOCH}
     * @return {@code true} if the resource was delisted, {@code false} otherwise
     * @throws IllegalStateException if the transaction is not active
     * @throws SystemException if a system error occurred
     */
    boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException, SystemException;

    /**
     * Enlists the specified XA resource with this transaction.
     *
     * @param xaRes the XA resource to enlist
     *
     * @return {@code true} if the resource was enlisted, {@code false} otherwise
     *
     * @throws RollbackException if the transaction has been marked for rollback only
     * @throws IllegalStateException if the transaction is not active or the
     *         resource is already enlisted
     * @throws SystemException if a system error occurred
     */
    boolean enlistResource(XAResource xaRes) throws RollbackException, IllegalStateException, SystemException;

    /**
     * Returns the status of this transaction.
     *
     * @return the transaction status, one of the {@link Status} constants
     * @throws SystemException if a system error occurred
     */
    int getStatus() throws SystemException;

    /**
     * Registers a synchronization callback with this transaction.
     *
     * @param sync the synchronization callback to register
     * @throws RollbackException if the transaction has been marked for rollback only
     * @throws IllegalStateException if the transaction is not active
     * @throws SystemException if a system error occurred
     */
    void registerSynchronization(Synchronization sync) throws RollbackException, IllegalStateException, SystemException;

    /**
     * Rolls back the transaction.
     *
     * @throws IllegalStateException if the transaction is not active
     * @throws SystemException if a system error occurred
     */
    void rollback() throws IllegalStateException, SystemException;

    /**
     * Marks this transaction so that the only valid outcome is a rollback.
     *
     * @throws IllegalStateException if the transaction is not active
     * @throws SystemException if a system error occurred
     */
    void setRollbackOnly() throws IllegalStateException, SystemException;

}
