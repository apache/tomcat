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
package org.apache.jasper.tagplugins.jstl.core;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk;

public class TestWhen extends AbstractTestTag {

    /*
     * A <c:when> whose direct parent is not a <c:choose> (but is another
     * plugin-enabled tag) must not generate an unbalanced if statement, which
     * would fail the translation of the whole page. The plugin must step
     * aside so that the standard tag handler reports the illegal nesting at
     * runtime, as the reference implementation does. The TLD validator does
     * not reject this nesting.
     */
    @Test
    public void testOutsideChoose() throws Exception {
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jstl/when-outside-choose.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertTrue(body, body.contains("JspTagException"));
        Assert.assertTrue(body, body.contains("style tag without"));
        Assert.assertTrue(body, body.contains("as its direct parent"));
    }
}
