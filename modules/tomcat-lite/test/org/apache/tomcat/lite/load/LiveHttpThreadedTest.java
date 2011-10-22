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
package org.apache.tomcat.lite.load;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.apache.tomcat.lite.TestMain;
import org.apache.tomcat.lite.http.HttpClient;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpChannel.RequestCompleted;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.SocketConnector;

/*
  Notes on memory use ( from heap dumps ):
    - buffers are not yet recycled ( the BBuffers used in channels )

    - each active connection consumes at least 26k - 2 buffers + head buffer
     ( 8k each )
     TODO: could 'peak' in the In buffer and move headRecv to HttpChannel


    - HttpChannel keeps about 64K ( for the hello world ).
    -- res is 25k
    -- req is 32k, BufferedIOReader 16k,

   TODO:
    - leak in NioThread.active - closed sockets not removed
    - need to rate-limit and queue requests - OOM
    - timeouts
    - seems few responses missing on large async requests (URL works)
 */

/**
 * Long running test - async tests are failing since rate control
 * is not implemented ( too many outstanding requests - OOM ),
 * it seems there is a bug as well.
 */
public class LiveHttpThreadedTest extends TestCase {
    HttpConnector clientCon = TestMain.shared().getClient();
    HttpConnector serverCon = TestMain.shared().getTestServer();

    HttpConnector spdyClient =
        HttpClient.newClient().setCompression(false);

    HttpConnector spdyClientCompress =
        HttpClient.newClient();

    HttpConnector spdyClientCompressSsl =
        HttpClient.newClient();

    ThreadRunner tr;
    static boolean dumpHeap = true;

    AtomicInteger ok = new AtomicInteger();
    int reqCnt;

    Map<HttpRequest, HttpRequest> active = new HashMap();

    public void tearDown() throws IOException {
        clientCon.cpool.clear();
    }

    public void test1000Async() throws Exception {
//        try {
            asyncRequest(10, 100, false, false, clientCon, "AsyncHttp");
//          } finally {
//          dumpHeap("heapAsync.bin");
//      }

    }

    public void test10000Async() throws Exception {
        asyncRequest(20, 500, false, false, clientCon, "AsyncHttp");
    }


    public void test1000AsyncSsl() throws Exception {
        asyncRequest(20, 50, false, true, clientCon, "AsyncHttpSsl");
    }

    public void test10000AsyncSsl() throws Exception {
        asyncRequest(20, 500, false, true, clientCon, "AsyncHttpSsl");
    }

    public void test1000AsyncSpdy() throws Exception {
        asyncRequest(10, 100, true, false, spdyClient, "AsyncSpdy");
    }

    public void test10000AsyncSpdy() throws Exception {
        asyncRequest(20, 500, true, false, spdyClient, "AsyncSpdy");
    }

    public void test1000AsyncSpdyComp() throws Exception {
            asyncRequest(10, 100, true, false, spdyClientCompress, "AsyncSpdyComp");
    }

    public void test10000AsyncSpdyComp() throws Exception {
        asyncRequest(20, 500, true, false, spdyClientCompress, "AsyncSpdyComp");
    }

    public void xtest1000AsyncSpdySsl() throws Exception {
        asyncRequest(10, 100, true, true, spdyClient, "AsyncSpdySsl");
    }

    public void xtest1000AsyncSpdyCompSsl() throws Exception {
        asyncRequest(10, 100, true, true, spdyClientCompress, "AsyncSpdyCompSsl");
    }

    public void xtest10000AsyncSpdyCompSsl() throws Exception {
        asyncRequest(20, 500, true, true, spdyClientCompress, "AsyncSpdyCompSsl");
    }

    Object thrlock = new Object();
    Object lock = new Object();

