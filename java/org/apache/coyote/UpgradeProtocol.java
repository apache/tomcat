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

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.tomcat.util.net.SocketWrapperBase;

public interface UpgradeProtocol {

    /**
     * @param isSSLEnabled Is this for a connector that is configured to support
     *                     TLS. Some protocols (e.g. HTTP/2) only support HTTP
     *                     upgrade over non-secure connections.
     * @return The name that clients will use to request an upgrade to this
     *         protocol via an HTTP/1.1 upgrade request or <code>null</code> if
     *         upgrade via an HTTP/1.1 upgrade request is not supported.
     */
    public String getHttpUpgradeName(boolean isSSLEnabled);

    /**
     * @return The byte sequence as listed in the IANA registry for this
     *         protocol or <code>null</code> if upgrade via ALPN is not
     *         supported.
     */
    public byte[] getAlpnIdentifier();

    /**
     * @return The name of the protocol as listed in the IANA registry if and
     *         only if {@link #getAlpnIdentifier()} returns the UTF-8 encoding
     *         of this name. If {@link #getAlpnIdentifier()} returns some other
     *         byte sequence, then this method returns the empty string. If
     *         upgrade via ALPN is not supported then <code>null</code> is
     *         returned.
     */
    /*
     * Implementation note: If Tomcat ever supports ALPN for a protocol where
     *                      the identifier is not the UTF-8 encoding of the name
     *                      then some refactoring is going to be required.
     *
     * Implementation note: Tomcat assumes that the UTF-8 encoding of this name
     *                      will not exceed 255 bytes. Tomcat's behaviour if
     *                      longer names are used is undefined.
     */
    public String getAlpnName();

    /**
     * @param socketWrapper The socketWrapper for the connection that requires
     *                      a processor
     * @param adapter The Adapter instance that provides access to the standard
     *                Engine/Host/Context/Wrapper processing chain
     *
     * @return A processor instance for processing a connection using this
     *         protocol.
     */
    public Processor getProcessor(SocketWrapperBase<?> socketWrapper, Adapter adapter);


    /**
     * @param adapter The Adapter to use to configure the new upgrade handler
     * @param request A copy (may be incomplete) of the request that triggered
     *                the upgrade
     *
     * @return An instance of the HTTP upgrade handler for this protocol
     */
    public InternalHttpUpgradeHandler getInternalUpgradeHandler(Adapter adapter, Request request);


    /**
     * Allows the implementation to examine the request and accept or reject it
     * based on what it finds.
     *
     * @param request The request that included an upgrade header for this
     *                protocol
     *
     * @return <code>true</code> if the request is accepted, otherwise
     *         <code>false</code>
     */
    public boolean accept(Request request);
}
