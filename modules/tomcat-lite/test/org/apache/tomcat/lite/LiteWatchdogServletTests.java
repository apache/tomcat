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

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import junit.framework.Test;
import junit.framework.TestResult;

import org.apache.tomcat.test.watchdog.WatchdogClient;


public class LiteWatchdogServletTests extends WatchdogClient {
  
  
  public LiteWatchdogServletTests() {
      goldenDir = base + "/src/clients/org/apache/jcheck/servlet/client/";
      testMatch = 
          //"HttpServletResponseWrapperSetStatusMsgTest";
          //"ServletContextAttributeAddedEventTest";
          null;
          // ex: "ServletToJSP";
      file = base + "/src/conf/servlet-gtest.xml";
      targetMatch = "gtestservlet-test";
  }
  
  protected void beforeSuite() {
      // required for the tests
      System.setProperty("org.apache.coyote.USE_CUSTOM_STATUS_MSG_IN_HEADER",
              "true");
      String path = System.getProperty("watchdog.home");
      if (path != null) {
          base = path;
      }
      
      try {
          initServerWithWatchdog(base);
      } catch (ServletException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
      } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
      }
  }
  
  public void initServerWithWatchdog(String wdDir) throws ServletException, 
          IOException {

      File f = new File(wdDir + "/build/webapps");
      
      //CoyoteServer connector = new CoyoteServer();
      //connector.addAdapter("/", new MapperAdapter());

      TomcatLite liteServer = new TomcatLite();
      LiteTestHelper.addConnector(liteServer, 8080, true);
      liteServer.init("webapps/ROOT", "/");

      for (String s : new String[] {      
              "servlet-compat", 
              "servlet-tests",
              "jsp-tests"} ) {
          liteServer.init(f.getCanonicalPath() + "/" + s, 
                        "/" + s);
      }

      //connector.init();
      liteServer.init();
      liteServer.start();

      liteServer.startConnector();
  }

  
  
  protected void afterSuite(TestResult res) {
      // no need to stop it - using daemon threads.
      System.err.println("DONE");
  }
  
  
  /** 
   * Magic JUnit method 
   */
  public static Test suite() {
      // The individual targets are dups - and bad ones, 
      // RequestWrapper are missing part of the URL
      return new LiteWatchdogServletTests().getSuite();
  }
}
