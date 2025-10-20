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
package jakarta.servlet.jsp.el;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestImportELResolver extends TomcatBaseTest {

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=66441
    @Test
    public void testImportStaticFields() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug6nnnn/bug66441.jsp");

        String result = res.toString();

        Assert.assertTrue(result.contains("EL  - Long min value is -9223372036854775808"));
        Assert.assertTrue(result.contains("JSP - Long min value is -9223372036854775808"));
    }


    // https://bz.apache.org/bugzilla/show_bug.cgi?id=66582
    @Test
    public void testImportStaticFieldFromInterface() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/bug6nnnn/bug66582.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String result = res.toString();
        Assert.assertFalse(result, result.contains("data"));
    }
}
