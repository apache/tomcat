/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jni;

import org.junit.Test;

/*
 * Helper class to investigate native memory leaks. Needs to be used with tools
 * to monitor native memory usage.
 *
 * Note: Moving the Pool, SSLContext, SSL and BIO creation in/out of the loop
 *       can help identify where the memory is leaking.
 */
public class TesterSSL {

    @Test
    public void testCreateDestroy() throws Exception {
        Library.initialize(null);
        SSL.initialize(null);

        long memoryPool = Pool.create(0);
        long sslCtx = SSLContext.make(memoryPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);

        for (int i = 0; i < 10000000; i++) {
            doNative(sslCtx);
            if (i % 1000 == 0) {
                System.gc();
            }
        }

        SSLContext.free(sslCtx);
        Pool.destroy(memoryPool);

        System.gc();
    }


    private void doNative(long sslCtx) throws Exception {
        long ssl = SSL.newSSL(sslCtx, true);
        long bio = SSL.makeNetworkBIO(ssl);
        SSL.freeBIO(bio);
        SSL.freeSSL(ssl);
    }
}
