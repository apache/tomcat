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
package org.apache.catalina.valves;

import java.io.CharArrayWriter;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Request;

public class TestExtendedAccessLogValveElements {

    /*
     * A valve attached to a Host or Engine also logs requests that did not map to any context, for which
     * Request.getContext() is null. The servlet context attribute element must use the standard placeholder rather
     * than throwing, since an exception would cause the entire log entry to be dropped.
     */
    @Test
    public void testServletContextElementWithoutMappedContext() {
        ExtendedAccessLogValve.ServletContextElement element = new ExtendedAccessLogValve.ServletContextElement(
                "attributeName");
        Request request = new Request(null, new org.apache.coyote.Request());
        Assert.assertNull(request.getContext());

        CharArrayWriter buf = new CharArrayWriter();
        element.addElement(buf, request, null, 0);

        Assert.assertEquals("-", buf.toString());
    }
}
