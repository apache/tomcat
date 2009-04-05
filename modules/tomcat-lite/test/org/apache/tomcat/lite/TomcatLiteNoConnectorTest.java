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
package org.apache.tomcat.lite;



import java.io.IOException;

import junit.framework.TestCase;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;

public class TomcatLiteNoConnectorTest extends TestCase {

  TomcatLite lite = new TomcatLite();
  
  public void setUp() throws Exception {
      LiteTestHelper.initServletsAndRun(lite, 0);
  }
  
  public void tearDown() throws Exception {
    lite.stop();
  }
  

  
  public void testSimpleRequest() throws Exception {
    ByteChunk out = new ByteChunk();
    
    ServletRequestImpl req = 
        LiteTestHelper.createMessage(lite, "/test1/1stTest", out);
    
    // more changes can be made to the req, populate fields that a 
    // connector would
    
    ServletResponseImpl res = lite.service(req);
    
    assertEquals("Hello world", out.toString());
    // Headers are still in the response
    assertEquals(res.getHeader("Foo"), "Bar");
    assertEquals(res.getStatus(), 200);
  }

  public void testPostRequest() throws Exception {
    ByteChunk out = new ByteChunk();
    ServletRequestImpl req = 
      LiteTestHelper.createMessage(lite, "/test1/1stTest", out);
    req.setMethod("POST");

    ServletResponseImpl res = lite.service(req);

    assertEquals("Hello post world", out.toString());
    // Headers are still in the response
    assertEquals(res.getHeader("Foo"), "Post");
    assertEquals(res.getStatus(), 200);
  }
  
  public void testException() throws IOException, Exception {
    ByteChunk out = new ByteChunk();
    ServletRequestImpl req = 
        LiteTestHelper.createMessage(lite, "/test1/testException", out);
    ServletResponseImpl res = lite.service(req);
    assertEquals(res.getStatus(), 500);
  }
  
}
