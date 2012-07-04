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
package org.apache.catalina.nonblocking;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.BytesStreamer;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.buf.ByteChunk;
import org.junit.Assert;
import org.junit.Test;


public class TestNonBlockingAPI extends TomcatBaseTest {

    @Override
    protected String getProtocol() {
        return Http11NioProtocol.class.getName();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testOne() throws Exception {
        // Configure a context with digest auth and a single protected resource
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext)
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        NBTesterServlet servlet = new NBTesterServlet();
        String servletName = NBTesterServlet.class.getName();
        Wrapper servletWrapper = tomcat.addServlet(ctx, servletName, servlet);
        ctx.addServletMapping("/", servletName);

        tomcat.start();

        Map<String,List<String>> resHeaders= new HashMap<String, List<String>>();
        int rc = postUrl(true, new DataWriter(),"http://localhost:" + getPort() + "/", new ByteChunk(),resHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    public static class DataWriter implements BytesStreamer {
        final int max = 5;
        int count = 0;
        byte[] b = "WANTMORE".getBytes();
        byte[] f = "FINISHED".getBytes();
        @Override
        public int getLength() {
            return b.length * max;
        }

        @Override
        public int available() {
            if (count<max) {
                return b.length;
            } else {
                return 0;
            }
        }

        @Override
        public byte[] next() {
            if (count < max) {
                if (count>0) try {Thread.sleep(6000);}catch(Exception x){}
                count++;
                if (count<max)
                    return b;
                else
                    return f;
            } else {
                return null;
            }
        }

    }

    @WebServlet(asyncSupported=true)
    public static class NBTesterServlet extends TesterServlet {

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            //step 1 - start async
            AsyncContext actx = req.startAsync();
            actx.setTimeout(Long.MAX_VALUE);
            actx.addListener(new AsyncListener() {

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    System.out.println("onTimeout");

                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    System.out.println("onStartAsync");

                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    System.out.println("onError");

                }

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    System.out.println("onComplete");

                }
            });
            //step 2 - notify on read
            ServletInputStream in = req.getInputStream();
            ReadListener rlist = new TestReadListener(actx);
            in.setReadListener(rlist);

            while (in.isReady()) {
                rlist.onDataAvailable();
            }
            //step 3 - notify that we wish to read
            //ServletOutputStream out = resp.getOutputStream();
            //out.setWriteListener(new TestWriteListener(actx));

        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            doGet(req,resp);
        }

        private class TestReadListener implements ReadListener {
            AsyncContext ctx;

            public TestReadListener(AsyncContext ctx) {
                this.ctx = ctx;
            }

            @Override
            public void onDataAvailable() {
                try {
                    ServletInputStream in = ctx.getRequest().getInputStream();
                    int avail=0;
                    String s = "";
                    while ((avail=in.dataAvailable()) > 0) {
                        byte[] b = new byte[avail];
                        in.read(b);
                        s += new String(b);
                    }
                    System.out.println(s);
                    if ("FINISHED".equals(s)) {
                        ctx.complete();
                        ctx.getResponse().getWriter().print("OK");
                    } else {
                        in.isReady();
                    }
                }catch (Exception x) {
                    x.printStackTrace();
                    ctx.complete();
                }

            }

            @Override
            public void onAllDataRead() {
                System.out.println("onAllDataRead");

            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("onError");
                throwable.printStackTrace();

            }
        }

        private class TestWriteListener implements WriteListener {

            AsyncContext ctx;

            public TestWriteListener(AsyncContext ctx) {
                this.ctx = ctx;
            }

            @Override
            public void onWritePossible() {
                // TODO Auto-generated method stub

            }

            @Override
            public void onError(Throwable throwable) {
                // TODO Auto-generated method stub

            }

        }



    }


    private static class StockQuotes {
        public StockQuotes() {
            super();
        }
        Random r = new Random(System.currentTimeMillis());

        public String getNextQuote() {
            return String.format("VMW: $%10.0f", r.nextDouble());
        }



    }


}
