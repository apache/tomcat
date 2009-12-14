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


import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import junit.framework.TestCase;

import org.apache.tomcat.lite.TestMain;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpChannel.RequestCompleted;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.IOBuffer;

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
  HttpConnector clientCon = TestMain.getClientAndInit();
  ThreadRunner tr;
  static MBeanServer server;
  
  AtomicInteger ok = new AtomicInteger();
  Object lock = new Object();
  int reqCnt;

  Map<HttpRequest, HttpRequest> active = new HashMap();
  
  public void xtest1000Async() throws Exception {
      try {
          asyncRequest(10, 100);
      } finally {
          dumpHeap("heapAsync.bin");
      }
      
  }

  public void xtest10000Async() throws Exception {
      try {
          asyncRequest(20, 500);
      } finally {
          dumpHeap("heapAsync.bin");
      }
  }
  
  public void asyncRequest(int thr, int perthr) throws Exception {
      reqCnt = thr * perthr;
      long t0 = System.currentTimeMillis();
      tr = new ThreadRunner(thr, perthr) {
          public void makeRequest(int i) throws Exception {
              HttpRequest cstate = clientCon.request("localhost", 8802);
              synchronized (active) {
                  active.put(cstate, cstate);
              }
              
              cstate.requestURI().set("/hello");
              cstate.setCompletedCallback(reqCallback);
              
              // Send the request, wait response
              Thread.currentThread().sleep(20);
              cstate.send();
            }
      };
      tr.run();
      assertEquals(0, tr.errors.get());
      synchronized (lock) {
          lock.wait(reqCnt * 100);
      }
      assertEquals(reqCnt, ok.get());
      System.err.println(reqCnt + " Async requests: " + (System.currentTimeMillis() - t0));
  }
  
  public void xtestURLRequest() throws Exception {
      urlRequest(10, 200);
  }

  public void testURLRequest2() throws Exception {
      urlRequest(40, 500);
  }
  
  public void urlRequest(int thr, int cnt) throws Exception {
          
      
      try {
          HttpConnector testServer = TestMain.testServer;
          
          tr = new ThreadRunner(thr, cnt) {

              public void makeRequest(int i) throws Exception {
                  try {
                      URL url = new URL("http://localhost:8802/hello");
                      HttpURLConnection con = (HttpURLConnection) url.openConnection();
                      con.setReadTimeout(4000000);
                      con.connect();
                      if (con.getResponseCode() != 200) {
                          errors.incrementAndGet();
                      }
                      BBuffer out = new IOBuffer().append(con.getInputStream()).copyAll(null);
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
          
          //assertEquals(testServer., actual)
      } finally {
          dumpHeap("heapURLReq.bin");
      }
  }
  
  // TODO: move to a servlet
  private void dumpHeap(String file) throws InstanceNotFoundException,
      MBeanException, ReflectionException, MalformedObjectNameException {

      if (server == null) {
          server = ManagementFactory.getPlatformMBeanServer();
          
      }
      File f1 = new java.io.File(file);
      if (f1.exists()) {
          f1.delete();
      }
      server.invoke(new ObjectName("com.sun.management:type=HotSpotDiagnostic"),
              "dumpHeap",
              new Object[] {file, Boolean.FALSE /* live */}, 
              new String[] {String.class.getName(), "boolean"});
  }


  RequestCompleted reqCallback = new RequestCompleted() {
    @Override
    public void handle(HttpChannel data, Object extraData) 
        throws IOException {
        String out = data.getIn().copyAll(null).toString();
        if (200 != data.getResponse().getStatus()) {
            System.err.println("Wrong status");
            tr.errors.incrementAndGet();            
        }
        if (!"Hello world".equals(out)) {
            tr.errors.incrementAndGet();
            System.err.println("bad result " + out);
        }        
        synchronized (active) {
            active.remove(data.getRequest());
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
  
  
}
