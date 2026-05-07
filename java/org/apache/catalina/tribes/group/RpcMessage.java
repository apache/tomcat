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
package org.apache.catalina.tribes.group;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.apache.catalina.tribes.util.Arrays;

/**
 * A message used for RPC communication between group members.
 */
public class RpcMessage implements Externalizable {

    /**
     * The payload of the message.
     */
    protected Serializable message;

    /**
     * The unique identifier for this message.
     */
    protected byte[] uuid;

    /**
     * The RPC channel identifier.
     */
    protected byte[] rpcId;

    /**
     * Whether this message is a reply.
     */
    protected boolean reply = false;

    /**
     * Default constructor required by Externalizable.
     */
    public RpcMessage() {
        // for serialization
    }

    /**
     * Create a new RpcMessage.
     *
     * @param rpcId   The RPC channel identifier
     * @param uuid    The unique identifier for this message
     * @param message The message payload
     */
    public RpcMessage(byte[] rpcId, byte[] uuid, Serializable message) {
        this.rpcId = rpcId;
        this.uuid = uuid;
        this.message = message;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        reply = in.readBoolean();
        int length = in.readInt();
        uuid = new byte[length];
        in.readFully(uuid);
        length = in.readInt();
        rpcId = new byte[length];
        in.readFully(rpcId);
        message = (Serializable) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(reply);
        out.writeInt(uuid.length);
        out.write(uuid, 0, uuid.length);
        out.writeInt(rpcId.length);
        out.write(rpcId, 0, rpcId.length);
        out.writeObject(message);
    }

    @Override
    public String toString() {
        return "RpcMessage[" + super.toString() + "] rpcId=" + Arrays.toString(rpcId) + "; uuid=" +
                Arrays.toString(uuid) + "; msg=" + message;
    }

    /**
     * A reply message sent when no RPC channel is found for a given RPC ID.
     */
    public static class NoRpcChannelReply extends RpcMessage {
        /**
         * Default constructor required by Externalizable.
         */
        public NoRpcChannelReply() {

        }

        /**
         * Create a new NoRpcChannelReply.
         *
         * @param rpcid The RPC channel identifier
         * @param uuid  The unique identifier for this message
         */
        public NoRpcChannelReply(byte[] rpcid, byte[] uuid) {
            super(rpcid, uuid, null);
            reply = true;
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            reply = true;
            int length = in.readInt();
            uuid = new byte[length];
            in.readFully(uuid);
            length = in.readInt();
            rpcId = new byte[length];
            in.readFully(rpcId);
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(uuid.length);
            out.write(uuid, 0, uuid.length);
            out.writeInt(rpcId.length);
            out.write(rpcId, 0, rpcId.length);
        }
    }


}
