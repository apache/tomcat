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
package org.apache.jasper.runtime;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.Tag;

import org.junit.Test;

import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterThreadedPerformance;
import org.apache.tomcat.unittest.tags.Bug53545;


public class TestTagHandlerPoolPerformance extends TomcatBaseTest {

    @Test
    public void testConcurrency() throws Exception {
        // Create a working TagHandlerPool
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, true);

        Wrapper w = (Wrapper) tomcat.getHost().findChildren()[0].findChild("jsp");
        TagHandlerPool tagHandlerPool = new TagHandlerPool();
        tagHandlerPool.init(w.getServlet().getServletConfig());

        for (int i = 1; i < 9; i++) {
            TesterThreadedPerformance test = new TesterThreadedPerformance(
                    i, 5000000, new TestInstanceSupplier(tagHandlerPool));
            long duration = test.doTest();
            System.out.println(i + " threads completed in " + duration + "ns");
        }
    }


    private static class TestInstanceSupplier implements Supplier<IntConsumer> {

        private final TagHandlerPool tagHandlerPool;

        public TestInstanceSupplier(TagHandlerPool tagHandlerPool) {
            this.tagHandlerPool = tagHandlerPool;
        }


        @Override
        public IntConsumer get() {
            return new TestInstance(tagHandlerPool);
        }
    }


    private static class TestInstance implements IntConsumer {

        private final TagHandlerPool tagHandlerPool;

        public TestInstance(TagHandlerPool tagHandlerPool) {
            this.tagHandlerPool = tagHandlerPool;
        }

        @Override
        public void accept(int value) {
            try {
                Tag t = tagHandlerPool.get(Bug53545.class);
                tagHandlerPool.reuse(t);
            } catch (JspException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
