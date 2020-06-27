/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet.http;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.junit.Test;

import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.filters.TesterHttpServletResponse;


/*
 * Note: Class name chosen so it matches *Performance.java and can be excluded
 *       from test runs along with other performance tests.
 */
public class TestHttpServletPerformance {

    @Test
    public void testDoOptions() throws IOException, ServletException{
        TesterServlet testerServlet = new TesterServlet();
        TesterRequest testerRequest = new TesterRequest(false);
        TesterHttpServletResponse testerResponse = new TesterHttpServletResponse();

        long start = System.nanoTime();
        for (int i = 0; i < 10000000; i++) {
            testerServlet.doOptions(testerRequest, testerResponse);
        }
        long end = System.nanoTime();

        System.out.println("doOptions()" + (end - start) + "ns");
    }


    private static class TesterServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
    }


    private static class TesterRequest extends RequestFacade {

        private final boolean allowTrace;

        public TesterRequest(boolean allowTrace) {
            super(null);
            this.allowTrace = allowTrace;
        }

        @Override
        public boolean getAllowTrace() {
            return allowTrace;
        }
    }
}
