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
package org.apache.jasper.servlet;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.jsp.JspFactory;

import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.Constants;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.tomcat.unittest.TesterServletContext;

public class TestJasperInitializer {

    @Test
    public void testPoolSize() throws Exception {

        final AtomicInteger actualPoolSize = new AtomicInteger(-1);
        final JspFactoryImpl defaultFactory = new JspFactoryImpl() {
            @Override
            public void setPoolSize(int poolSize) {
                actualPoolSize.set(poolSize);
                super.setPoolSize(poolSize);
            }
        };

        JspFactory.setDefaultFactory(defaultFactory);
        new JasperInitializer().onStartup(Collections.emptySet(), new TesterServletContext(){
            @Override
            public void setAttribute(String name, Object object) {
                // ignore
            }

            @Override
            public JspConfigDescriptor getJspConfigDescriptor() {
                return null;
            }

            @Override
            public String getInitParameter(String name) {
                if (Constants.JSP_FACTORY_POOL_SIZE_INIT_PARAM.equals(name)) {
                    return "3";
                }
                return super.getInitParameter(name);
            }
        });

        // Default value is 8 in JasperInitializer but we have overridden it
        Assert.assertEquals(3, actualPoolSize.get());
    }

}
