/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.net;

import java.io.*;
import java.net.*;

/**
 * Default server socket factory. Doesn't do much except give us
 * plain ol' server sockets.
 *
 * @author db@eng.sun.com
 * @author Harish Prabandham
 */

// Default implementation of server sockets.

//
// WARNING: Some of the APIs in this class are used by J2EE. 
// Please talk to harishp@eng.sun.com before making any changes.
//
class DefaultServerSocketFactory extends ServerSocketFactory {

    DefaultServerSocketFactory () {
        /* NOTHING */
    }

    public ServerSocket createSocket (int port)
    throws IOException {
        return  new ServerSocket (port);
    }

    public ServerSocket createSocket (int port, int backlog)
    throws IOException {
        return new ServerSocket (port, backlog);
    }

    public ServerSocket createSocket (int port, int backlog,
        InetAddress ifAddress)
    throws IOException {
        return new ServerSocket (port, backlog, ifAddress);
    }
 
    public Socket acceptSocket(ServerSocket socket)
 	throws IOException {
 	return socket.accept();
    }
 
    public void handshake(Socket sock)
 	throws IOException {
 	; // NOOP
    }
 	    
        
 }
