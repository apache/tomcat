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

import org.apache.catalina.Wrapper;

public class TestApplicationServletRegistration {

    @Test
    public void testUrlPatternEncoded() {
        doTestUrlPattern(false, "/servlet%");
    }

    @Test
    public void testUrlPatternDecoded() {
        doTestUrlPattern(true, "/servlet%25");
    }

    private void doTestUrlPattern(boolean urlPatternsProvidedInDecodedForm, String expectedPattern) {
        StandardContext context = new StandardContext();
        context.setUrlPatternsProvidedInDecodedForm(urlPatternsProvidedInDecodedForm);

        Wrapper wrapper = context.createWrapper();
        wrapper.setName("servlet");
        context.addChild(wrapper);

        ApplicationServletRegistration registration = new ApplicationServletRegistration(wrapper, context);
        Assert.assertTrue(registration.addMapping("/servlet%25").isEmpty());
        Assert.assertEquals("servlet", context.findServletMapping(expectedPattern));
    }
}
