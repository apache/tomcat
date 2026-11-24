/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.jdbc.pool;

import org.junit.jupiter.api.Nested;

/**
 * Thematic grouping of the PoolLifecycle tests.
 * <p>Each nested class extends the former standalone test class, so all
 * test methods, assertions and fixtures are preserved.</p>
 */
class TestPoolLifecycle {

    @Nested
    class PoolEviction extends PoolEvictionBase {
    }

    @Nested
    class Purge extends PurgeBase {
    }

    @Nested
    class PoolInitializationCleanup extends PoolInitializationCleanupBase {
    }

    @Nested
    class CloseDuringInit extends CloseDuringInitBase {
    }

    @Nested
    class DataSourceProxyPublicationCleanup extends DataSourceProxyPublicationCleanupBase {
    }

    @Nested
    class SharedCleanerTimer extends SharedCleanerTimerBase {
    }

    @Nested
    class DuplicatePoolCleaner extends DuplicatePoolCleanerBase {
    }

    @Nested
    class FailedCleanerSchedule extends FailedCleanerScheduleBase {
    }

    @Nested
    class PoolSweeperSetters extends PoolSweeperSettersBase {
    }

    @Nested
    class UnlockedMaintenanceSweep extends UnlockedMaintenanceSweepBase {
    }

    @Nested
    class AllIdleClaim extends AllIdleClaimBase {
    }

    @Nested
    class IdleValidationHeadOfLine extends IdleValidationHeadOfLineBase {
    }

    @Nested
    class UnconnectedPlaceholder extends UnconnectedPlaceholderBase {
    }

    @Nested
    class InitialSizeBelowZero extends InitialSizeBelowZeroBase {
    }

    @Nested
    class PoolSizeSetterValidation extends PoolSizeSetterValidationBase {
    }

    @Nested
    class LoginTimeoutMapping extends LoginTimeoutMappingBase {
    }

    @Nested
    class ConcurrentClose extends ConcurrentCloseBase {
    }

}