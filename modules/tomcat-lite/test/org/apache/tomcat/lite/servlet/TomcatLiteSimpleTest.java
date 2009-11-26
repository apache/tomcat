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

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import junit.framework.TestCase;

import org.apache.tomcat.lite.io.IOBuffer;

/** 
 * TODO: convert to new API
 * 
 */
public class TomcatLiteSimpleTest extends TestCase {

  protected TomcatLite lite = new TomcatLite(); 
  
  public void setUp() throws Exception {
      LiteTestHelper.addContext(lite);
      
      lite.init();
      
      lite.setPort(8884);
      lite.start();
      lite.startConnector();
  }
    
  public void testSimpleRequest() throws Exception {
      URL url = new URL("http://localhost:8884/test1/1stTest");
      URLConnection connection = url.openConnection();
      InputStream is = connection.getInputStream();
      String res = new IOBuffer().append(is).readAll(null).toString();
      assertEquals("Hello world", res);
  }
}
