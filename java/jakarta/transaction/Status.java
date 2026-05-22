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
 * Defines the set of status constants for a transaction.
 */
public interface Status {
    /**
     * The transaction is active and work is in progress.
     */
    int STATUS_ACTIVE = 0;
    /**
     * The transaction has been marked for rollback only.
     */
    int STATUS_MARKED_ROLLBACK = 1;
    /**
     * The transaction resources have been prepared and are awaiting completion.
     */
    int STATUS_PREPARED = 2;
    /**
     * The transaction has been committed successfully.
     */
    int STATUS_COMMITTED = 3;
    /**
     * The transaction has been rolled back.
     */
    int STATUS_ROLLEDBACK = 4;
    /**
     * The transaction status is unknown.
     */
    int STATUS_UNKNOWN = 5;
    /**
     * There is no active transaction for the current thread.
     */
    int STATUS_NO_TRANSACTION = 6;
    /**
     * The transaction manager is in the process of preparing the transaction resources.
     */
    int STATUS_PREPARING = 7;
    /**
     * The transaction manager is in the process of committing the transaction.
     */
    int STATUS_COMMITTING = 8;
    /**
     * The transaction manager is in the process of rolling back the transaction.
     */
    int STATUS_ROLLING_BACK = 9;
}
