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

package org.apache.tomcat.lite.util;

import junit.framework.TestCase;

public class UEncoderTest extends TestCase {
    URLEncoder enc=new URLEncoder();

    /*
     *
     * Test method for 'org.apache.tomcat.util.buf.UEncoder.encodeURL(String)'
     * TODO: find the relevant rfc and apache tests and add more
     */
    public void testEncodeURL() {

        String eurl1=enc.encodeURL("test");
        assertEquals("test", eurl1);

        eurl1=enc.encodeURL("/test");
        assertEquals("/test", eurl1);

        // safe ranges
        eurl1=enc.encodeURL("test$-_.");
        assertEquals("test$-_.", eurl1);

        eurl1=enc.encodeURL("test$-_.!*'(),");
        assertEquals("test$-_.!*'(),", eurl1);

        eurl1=enc.encodeURL("//test");
        assertEquals("//test", eurl1);


    }

}
