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
package org.apache.tomcat.lite.cli;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.integration.jmx.JmxObjectManagerSpi;
import org.apache.tomcat.integration.simple.SimpleObjectManager;
import org.apache.tomcat.lite.TomcatLite;
import org.apache.tomcat.lite.coyote.CoyoteHttp;

/**
 * Run tomcat lite, mostly for demo. In normal use you would
 * embed it in an app, or use a small customized launcher.
 * 
 * With no arguments, it'll load only the root context / from 
 * ./webapps/ROOT
 *
 * Most configuration can be done using web.xml files, few settings 
 * can be set by flags. 
 * 
 * @author Costin Manolache
 */
public class Main {
  
  public static void main(String args[]) 
          throws Exception {
      
      if (args.length == 0) {
          System.err.println("Please specify at least one webapp.");
          System.err.println("Example:");
          System.err.println("-context /:webapps/ROOT -port 9999");
      }

      // Enable CLI processing
      SimpleObjectManager.setArgs(args);
      
      TomcatLite lite = new TomcatLite();
      ObjectManager om = lite.getObjectManager();

      // add JMX support
      new JmxObjectManagerSpi().register(om);
      
      lite.run();
  }    
}
