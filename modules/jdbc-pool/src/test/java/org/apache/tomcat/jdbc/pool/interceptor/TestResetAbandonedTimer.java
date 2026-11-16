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

package org.apache.tomcat.jdbc.pool.interceptor;

import org.junit.jupiter.api.Nested;

/**
 * Thematic grouping of the ResetAbandonedTimer tests.
 * <p>Each nested class extends the former standalone test class, so all
 * test methods, assertions and fixtures are preserved.</p>
 */
class TestResetAbandonedTimer {

    @Nested
    class ResetAbandonedTimer extends ResetAbandonedTimerBase {
    }

    @Nested
    class ResetAbandonedTimerCursorOutParameter extends ResetAbandonedTimerCursorOutParameterBase {
    }

    @Nested
    class ResetAbandonedTimerEpochStampRace extends ResetAbandonedTimerEpochStampRaceBase {
    }

    @Nested
    class ResetAbandonedTimerFailedSetupDiscard extends ResetAbandonedTimerFailedSetupDiscardBase {
    }

    @Nested
    class ResetAbandonedTimerHandleRetention extends ResetAbandonedTimerHandleRetentionBase {
    }

    @Nested
    class ResetAbandonedTimerMBeanIdle extends ResetAbandonedTimerMBeanIdleBase {
    }

    @Nested
    class ResetAbandonedTimerNestedCursor extends ResetAbandonedTimerNestedCursorBase {
    }

    @Nested
    class ResetAbandonedTimerParkedCallStamp extends ResetAbandonedTimerParkedCallStampBase {
    }

    @Nested
    class ResetAbandonedTimerStreaming extends ResetAbandonedTimerStreamingBase {
    }

    @Nested
    class ResetAbandonedTimerSuspectReturnReport extends ResetAbandonedTimerSuspectReturnReportBase {
    }

}