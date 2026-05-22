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
 * Provides methods for managing transactions.
 * Used by the transaction manager to begin, commit, and roll back
 * transactions, and to suspend and resume them.
 */
public interface TransactionManager {
    /**
     * Begins a new transaction for the current thread.
     *
     * @throws NotSupportedException if the transaction manager does not
     *         support transactions
     * @throws SystemException if a system error occurred
     */
    void begin() throws NotSupportedException, SystemException;

    /**
     * Commits the transaction associated with the current thread.
     *
     * @throws RollbackException if the transaction has been marked for rollback only
     * @throws HeuristicMixedException if the transaction was heuristically
     *         partitioned
     * @throws HeuristicRollbackException if the transaction was heuristically rolled back
     * @throws SecurityException if the caller is not authorized to commit
     * @throws IllegalStateException if no transaction is active
     * @throws SystemException if a system error occurred
     */
    void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException,
            IllegalStateException, SystemException;

    /**
     * Returns the status of the transaction associated with the current thread.
     *
     * @return the transaction status, one of the {@link Status} constants
     * @throws SystemException if a system error occurred
     */
    int getStatus() throws SystemException;

    /**
     * Returns the {@link Transaction} object associated with the current thread.
     *
     * @return the current transaction, or {@code null} if there is no active transaction
     * @throws SystemException if a system error occurred
     */
    Transaction getTransaction() throws SystemException;

    /**
     * Resumes a previously suspended transaction for the current thread.
     *
     * @param tobj the transaction to resume
     * @throws InvalidTransactionException if the transaction is no longer valid
     * @throws IllegalStateException if there is already an active transaction
     * @throws SystemException if a system error occurred
     */
    void resume(Transaction tobj) throws InvalidTransactionException, IllegalStateException, SystemException;

    /**
     * Rolls back the transaction associated with the current thread.
     *
     * @throws IllegalStateException if no transaction is active
     * @throws SecurityException if the caller is not authorized to roll back
     * @throws SystemException if a system error occurred
     */
    void rollback() throws IllegalStateException, SecurityException, SystemException;

    /**
     * Marks the current transaction so that the only valid outcome is a rollback.
     *
     * @throws IllegalStateException if no transaction is active
     * @throws SystemException if a system error occurred
     */
    void setRollbackOnly() throws IllegalStateException, SystemException;

    /**
     * Sets the default transaction timeout.
     *
     * @param seconds the timeout value in seconds
     * @throws SystemException if a system error occurred
     */
    void setTransactionTimeout(int seconds) throws SystemException;

    /**
     * Suspends the transaction associated with the current thread.
     *
     * @return the suspended transaction, or {@code null} if there was no active transaction
     * @throws SystemException if a system error occurred
     */
    Transaction suspend() throws SystemException;
}
