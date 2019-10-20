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
package org.apache.tomcat.dbcp.dbcp;

import java.util.HashSet;
import java.util.Set;

/**
 * Simple class loader that just records the classes it was asked to load.
 */
public class TesterClassLoader extends ClassLoader {

    private Set<String> loadedClasses = new HashSet<String>();
    
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        Class<?> clazz =  super.loadClass(name, resolve);
        loadedClasses.add(name);
        return clazz;
    }

    public boolean didLoad(String className) {
        return loadedClasses.contains(className);
    }
}
