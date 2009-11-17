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

package org.apache.tomcat.lite;

import java.io.IOException;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.tomcat.integration.simple.SimpleObjectManager;


public class PropertiesSpiTest extends TestCase {

    SimpleObjectManager spi;
    
    public void setUp() {
        spi = new SimpleObjectManager();
        
        spi.getProperties().put("obj1.name", "foo");
        spi.getProperties().put("obj1.(class)", BoundObj.class.getName());
        
    }
    
    public void testArgs() throws IOException { 
        spi = new SimpleObjectManager(new String[] {
            "-a=1", "-b", "2"});
        Properties res = spi.getProperties();
        
        assertEquals("1", res.get("a"));
        assertEquals("2", res.get("b"));
        
        
    }
    
    public static class BoundObj {
        String name;
        
        public void setName(String n) {
            this.name = n;
        }
    }
    
    public void testBind() throws Exception {
        BoundObj bo = new BoundObj();
        spi.bind("obj1", bo);
        assertEquals(bo.name, "foo");        
    }
    
    public void testCreate() throws Exception {
        BoundObj bo = (BoundObj) spi.get("obj1");
        assertEquals(bo.name, "foo");
    }
}
