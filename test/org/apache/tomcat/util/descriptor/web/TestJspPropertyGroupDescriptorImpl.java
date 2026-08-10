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
package org.apache.tomcat.util.descriptor.web;

import org.junit.Assert;
import org.junit.Test;

public class TestJspPropertyGroupDescriptorImpl {

    @Test
    public void testPreludesAreIsolated() {
        JspPropertyGroup jpg = new JspPropertyGroup();
        jpg.addIncludePrelude("prelude");
        JspPropertyGroupDescriptorImpl descriptor = new JspPropertyGroupDescriptorImpl(jpg);
        descriptor.getIncludePreludes().clear();
        Assert.assertEquals(1, descriptor.getIncludePreludes().size());
    }

    @Test
    public void testCodasAreIsolated() {
        JspPropertyGroup jpg = new JspPropertyGroup();
        jpg.addIncludeCoda("coda");
        JspPropertyGroupDescriptorImpl descriptor = new JspPropertyGroupDescriptorImpl(jpg);
        descriptor.getIncludeCodas().clear();
        Assert.assertEquals(1, descriptor.getIncludeCodas().size());
    }

    @Test
    public void testUrlPatternsAreIsolated() {
        JspPropertyGroup jpg = new JspPropertyGroup();
        jpg.addUrlPattern("pattern");
        JspPropertyGroupDescriptorImpl descriptor = new JspPropertyGroupDescriptorImpl(jpg);
        descriptor.getUrlPatterns().clear();
        Assert.assertEquals(1, descriptor.getUrlPatterns().size());
    }
}
