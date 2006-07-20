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


/**
 * This interface will be implemented by any object that
 * uses TcpConnections. It is supported by the pool tcp
 * connection manager and should be supported by future
 * managers.
 * The goal is to decouple the connection handler from
 * the thread, socket and pooling complexity.
 */
public interface TcpConnectionHandler {
    
    /** Add informations about the a "controler" object
     *  specific to the server. In tomcat it will be a
     *  ContextManager.
     *  @deprecated This has nothing to do with TcpHandling,
     *  was used as a workaround
     */
    public void setServer(Object manager);

    
    /** Used to pass config informations to the handler.
     *
     *  @deprecated This has nothing to do with Tcp,
     *    was used as a workaround.
     */
    public void setAttribute(String name, Object value );
    
    /** Called before the call to processConnection.
     *  If the thread is reused, init() should be called once per thread.
     *
     *  It may look strange, but it's a _very_ good way to avoid synchronized
     *  methods and keep per thread data.
     *
     *  Assert: the object returned from init() will be passed to
     *  all processConnection() methods happening in the same thread.
     * 
     */
    public Object[] init( );

    /**
     *  Assert: connection!=null
     *  Assert: connection.getSocket() != null
     *  Assert: thData != null and is the result of calling init()
     *  Assert: thData is preserved per Thread.
     */
    public void processConnection(TcpConnection connection, Object thData[]);    
}
