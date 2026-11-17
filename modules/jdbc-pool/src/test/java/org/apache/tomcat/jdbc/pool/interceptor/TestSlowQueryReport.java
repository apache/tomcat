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
 * Thematic grouping of the SlowQueryReport tests.
 * <p>Each nested class extends the former standalone test class, so all
 * test methods, assertions and fixtures are preserved.</p>
 */
class TestSlowQueryReport {

    @Nested
    class SlowQueryBookingTimestamp extends SlowQueryBookingTimestampBase {
    }

    @Nested
    class SlowQueryComparator extends SlowQueryComparatorBase {
    }

    @Nested
    class SlowQueryEviction extends SlowQueryEvictionBase {
    }

    @Nested
    class SlowQueryJmxChainCloseRelease extends SlowQueryJmxChainCloseReleaseBase {
    }

    @Nested
    class SlowQueryJmxMBeanSurface extends SlowQueryJmxMBeanSurfaceBase {
    }

    @Nested
    class SlowQueryJmxNotifications extends SlowQueryJmxNotificationsBase {
    }

    @Nested
    class SlowQueryJmxSlowQueriesCDRace extends SlowQueryJmxSlowQueriesCDRaceBase {
    }

    @Nested
    class SlowQueryReportAbandonedPool extends SlowQueryReportAbandonedPoolBase {
    }

    @Nested
    class SlowQueryReportDeadSessionWarning extends SlowQueryReportDeadSessionWarningBase {
    }

    @Nested
    class SlowQueryReportLogForgery extends SlowQueryReportLogForgeryBase {
    }

    @Nested
    class SlowQueryReportLogging extends SlowQueryReportLoggingBase {
    }

    @Nested
    class SlowQueryReportNamelessPool extends SlowQueryReportNamelessPoolBase {
    }

    @Nested
    class SlowQueryReportPoolNames extends SlowQueryReportPoolNamesBase {
    }

    @Nested
    class SlowQueryReportPoolRename extends SlowQueryReportPoolRenameBase {
    }

    @Nested
    class SlowQueryReportProperties extends SlowQueryReportPropertiesBase {
    }

    @Nested
    class SlowQueryReportSharedStatistics extends SlowQueryReportSharedStatisticsBase {
    }

    @Nested
    class SlowQueryReportSlowLogAfterChainRelease extends SlowQueryReportSlowLogAfterChainReleaseBase {
    }

}