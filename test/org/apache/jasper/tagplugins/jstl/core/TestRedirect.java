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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.SimpleTagSupport;
import jakarta.servlet.jsp.tagext.TagSupport;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk;

public class TestRedirect extends AbstractTestTag {

    private static volatile CountDownLatch pageStarted;
    private static volatile CountDownLatch pageProcessingContinued;


    public static class InvokeBodyTag extends SimpleTagSupport {

        @Override
        public void doTag() throws JspException, IOException {
            getJspBody().invoke(null);
        }
    }


    public static class InvokeBodyClassicTag extends TagSupport {

        private static final long serialVersionUID = 1L;

        @Override
        public int doStartTag() {
            return EVAL_BODY_INCLUDE;
        }
    }


    public static void markPageStarted() {
        pageStarted.countDown();
    }


    public static void markPageProcessingContinued() {
        pageProcessingContinued.countDown();
    }


    @Override
    protected File getTagPluginsFile() {
        return new File("test/org/apache/jasper/tagplugins/jstl/core/redirect-tagPlugins.xml");
    }


    @Test
    public void testRedirectSkipsPage() throws Exception {
        doTestRedirectSkipsPage("redirect.jsp");
    }


    @Test
    public void testRedirectSkipsPageFromTagFile() throws Exception {
        doTestRedirectSkipsPage("redirect-tag-file.jsp");
    }


    @Test
    public void testRedirectSkipsPageFromFragment() throws Exception {
        doTestRedirectSkipsPage("redirect-fragment.jsp");
    }


    @Test
    public void testRedirectSkipsPageFromNestedMethod() throws Exception {
        doTestRedirectSkipsPage("redirect-nested.jsp");
    }


    private void doTestRedirectSkipsPage(String jsp) throws Exception {
        pageStarted = new CountDownLatch(1);
        pageProcessingContinued = new CountDownLatch(1);
        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/jstl/" + jsp, res, false);
        Assert.assertEquals(HttpServletResponse.SC_FOUND, rc);
        Assert.assertTrue(pageStarted.await(1, TimeUnit.SECONDS));
        Assert.assertFalse(pageProcessingContinued.await(1, TimeUnit.SECONDS));
    }
}
