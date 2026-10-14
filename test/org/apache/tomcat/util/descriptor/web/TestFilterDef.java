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
package org.apache.tomcat.util.descriptor.web;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test case for {@link FilterDef}.
 */
public class TestFilterDef {

    @Test(expected = IllegalArgumentException.class)
    public void testSetFilterNameNull() {
        new FilterDef().setFilterName(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetFilterNameEmptyString() {
        new FilterDef().setFilterName("");
    }

    @Test
    public void testSetFilterName() {
        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName("test");
        Assert.assertEquals("'test' is expected as filter name",
                "test", filterDef.getFilterName());
    }

    @Test
    public void testAsyncSupportedDefaultsToFalse() {
        // The specification default is that a filter does not support async
        // processing and every other async-supported default in the container
        // is false. This matters in particular for filters registered
        // dynamically via ServletContext.addFilter() which do not pass through
        // the ContextConfig normalisation of the descriptor value.
        FilterDef filterDef = new FilterDef();
        Assert.assertNull(filterDef.getAsyncSupported());
        Assert.assertFalse(filterDef.getAsyncSupportedBoolean());
    }

    @Test
    public void testSetAsyncSupported() {
        FilterDef filterDef = new FilterDef();

        filterDef.setAsyncSupported("true");
        Assert.assertTrue(filterDef.getAsyncSupportedBoolean());

        filterDef.setAsyncSupported("TRUE");
        Assert.assertTrue(filterDef.getAsyncSupportedBoolean());

        filterDef.setAsyncSupported("false");
        Assert.assertFalse(filterDef.getAsyncSupportedBoolean());

        // Only "true" enables async support. Anything else, including null
        // and invalid values such as "yes", is not supported which matches
        // the handling by ServletDef and by Boolean. The string value is
        // stored as-is since WebXml.mergeFilter() and the ContextConfig
        // normalisation of the descriptor value operate on it.
        filterDef.setAsyncSupported("yes");
        Assert.assertEquals("yes", filterDef.getAsyncSupported());
        Assert.assertFalse(filterDef.getAsyncSupportedBoolean());

        filterDef.setAsyncSupported(null);
        Assert.assertFalse(filterDef.getAsyncSupportedBoolean());
    }

}
