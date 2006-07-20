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

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 *
 */
public class TcpConnection  { // implements Endpoint {
    /**
     * Maxium number of times to clear the socket input buffer.
     */
    static  int MAX_SHUTDOWN_TRIES=20;

    public TcpConnection() {
    }

    // -------------------- Properties --------------------

    PoolTcpEndpoint endpoint;
    Socket socket;

    public static void setMaxShutdownTries(int mst) {
	MAX_SHUTDOWN_TRIES = mst;
    }
    public void setEndpoint(PoolTcpEndpoint endpoint) {
	this.endpoint = endpoint;
    }

    public PoolTcpEndpoint getEndpoint() {
	return endpoint;
    }

    public void setSocket(Socket socket) {
	this.socket=socket;
    }

    public Socket getSocket() {
	return socket;
    }

    public void recycle() {
        endpoint = null;
        socket = null;
    }

    // Another frequent repetition
    public static int readLine(InputStream in, byte[] b, int off, int len)
	throws IOException
    {
	if (len <= 0) {
	    return 0;
	}
	int count = 0, c;

	while ((c = in.read()) != -1) {
	    b[off++] = (byte)c;
	    count++;
	    if (c == '\n' || count == len) {
		break;
	    }
	}
	return count > 0 ? count : -1;
    }

    
    // Usefull stuff - avoid having it replicated everywhere
    public static void shutdownInput(Socket socket)
	throws IOException
    {
	try {
	    InputStream is = socket.getInputStream();
	    int available = is.available ();
	    int count=0;
	    
	    // XXX on JDK 1.3 just socket.shutdownInput () which
	    // was added just to deal with such issues.
	    
	    // skip any unread (bogus) bytes
	    while (available > 0 && count++ < MAX_SHUTDOWN_TRIES) {
		is.skip (available);
		available = is.available();
	    }
	}catch(NullPointerException npe) {
	    // do nothing - we are just cleaning up, this is
	    // a workaround for Netscape \n\r in POST - it is supposed
	    // to be ignored
	}
    }
}


