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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.descriptor.JspPropertyGroupDescriptor;
import jakarta.servlet.descriptor.TaglibDescriptor;

import org.junit.Assert;
import org.junit.Test;

public class TestJspConfigDescriptorImpl {

    @Test
    public void testTaglibsAreIsolate() {
        List<TaglibDescriptor> taglibs = new ArrayList<>();
        taglibs.add(new TaglibDescriptorImpl("location", "uri"));
        List<JspPropertyGroupDescriptor> propertyGroups = Collections.emptyList();
        JspConfigDescriptor descriptor = new JspConfigDescriptorImpl(propertyGroups, taglibs);
        descriptor.getTaglibs().clear();
        Assert.assertEquals(taglibs, descriptor.getTaglibs());
    }

    @Test
    public void testPropertyGroupsAreIsolate() {
        List<TaglibDescriptor> taglibs = Collections.emptyList();
        List<JspPropertyGroupDescriptor> propertyGroups = new ArrayList<>();
        propertyGroups.add(new JspPropertyGroupDescriptorImpl(new JspPropertyGroup()));
        JspConfigDescriptor descriptor = new JspConfigDescriptorImpl(propertyGroups, taglibs);
        descriptor.getJspPropertyGroups().clear();
        Assert.assertEquals(propertyGroups, descriptor.getJspPropertyGroups());
    }
}
