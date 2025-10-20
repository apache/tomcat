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
package org.apache.catalina.mapper;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.MessageBytes;

/*
 * This is an absolute performance test with an upper limit. Therefore it is executed as part of a standard test run.
 */
public class TestMapperPerformance extends TestMapper {

    @Test
    public void testPerformance() throws Exception {
        String[] requestedHostNames = new String[] { "xxxxxxxxxxx", "iowejoiejfoiew", "iowejoiejfoiex", "owefojiwefoi",
                "owefojiwefoix", "qwerty.net", "foo.net", "zzz.com", "abc.com" };

        for (String requestedHostName : requestedHostNames) {
            testPerformance(requestedHostName);
        }
    }

    private void testPerformance(String requestedHostName) throws Exception {
        // Takes ~1s on markt's laptop. If this takes more than 5s something
        // probably needs looking at. If this fails repeatedly then we may need
        // to increase this limit.
        final long maxTime = 5000;
        long time = testPerformanceImpl(requestedHostName);
        log.info("Host [" + requestedHostName + "], Time [" + time + "]ms");
        if (time >= maxTime) {
            // Rerun to reject occasional failures, e.g. because of gc
            log.warn("testPerformance() test completed in " + time + " ms");
            time = testPerformanceImpl(requestedHostName);
            log.warn("testPerformance() test rerun completed in " + time + " ms");
        }
        Assert.assertTrue(String.valueOf(time), time < maxTime);
    }

    private long testPerformanceImpl(String requestedHostName) throws Exception {
        MappingData mappingData = new MappingData();
        MessageBytes host = MessageBytes.newInstance();
        host.setString(requestedHostName);
        MessageBytes uri = MessageBytes.newInstance();
        uri.setString("/foo/bar/blah/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            mappingData.recycle();
            mapper.map(host, uri, null, mappingData);
        }
        long time = System.currentTimeMillis() - start;
        return time;
    }

}
