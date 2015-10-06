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
package org.apache.coyote;

import java.util.concurrent.Executor;

import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * This is the main interface to be implemented by a coyote connector.
 * Adapter is the main interface to be implemented by a coyote servlet
 * container.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @see Adapter
 */
public interface ProtocolHandler {

    /**
     * The adapter, used to call the connector.
     *
     * @param adapter The adapter to associate
     */
    public void setAdapter(Adapter adapter);
    public Adapter getAdapter();


    /**
     * The executor, provide access to the underlying thread pool.
     *
     * @return The executor used to process requests
     */
    public Executor getExecutor();


    /**
     * Initialise the protocol.
     *
     * @throws Exception If the protocol handler fails to initialise
     */
    public void init() throws Exception;


    /**
     * Start the protocol.
     *
     * @throws Exception If the protocol handler fails to start
     */
    public void start() throws Exception;


    /**
     * Pause the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to pause
     */
    public void pause() throws Exception;


    /**
     * Resume the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to resume
     */
    public void resume() throws Exception;


    /**
     * Stop the protocol.
     *
     * @throws Exception If the protocol handler fails to stop
     */
    public void stop() throws Exception;


    /**
     * Destroy the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to destroy
     */
    public void destroy() throws Exception;


    /**
     * Requires APR/native library
     *
     * @return <code>true</code> if this Protocol Handler requires the
     *         APR/native library, otherwise <code>false</code>
     */
    public boolean isAprRequired();


    /**
     * Does this ProtocolHandler support sendfile?
     *
     * @return <code>true</code> if this Protocol Handler supports sendfile,
     *         otherwise <code>false</code>
     */
    public boolean isSendfileSupported();


    public void addSslHostConfig(SSLHostConfig sslHostConfig);
    public SSLHostConfig[] findSslHostConfigs();


    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol);
    public UpgradeProtocol[] findUpgradeProtocols();
}
