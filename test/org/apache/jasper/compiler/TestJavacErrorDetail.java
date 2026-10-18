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
package org.apache.jasper.compiler;

import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.JspCompilationContext;
import org.easymock.EasyMock;

public class TestJavacErrorDetail {

    /*
     * If the JSP source is no longer available when the error detail is
     * constructed, such as when the JSP is removed between translation and
     * compilation, the error detail must fall back to reporting only the
     * error in the generated Java source, rather than throwing a
     * NullPointerException.
     */
    @Test
    public void testMissingJspSource() throws Exception {
        JspCompilationContext ctxt = EasyMock.createMock(
                JspCompilationContext.class);
        EasyMock.expect(ctxt.getTagFileJar()).andReturn(null).anyTimes();
        EasyMock.expect(ctxt.getResourceAsStream("/a.jsp")).andReturn(null)
                .anyTimes();
        EasyMock.replay(ctxt);

        JavacErrorDetail detail = new JavacErrorDetail("a_jsp.java", 10,
                "/a.jsp", 5, new StringBuilder("error message"), ctxt);

        // A negative JSP line number signals that no JSP line is known, in
        // which case only the Java source error details are reported
        Assert.assertEquals(-1, detail.getJspBeginLineNumber());

        EasyMock.verify(ctxt);
    }
}
