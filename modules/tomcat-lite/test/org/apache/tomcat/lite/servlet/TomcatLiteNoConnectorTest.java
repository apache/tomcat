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
package org.apache.tomcat.lite.servlet;



import junit.framework.TestCase;

import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.io.BBuffer;

/**
 * Example of testing servlets without using sockets.
 * 
 * @author Costin Manolache
 */
public class TomcatLiteNoConnectorTest extends TestCase {

  TomcatLite lite;
  HttpConnector con;
  
  public void setUp() throws Exception {
      con = new HttpConnector(null);
      
      lite = new TomcatLite();
      lite.setHttpConnector(con);
      
      // Load all servlets we need to test
      LiteTestHelper.initServletsAndRun(lite, 0);
  }
  
  public void tearDown() throws Exception {
    lite.stop();
  }
  
  public void testSimpleRequest() throws Exception {
      HttpChannel httpCh = con.getServer();
      
      HttpRequest req = httpCh.getRequest();
      req.setURI("/test1/1stTest");

      HttpResponse res = httpCh.getResponse();
      
      lite.getHttpConnector().getDispatcher().service(req, res, true, false);
    
      BBuffer resBody = res.getBody().readAll(null);
      assertEquals("Hello world", resBody.toString());

      assertEquals(res.getHeader("Foo"), "Bar");
      assertEquals(res.getStatus(), 200);
  }
  
//
//  public void testPostRequest() throws Exception {
//    ByteChunk out = new ByteChunk();
//    ServletRequestImpl req = 
//      LiteTestHelper.createMessage(lite, "/test1/1stTest", out);
//    req.setMethod("POST");
//
//    ServletResponseImpl res = lite.service(req);
//
//    assertEquals("Hello post world", out.toString());
//    // Headers are still in the response
//    assertEquals(res.getHeader("Foo"), "Post");
//    assertEquals(res.getStatus(), 200);
//  }
//  
//  public void testException() throws IOException, Exception {
//    ByteChunk out = new ByteChunk();
//    ServletRequestImpl req = 
//        LiteTestHelper.createMessage(lite, "/test1/testException", out);
//    ServletResponseImpl res = lite.service(req);
//    assertEquals(res.getStatus(), 500);
//  }
  
}
