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
package org.apache.catalina.core;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestApplicationFilterRegistration {

    @Test
    public void testUrlPatternEncoded() {
        doTestUrlPattern(false, "/filter%");
    }

    @Test
    public void testUrlPatternDecoded() {
        doTestUrlPattern(true, "/filter%25");
    }

    private void doTestUrlPattern(boolean urlPatternsProvidedInDecodedForm, String expectedPattern) {
        StandardContext context = new StandardContext();
        context.setUrlPatternsProvidedInDecodedForm(urlPatternsProvidedInDecodedForm);

        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName("filter");
        context.addFilterDef(filterDef);

        ApplicationFilterRegistration registration = new ApplicationFilterRegistration(filterDef, context);
        registration.addMappingForUrlPatterns(null, true, "/filter%25");

        FilterMap[] filterMaps = context.findFilterMaps();
        Assert.assertEquals(1, filterMaps.length);
        Assert.assertArrayEquals(new String[] { expectedPattern }, filterMaps[0].getURLPatterns());
    }
}
