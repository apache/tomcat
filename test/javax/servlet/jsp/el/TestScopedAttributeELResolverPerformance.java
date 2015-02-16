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
package javax.servlet.jsp.el;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ELResolver;
import javax.el.StandardELContext;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.TesterPageContext;

import org.junit.Test;

public class TestScopedAttributeELResolverPerformance {

    /*
     * With the caching of NotFound responses this test takes ~20ms. Without the
     * caching it takes ~6s.
     */
    @Test
    public void testGetValuePerformance() throws Exception {

        ELContext context = new StandardELContext(ELManager.getExpressionFactory());

        context.putContext(JspContext.class, new TesterPageContext());

        ELResolver resolver = new ScopedAttributeELResolver();

        for (int i = 0; i < 100000; i++) {
            resolver.getValue(context, null, "unknown");
        }
    }
}
