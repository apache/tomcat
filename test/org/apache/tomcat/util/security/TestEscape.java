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
package org.apache.tomcat.util.security;

import org.junit.Assert;
import org.junit.Test;

public class TestEscape {

    @Test
    public void testHtmlContent() {
        Assert.assertEquals("&lt;&gt;&amp;&#39;&quot;&#47;", Escape.htmlElementContent("<>&'\"/"));
    }


    @Test
    public void testHtmlContentNullString() {
        Assert.assertEquals(null, Escape.htmlElementContent((String) null));
    }


    @Test
    public void testHtmlContentNullObject() {
        Assert.assertEquals("?", Escape.htmlElementContent((Object) null));
    }


    @Test
    public void testHtmlContentObject() {
        StringBuilder sb = new StringBuilder("test");
        Assert.assertEquals("test", Escape.htmlElementContent(sb));
    }


    @Test
    public void testHtmlContentObjectException() {
        StringBuilder sb = new StringBuilder("test");
        Assert.assertEquals("test", Escape.htmlElementContent(sb));
    }


}
