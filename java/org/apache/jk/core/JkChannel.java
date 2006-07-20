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

package org.apache.jk.core;

import java.io.IOException;

import org.apache.coyote.Request;

/**
 * A Channel represents a connection point to the outside world.
 *
 * @author Bill Barker
 */

public interface JkChannel {


    /**
     * Return the identifying name of this Channel.
     */
    public String getChannelName();

    /**
     * Send a message back to the client.
     * @param msg The message to send.
     * @param ep The connection point for this request.
     */
    public int send(Msg msg, MsgContext ep) throws IOException;

    /**
     * Recieve a message from the client.
     * @param msg The place to recieve the data into.
     * @param ep The connection point for this request.
     */
    public  int receive(Msg msg, MsgContext ep) throws IOException;

    /**
     * Flush the data to the client.
     */
    public int flush(Msg msg, MsgContext ep) throws IOException;

    /**
     * Invoke the request chain.
     */
    public int invoke(Msg msg, MsgContext ep) throws IOException;

    /**
     * Confirm that a shutdown request was recieved form us.
     */
    public boolean isSameAddress(MsgContext ep);

    /**
     * Register a new Request in the Request pool.
     */
    public void registerRequest(Request req, MsgContext ep, int count);

    /**
     * Create a new request endpoint.
     */
    public MsgContext createMsgContext();

}
