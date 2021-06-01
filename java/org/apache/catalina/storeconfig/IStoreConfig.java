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

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Server;
import org.apache.catalina.Service;

public interface IStoreConfig {

    /**
     * Get Configuration Registry
     *
     * @return aRegistry that handle the store operations
     */
    StoreRegistry getRegistry();

    /**
     * Set Configuration Registry
     *
     * @param aRegistry
     *            aregistry that handle the store operations
     */
    void setRegistry(StoreRegistry aRegistry);

    /**
     * Get associated server
     *
     * @return aServer the associated server
     */
    Server getServer();

    /**
     * Set associated server
     *
     * @param aServer the associated server
     */
    void setServer(Server aServer);

    /**
     * Store the current StoreFactory Server.
     */
    void storeConfig();

    /**
     * Store the specified Server properties.
     *
     * @param aServer
     *            Object to be stored
     * @return <code>true</code> if the store operation was successful
     */
    boolean store(Server aServer);

    /**
     * Store the specified Server properties.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aServer
     *            Object to be stored
     * @throws Exception Store error occurred
     */
    void store(PrintWriter aWriter, int indent, Server aServer) throws Exception;

    /**
     * Store the specified Service properties.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aService
     *            Object to be stored
     * @throws Exception Store error occurred
     */
    void store(PrintWriter aWriter, int indent, Service aService) throws Exception;

    /**
     * Store the specified Host properties.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aHost
     *            Object to be stored
     * @throws Exception Store error occurred
     */
    void store(PrintWriter aWriter, int indent, Host aHost) throws Exception;

    /**
     * Store the specified Context properties.
     *
     * @param aContext
     *            Object to be stored
     * @return <code>true</code> if the store operation was successful
     */
    boolean store(Context aContext);

    /**
     * Store the specified Context properties.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aContext
     *            Object to be stored
     * @throws Exception Store error occurred
     */
    void store(PrintWriter aWriter, int indent, Context aContext) throws Exception;
}