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
package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;

public class SNIExtractor {

    private final SNIResult result = SNIResult.NOT_PRESENT;
    private final String sniValue = null;

    public SNIExtractor(ByteBuffer netInBuffer) {
        // TODO: Detect use of http on a secure connection and provide a simple
        //       error page.

        int pos = netInBuffer.position();
        try {
            // TODO Parse the data

        } finally {
            // Whatever happens, return the buffer to its original state
            netInBuffer.limit(netInBuffer.capacity());
            netInBuffer.position(pos);
        }
    }

    public SNIResult getResult() {
        return result;
    }

    public String getSNIValue() {
        if (result == SNIResult.FOUND) {
            return sniValue;
        } else {
            throw new IllegalStateException();
        }
    }

    public static enum SNIResult {
        FOUND,
        NOT_PRESENT,
        UNDERFLOW,
        ERROR
    }
}
