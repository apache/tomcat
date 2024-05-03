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
package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

public interface IStoreFactory {

    /**
     * @return the writer
     */
    StoreAppender getStoreAppender();

    /**
     * Set the store appender.
     *
     * @param storeWriter the writer
     */
    void setStoreAppender(StoreAppender storeWriter);

    /**
     * Set the registry.
     *
     * @param aRegistry the registry to be associated with
     */
    void setRegistry(StoreRegistry aRegistry);

    /**
     * @return the associated registry
     */
    StoreRegistry getRegistry();

    /**
     * Store a server.xml element with attributes and children.
     *
     * @param aWriter  the writer to write to
     * @param indent   the indentation
     * @param aElement the element to write
     *
     * @throws Exception if an error occurs
     */
    void store(PrintWriter aWriter, int indent, Object aElement) throws Exception;

    /**
     * Store XML header.
     *
     * @param aWriter the writer to write to
     */
    void storeXMLHead(PrintWriter aWriter);

}