    public void asyncRequest(final int thr, int perthr,
            final boolean spdy, final boolean ssl,
            final HttpConnector clientCon, String test) throws Exception {
        clientCon.getConnectionPool().clear();
        reqCnt = thr * perthr;
        long t0 = System.currentTimeMillis();

        tr = new ThreadRunner(thr, perthr) {
            public void makeRequest(int i) throws Exception {
                HttpRequest cstate = clientCon.request("localhost",
                        ssl ? 8443 : 8802);
                synchronized (active) {
                    active.put(cstate, cstate);
                }
                if (spdy) {
                    // Magic way to force spdy - will be replaced with
                    // a negotiation.
                    cstate.setProtocol("SPDY/1.0");
                }
                if (ssl) {
                    cstate.setSecure(true);
                }
                cstate.requestURI().set("/hello");
                cstate.setCompletedCallback(reqCallback);
                // no body
                cstate.getBody().close();

                cstate.send();

                while (active.size() >= thr) {
                    synchronized(thrlock) {
                        thrlock.wait();
                    }
                }
            }
        };
        tr.run();
        synchronized (lock) {
            if (ok.get() < reqCnt) {
                lock.wait(reqCnt * 100);
            }
        }
        long time = (System.currentTimeMillis() - t0);

        System.err.println("====== " + test +
                " threads: " + thr + ", req: " +
                reqCnt + ", sendTime" + tr.time +
                ", time: " + time +
                ", connections: " + clientCon.getConnectionPool().getSocketCount() +
                ", avg: " + (time / reqCnt));

        assertEquals(reqCnt, ok.get());
        assertEquals(0, tr.errors.get());
    }

    RequestCompleted reqCallback = new RequestCompleted() {
        @Override
        public void handle(HttpChannel data, Object extraData)
        throws IOException {
            String out = data.getIn().copyAll(null).toString();
            if (200 != data.getResponse().getStatus()) {
                System.err.println("Wrong status");
                tr.errors.incrementAndGet();
            } else if (!"Hello world".equals(out)) {
                tr.errors.incrementAndGet();
                System.err.println("bad result " + out);
            }
            synchronized (active) {
                active.remove(data.getRequest());
            }
            synchronized (thrlock) {
                thrlock.notify();
            }
            data.release();
            int okres = ok.incrementAndGet();
            if (okres >= reqCnt) {
                synchronized (lock) {
                    lock.notify();
                }
            }
        }
    };



    public void testURLRequest1000() throws Exception {
        urlRequest(10, 100, false, "HttpURLConnection");
    }

    public void xtestURLRequest10000() throws Exception {
        urlRequest(20, 500, false, "HttpURLConnection");

    }

    // I can't seem to get 1000 requests to all complete...
    public void xtestURLRequestSsl100() throws Exception {
        urlRequest(10, 10, true, "HttpURLConnectionSSL");
    }

    public void xtestURLRequestSsl10000() throws Exception {
        urlRequest(20, 500, true, "HttpURLConnectionSSL");

    }

    /**
     * HttpURLConnection client against lite.http server.
     */
    public void urlRequest(int thr, int cnt, final boolean ssl, String test)
            throws Exception {
        long t0 = System.currentTimeMillis();


        try {
            HttpConnector testServer = TestMain.getTestServer();

            tr = new ThreadRunner(thr, cnt) {

                public void makeRequest(int i) throws Exception {
                    try {
                        BBuffer out = BBuffer.allocate();
                        String url = ssl ? "https://localhost:8443/hello" :
                            "http://localhost:8802/hello";
                        HttpURLConnection con =
                            TestMain.getUrl(url, out);
                        if (con.getResponseCode() != 200) {
                            errors.incrementAndGet();
                        }
                        if (!"Hello world".equals(out.toString())) {
                            errors.incrementAndGet();
                            System.err.println("bad result " + out);
                        }
                    } catch(Throwable t) {
                        t.printStackTrace();
                        errors.incrementAndGet();
                    }
                }
            };
            tr.run();
            assertEquals(0, tr.errors.get());
            long time = (System.currentTimeMillis() - t0);

            System.err.println("====== " + test + " threads: " + thr + ", req: " +
                    (thr * cnt) + ", time: " + time + ", avg: " +
                    (time / (thr * cnt)));
        } finally {
            //dumpHeap("heapURLReq.bin");
        }
    }

    // TODO: move to a servlet


}
