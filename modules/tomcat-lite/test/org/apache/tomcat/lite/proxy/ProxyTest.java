/*
n * Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.lite.proxy;

import java.io.IOException;

import org.apache.tomcat.lite.TestMain;

import junit.framework.TestCase;


public class ProxyTest extends TestCase {

  String resStr;

  public void setUp() throws Exception {
      TestMain.getTestServer();
  }

  public void tearDown() throws IOException {
  }

  public void xtestRequestSlowChunked() throws Exception {
      resStr =
          TestMain.get("http://localhost:8903/sleep/1c").toString();
      assertEquals("sleep 1csleep 1c", resStr);
  }

  public void testSingleRequest() throws Exception {
      String resStr =
          TestMain.get("http://localhost:8903/hello").toString();
      assertEquals("Hello world", resStr);
  }


  public void test2Requests() throws Exception {
      String resStr =
          TestMain.get("http://localhost:8903/hello").toString();
      assertEquals("Hello world", resStr);
      resStr =
          TestMain.get("http://localhost:8903/hello?a=b").toString();
      assertEquals("Hello world", resStr);
  }

  public void testRequestSimple() throws Exception {
      resStr =
          TestMain.get("http://localhost:8903/hello").toString();
      assertEquals("Hello world", resStr);
      resStr =
          TestMain.get("http://localhost:8903/hello").toString();
      assertEquals("Hello world", resStr);
      resStr =
          TestMain.get("http://localhost:8903/hello").toString();
      assertEquals(resStr, "Hello world");

  }

  public void testExtAdapter() throws Exception {
      String res =
              TestMain.get("http://www.apache.org/").toString();
      assertTrue(res.indexOf("Apache") > 0);

      Thread.currentThread().sleep(100);
      // second time - are we reusing ?
      res =
          TestMain.get("http://www.apache.org/").toString();

      assertTrue(res.indexOf("Apache") > 0);

  }

  public void testStaticAdapter() throws Exception {

      assertEquals("Hello world",
          TestMain.get("http://localhost:8802/hello").toString());
      assertEquals("Hello world2",
          TestMain.get("http://localhost:8802/2nd").toString());

    }

  public void testRequestParams() throws Exception {
      // qry string
      String resStr =
          TestMain.get("http://localhost:8903/echo/foo?q=a&b")
          .toString();
      assertTrue(resStr, resStr.indexOf("foo?q=a&b") > 0);
  }


  public void testRequestChunked() throws Exception {
      // Chunked encoding
      String resStr =
          TestMain.get("http://localhost:8903/chunked/test")
          .toString();
      assertEquals(4, resStr.length());
      assertTrue(resStr.indexOf("AAA") >= 0);
  }


  public void testRequestSlow() throws Exception {
      // Slow
      String resStr =
          TestMain.get("http://localhost:8903/sleep/1").toString();
      assertEquals("sleep 1sleep 1", resStr.toString());
  }
}
