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


package org.apache.tomcat;

import java.io.InputStream;
import java.util.Properties;

public class Apr {
    public  static String Platform = null;
    public  static String Cpu      = null;
    public  static String[] Libraries = null;

    static {
        String prop = System.getProperty("os.name");
        String platform = "unknown";

        if (name.startsWith("Windows"))
            Platform = "windows";
        else if (name.equals("Linux"))
            Platform = "linux2";
        else if (name.equals("SunOS"))
            Platform = "solaris";
        else if (name.equals("HP-UX"))
            Platform = "hpux";
        else
            Platform = "unknown";
       prop = System.getProperty("os.arch");

        if (Platform.equals("windows")) {
            if (prop.equals("x86"))
                Cpu = "i686";
            else
                Cpu = prop;
        }
        else if (Platform.equals("linux2")) {
            if (prop.equals("x86"))
                Cpu = "i686";
            else
                Cpu = prop;
        }
        else if (Platform.equals("solaris")) {
            Cpu = prop;
        }
        else if (Platform.equals("hpux")) {
            if (prop.startsWith("PA_RISC"))
                Cpu = "parisc2";
            else if (prop.startsWith("IA64"))
                Cpu = "ia64";
            else
                Cpu = prop;
        }
        else
            Cpu = "unknown";

        try {
            InputStream is = Apr.class.getResourceAsStream
                ("/org/apache/tomcat/apr.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            int count = Integer.parseInt(props.getProperty(Platform + ".count"));
            Libraries = new String[count];
            for (int i = 0; i < count; i++) {
                Libraries[i] = props.getProperty(Platfrom + "." + i);
            }
        }
        catch (Throwable t) {
            ; // Nothing
        }
    }
}
