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

import junit.framework.TestCase;

import org.apache.tomcat.lite.TestMain;
import org.apache.tomcat.lite.http.DefaultHttpConnector;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpChannel.RequestCompleted;

public class LiveHttpThreadedTest extends TestCase {
  HttpConnector staticMain = TestMain.getTestServer();
  
  
  int tCount = 1;
  Thread[] threads = new Thread[tCount];
  int[] ok = new int[tCount];
  private int rCount = 100;
  
  public void xtestSimpleRequest() throws Exception {
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < tCount; i++) {
      final int j = i;
      threads[i] = new Thread(new Runnable() {
        public void run() {
          makeRequests(j, true);
        }
      });
      threads[i].start();
    }
    
    int res = 0;
    for (int i = 0; i < tCount; i++) {
      threads[i].join();
      res += ok[i];
    }
    long t1 = System.currentTimeMillis();
    System.err.println("Time: " + (t1 - t0) + " " + res);
  }

  public void testSimpleRequestNB() throws Exception {
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < tCount; i++) {
      final int j = i;
      threads[i] = new Thread(new Runnable() {
        public void run() {
          makeRequests(j, false);
        }
      });
      threads[i].start();
    }
    
    int res = 0;
    for (int i = 0; i < tCount; i++) {
      threads[i].join();
      res += ok[i];
    }
    long t1 = System.currentTimeMillis();
    System.err.println("TimeNB: " + (t1 - t0) + " " + res);
  }
  
  void makeRequests(int t, boolean b) {
    for (int i = 0; i < rCount ; i++) {
      try {
        //System.err.println("MakeReq " + t + " " + i);
        makeRequest(t, b);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  static RequestCompleted reqCallback = new RequestCompleted() {
    @Override
    public void handle(HttpChannel data, Object extraData) 
        throws IOException {
        //dumpHead(cstate);  
        //System.err.println("DATA\n" + cstate.output.toString() + "\n----");
        //assertTrue(cstate.bodyRecvBuffer.toString().indexOf("AAA") >= 0);
    
        data.release();
    }
      
  };
  
  void makeRequest(int i, boolean block) throws Exception {
    HttpChannel cstate = DefaultHttpConnector.get().get("localhost", 8802);
    
    cstate.getRequest().requestURI().set("/hello");
    cstate.setCompletedCallback(reqCallback);
    
    // Send the request, wait response
    cstate.sendRequest();
  }
  
}
