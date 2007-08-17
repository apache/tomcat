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
package org.apache.catalina.loader;
/**
 * 
 * An interface to be able to able to have named classloader.
 * Useful when distributing data through AOP or byte code injection
 * To be able to map loaders between two instances to make sure the data
 * gets loaded through the correct loader on the other node.
 * @author Filip Hanik
 * 
 */
public interface NamedClassLoader {
    
    /**
     * returns the name of this class loader
     * @return String
     */
    public String getName();
    
    /**
     * Sets the name of this class loader
     * @param name String
     */
    public void setName(String name);
    
}