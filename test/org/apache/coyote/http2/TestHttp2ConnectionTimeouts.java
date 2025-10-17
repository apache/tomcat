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
package org.apache.coyote.http2;

import org.junit.Test;

public class TestHttp2ConnectionTimeouts extends Http2TestBase {

    @Test
    public void testConnectionTimeout() throws Exception {

        // Reduce default timeouts so test completes sooner
        enableHttp2(200, false, 5000, 5000, 10000, 5000, 5000);
        configureAndStartWebApplication();
        openClientConnection(false);
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Wait for timeout - should receive GoAway frame
        handleGoAwayResponse(1, Http2Error.NO_ERROR);
    }
}
