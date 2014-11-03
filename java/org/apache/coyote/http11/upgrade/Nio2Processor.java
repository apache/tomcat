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
package org.apache.coyote.http11.upgrade;

import java.nio.ByteBuffer;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.SocketWrapper;

public class Nio2Processor extends AbstractProcessor<Nio2Channel> {

    private static final Log log = LogFactory.getLog(Nio2Processor.class);
    @Override
    protected Log getLog() {return log;}

    private static final int INFINITE_TIMEOUT = -1;

    public Nio2Processor(AbstractEndpoint<Nio2Channel> endpoint,
            SocketWrapper<Nio2Channel> wrapper, ByteBuffer leftoverInput,
            HttpUpgradeHandler httpUpgradeProcessor,
            int asyncWriteBufferSize) {
        super(httpUpgradeProcessor,
                new Nio2ServletInputStream(wrapper, endpoint),
                new Nio2ServletOutputStream(wrapper, asyncWriteBufferSize, endpoint));

        wrapper.setTimeout(INFINITE_TIMEOUT);
        if (leftoverInput != null) {
            wrapper.getSocket().getBufHandler().getReadBuffer().put(leftoverInput);
        }
    }
}
