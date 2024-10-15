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

import java.io.IOException;

import org.apache.coyote.http2.Http2TestBase.TestOutput;

/**
 * Expose the parser outside of this package for use in other tests.
 */
public class TesterHttp2Parser extends Http2Parser {

    TesterHttp2Parser(String connectionId, Input input, TestOutput output) {
        super(connectionId, input, output);
    }


    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> The test implementation always uses blocking IO for both the initial read and the remainder.
     */
    @Override
    public boolean readFrame() throws Http2Exception, IOException {
        return super.readFrame();
    }
}
