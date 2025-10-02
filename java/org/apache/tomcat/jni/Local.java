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
package org.apache.tomcat.jni;

/**
 * Local socket.
 *
 * @deprecated The scope of the APR/Native Library will be reduced in Tomcat 9.1.x / Tomcat Native 2.x and has been
 *                 reduced in Tomcat 10.1.x / Tomcat Native 2.x onwards to only include those components required to
 *                 provide OpenSSL integration with the NIO and NIO2 connectors.
 */
@Deprecated
public class Local {

    /**
     * Create a socket.
     *
     * @param path The address of the new socket.
     * @param cont The parent pool to use
     *
     * @return The new socket that has been set up.
     *
     * @throws Exception If socket creation failed
     */
    public static native long create(String path, long cont) throws Exception;

    /**
     * Bind the socket to its associated port
     *
     * @param sock The socket to bind
     * @param sa   The socket address to bind to This may be where we will find out if there is any other process using
     *                 the selected port.
     *
     * @return the operation status
     */
    public static native int bind(long sock, long sa);

    /**
     * Listen to a bound socket for connections.
     *
     * @param sock    The socket to listen on
     * @param backlog The number of outstanding connections allowed in the sockets listen queue. If this value is less
     *                    than zero, for NT pipes the number of instances is unlimited.
     *
     * @return the operation status
     */
    public static native int listen(long sock, int backlog);

    /**
     * Accept a new connection request
     *
     * @param sock The socket we are listening on.
     *
     * @return A copy of the socket that is connected to the socket that made the connection request. This is the socket
     *             which should be used for all future communication.
     *
     * @throws Exception If accept failed
     */
    public static native long accept(long sock) throws Exception;

    /**
     * Issue a connection request to a socket either on the same machine or a different one.
     *
     * @param sock The socket we wish to use for our side of the connection
     * @param sa   The address of the machine we wish to connect to. Unused for NT Pipes.
     *
     * @return the operation status
     */
    public static native int connect(long sock, long sa);

}
