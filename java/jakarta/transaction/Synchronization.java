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
 * Callback interface for notification of transaction completion.
 * Registered with a transaction so that the implementing object is
 * notified when the transaction is about to complete and when it has
 * completed.
 */
public interface Synchronization {
    /**
     * Called before the transaction is committed or rolled back.
     * This method is invoked after all work on the transaction has
     * been completed but before the commit or rollback is performed.
     */
    void beforeCompletion();

    /**
     * Called after the transaction has completed.
     *
     * @param status the status of the transaction, one of the
     *               {@link Status} constants
     */
    void afterCompletion(int status);
}
