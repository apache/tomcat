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
package org.apache.cometd.bayeux;

/**
 * Cometd Listener interface.<br/>
 * For local clients, in order to receive messages, they pass in a callback object
 * when the local client is created using the {@link Bayeux#newClient(String,Listener)} method.
 * This callback object, implementing the Listener interface, is used to deliver messages to local, in JVM, clients.
 * @author Greg Wilkins
 * @author Filip Hanik
 *
 */
public interface Listener
{
    /**
     * This method is called when the client is removed (explicitly or from a timeout)
     * @param timeout - true if the client was removed from a timeout
     * false if it was removed explicitly.
     */
    public void removed(boolean timeout);

    /**
     * Invoked when a message is delivered to the client.
     * The message contains the message itself, as well as what channel this message came through
     * and who the sender is. If someone invoked {@link Client#deliver(Message)} then the channel reference will
     * be null.
     * @param msg
     */
    public void deliver(Message[] msg);
}
