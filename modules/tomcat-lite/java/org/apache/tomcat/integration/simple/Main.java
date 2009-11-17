/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.integration.simple;

import org.apache.tomcat.integration.ObjectManager;

/**
 * Replacement for tomcat-lite specific Main, using the simple 
 * injection. SimpleObjectManager also has support for simple 
 * command line processing - CLI is treated the same with 
 * properties from the config file. 
 * 
 * @author Costin Manolache
 */
public class Main {

    public static void main(String args[]) 
    throws Exception {
        SimpleObjectManager om = new SimpleObjectManager();
        
        // Will process CLI. 
        // 'config' will load a config file.
        om.bind("Main.args", args);

        Runnable main = (Runnable) om.get("Main");
        if (main == null) {
            // TODO: look for a pre-defined name in local dir, resource,
            // manifest
            System.err.println("Using default tomcat-lite configuration");

            if (args.length == 0) {
                System.err.println("Example command line:");
                System.err.println("-context /:webapps/ROOT -Connector.port 9999");
            }
            
            String cfgFile = "org/apache/tomcat/lite/config.properties";
            om.loadResource(cfgFile);
            main = (Runnable) om.get("Main");
        }
        
        // add JMX support
        ObjectManager jmx = (ObjectManager) om.get("JMX");
        if (jmx != null) {
            jmx.register(om);
        }

        main.run();

    }    
}
