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
package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.util.concurrent.Future;

public class Bug50805 extends DefaultTestCase {
    public Bug50805(String name) {
        super(name);
    }
    
    public void test50805() throws Exception {
        init();
        this.datasource.setInitialSize(0);
        this.datasource.setMaxActive(10);
        this.datasource.setMinIdle(1);
        
        assertEquals("Current size should be 0.", 0, this.datasource.getSize());
        
        this.datasource.getConnection().close();
        
        assertEquals("Current size should be 1.", 1, this.datasource.getSize());
        assertEquals("Idle size should be 1.", 1, this.datasource.getIdle());
        assertEquals("Busy size should be 0.", 0, this.datasource.getActive());
        
        Future<Connection> fc = this.datasource.getConnectionAsync();
        
        Connection con = fc.get();
        
        assertEquals("Current size should be 1.", 1, this.datasource.getSize());
        assertEquals("Idle size should be 0.", 0, this.datasource.getIdle());
        assertEquals("Busy size should be 1.", 1, this.datasource.getActive());
        
        con.close();
        assertEquals("Current size should be 1.", 1, this.datasource.getSize());
        assertEquals("Idle size should be 1.", 1, this.datasource.getIdle());
        assertEquals("Busy size should be 0.", 0, this.datasource.getActive());
    }
}
