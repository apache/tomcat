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
package org.apache.catalina.tribes.io;

import java.sql.Timestamp;
import java.util.Arrays;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.util.UUIDGenerator;

/**
 * The <code>ChannelData</code> object is used to transfer a message through the channel interceptor stack and
 * eventually out on a transport to be sent to another node. While the message is being processed by the different
 * interceptors, the message data can be manipulated as each interceptor seems appropriate.
 *
 * @author Peter Rossbach
 */
public class ChannelData implements ChannelMessage {
    private static final long serialVersionUID = 1L;

    public static final ChannelData[] EMPTY_DATA_ARRAY = new ChannelData[0];

    public static volatile boolean USE_SECURE_RANDOM_FOR_UUID = false;

    /**
     * The options this message was sent with
     */
    private int options = 0;
    /**
     * The message data, stored in a dynamic buffer
     */
    private XByteBuffer message;
    /**
     * The timestamp that goes with this message
     */
    private long timestamp;
    /**
     * A unique message id
     */
    private byte[] uniqueId;
    /**
     * The source or reply-to address for this message
     */
    private Member address;

    /**
     * Creates an empty channel data with a new unique Id
     *
     * @see #ChannelData(boolean)
     */
    public ChannelData() {
        this(true);
    }

    /**
     * Create an empty channel data object
     *
     * @param generateUUID boolean - if true, a unique Id will be generated
     */
    public ChannelData(boolean generateUUID) {
        if (generateUUID) {
            generateUUID();
        }
    }


    /**
     * Creates a new channel data object with data
     *
     * @param uniqueId  - unique message id
     * @param message   - message data
     * @param timestamp - message timestamp
     */
    public ChannelData(byte[] uniqueId, XByteBuffer message, long timestamp) {
        this.uniqueId = uniqueId;
        this.message = message;
        this.timestamp = timestamp;
    }

    @Override
    public XByteBuffer getMessage() {
        return message;
    }

    @Override
    public void setMessage(XByteBuffer message) {
        this.message = message;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public byte[] getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(byte[] uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public int getOptions() {
        return options;
    }

    @Override
    public void setOptions(int options) {
        this.options = options;
    }

    @Override
    public Member getAddress() {
        return address;
    }

    @Override
    public void setAddress(Member address) {
        this.address = address;
    }

    /**
     * Generates a UUID and invokes setUniqueId
     */
    public void generateUUID() {
        byte[] data = new byte[16];
        UUIDGenerator.randomUUID(USE_SECURE_RANDOM_FOR_UUID, data, 0);
        setUniqueId(data);
    }

    public int getDataPackageLength() {
        int length = 4 + // options
                8 + // timestamp off=4
                4 + // unique id length off=12
                uniqueId.length + // id data off=12+uniqueId.length
                4 + // addr length off=12+uniqueId.length+4
                address.getDataLength() + // member data off=12+uniqueId.length+4+add.length
                4 + // message length off=12+uniqueId.length+4+add.length+4
                message.getLength();
        return length;

    }

    /**
     * Serializes the ChannelData object into a byte[] array
     *
     * @return byte[]
     */
    public byte[] getDataPackage() {
        int length = getDataPackageLength();
        byte[] data = new byte[length];
        int offset = 0;
        return getDataPackage(data, offset);
    }

    public byte[] getDataPackage(byte[] data, int offset) {
        byte[] addr = address.getData(false);
        XByteBuffer.toBytes(options, data, offset);
        offset += 4; // options
        XByteBuffer.toBytes(timestamp, data, offset);
        offset += 8; // timestamp
        XByteBuffer.toBytes(uniqueId.length, data, offset);
        offset += 4; // uniqueId.length
        System.arraycopy(uniqueId, 0, data, offset, uniqueId.length);
        offset += uniqueId.length; // uniqueId data
        XByteBuffer.toBytes(addr.length, data, offset);
        offset += 4; // addr.length
        System.arraycopy(addr, 0, data, offset, addr.length);
        offset += addr.length; // addr data
        XByteBuffer.toBytes(message.getLength(), data, offset);
        offset += 4; // message.length
        System.arraycopy(message.getBytesDirect(), 0, data, offset, message.getLength());
        return data;
    }

    /**
     * Deserializes a ChannelData object from a byte array
     *
     * @param xbuf byte[]
     *
     * @return ChannelData
     */
    public static ChannelData getDataFromPackage(XByteBuffer xbuf) {
        ChannelData data = new ChannelData(false);
        int offset = 0;
        data.setOptions(XByteBuffer.toInt(xbuf.getBytesDirect(), offset));
        offset += 4; // options
        data.setTimestamp(XByteBuffer.toLong(xbuf.getBytesDirect(), offset));
        offset += 8; // timestamp
        data.uniqueId = new byte[XByteBuffer.toInt(xbuf.getBytesDirect(), offset)];
        offset += 4; // uniqueId length
        System.arraycopy(xbuf.getBytesDirect(), offset, data.uniqueId, 0, data.uniqueId.length);
        offset += data.uniqueId.length; // uniqueId data
        // byte[] addr = new byte[XByteBuffer.toInt(xbuf.getBytesDirect(),offset)];
        int addrlen = XByteBuffer.toInt(xbuf.getBytesDirect(), offset);
        offset += 4; // addr length
        // System.arraycopy(xbuf.getBytesDirect(),offset,addr,0,addr.length);
        data.setAddress(MemberImpl.getMember(xbuf.getBytesDirect(), offset, addrlen));
        // offset += addr.length; //addr data
        offset += addrlen;
        int xsize = XByteBuffer.toInt(xbuf.getBytesDirect(), offset);
        offset += 4; // xsize length
        System.arraycopy(xbuf.getBytesDirect(), offset, xbuf.getBytesDirect(), 0, xsize);
        xbuf.setLength(xsize);
        data.message = xbuf;
        return data;

    }

    public static ChannelData getDataFromPackage(byte[] b) {
        ChannelData data = new ChannelData(false);
        int offset = 0;
        data.setOptions(XByteBuffer.toInt(b, offset));
        offset += 4; // options
        data.setTimestamp(XByteBuffer.toLong(b, offset));
        offset += 8; // timestamp
        data.uniqueId = new byte[XByteBuffer.toInt(b, offset)];
        offset += 4; // uniqueId length
        System.arraycopy(b, offset, data.uniqueId, 0, data.uniqueId.length);
        offset += data.uniqueId.length; // uniqueId data
        byte[] addr = new byte[XByteBuffer.toInt(b, offset)];
        offset += 4; // addr length
        System.arraycopy(b, offset, addr, 0, addr.length);
        data.setAddress(MemberImpl.getMember(addr));
        offset += addr.length; // addr data
        int xsize = XByteBuffer.toInt(b, offset);
        // data.message = new XByteBuffer(new byte[xsize],false);
        data.message = BufferPool.getBufferPool().getBuffer(xsize, false);
        offset += 4; // message length
        System.arraycopy(b, offset, data.message.getBytesDirect(), 0, xsize);
        data.message.append(b, offset, xsize);
        offset += xsize; // message data
        return data;
    }

    @Override
    public int hashCode() {
        return XByteBuffer.toInt(getUniqueId(), 0);
    }

    /**
     * Compares to ChannelData objects, only compares on getUniqueId().equals(o.getUniqueId())
     *
     * @param o Object
     *
     * @return boolean
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof ChannelData) {
            return Arrays.equals(getUniqueId(), ((ChannelData) o).getUniqueId());
        } else {
            return false;
        }
    }

    /**
     * Create a shallow clone, only the data gets recreated
     *
     * @return ClusterData
     */
    @Override
    public ChannelData clone() {
        ChannelData clone;
        try {
            clone = (ChannelData) super.clone();
        } catch (CloneNotSupportedException e) {
            // Cannot happen
            throw new AssertionError();
        }
        if (this.message != null) {
            clone.message = new XByteBuffer(this.message.getBytesDirect(), false);
        }
        return clone;
    }

    @Override
    public Object deepclone() {
        byte[] d = this.getDataPackage();
        return getDataFromPackage(d);
    }

    /**
     * Utility method, returns true if the options flag indicates that an ack is to be sent after the message has been
     * received and processed
     *
     * @param options int - the options for the message
     *
     * @return boolean
     *
     * @see org.apache.catalina.tribes.Channel#SEND_OPTIONS_USE_ACK
     * @see org.apache.catalina.tribes.Channel#SEND_OPTIONS_SYNCHRONIZED_ACK
     */
    public static boolean sendAckSync(int options) {
        return ((Channel.SEND_OPTIONS_USE_ACK & options) == Channel.SEND_OPTIONS_USE_ACK) &&
                ((Channel.SEND_OPTIONS_SYNCHRONIZED_ACK & options) == Channel.SEND_OPTIONS_SYNCHRONIZED_ACK);
    }


    /**
     * Utility method, returns true if the options flag indicates that an ack is to be sent after the message has been
     * received but not yet processed
     *
     * @param options int - the options for the message
     *
     * @return boolean
     *
     * @see org.apache.catalina.tribes.Channel#SEND_OPTIONS_USE_ACK
     * @see org.apache.catalina.tribes.Channel#SEND_OPTIONS_SYNCHRONIZED_ACK
     */
    public static boolean sendAckAsync(int options) {
        return ((Channel.SEND_OPTIONS_USE_ACK & options) == Channel.SEND_OPTIONS_USE_ACK) &&
                ((Channel.SEND_OPTIONS_SYNCHRONIZED_ACK & options) != Channel.SEND_OPTIONS_SYNCHRONIZED_ACK);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("ClusterData[src=");
        buf.append(getAddress()).append("; id=");
        buf.append(bToS(getUniqueId())).append("; sent=");
        buf.append(new Timestamp(this.getTimestamp()).toString()).append(']');
        return buf.toString();
    }

    public static String bToS(byte[] data) {
        StringBuilder buf = new StringBuilder(4 * 16);
        buf.append('{');
        for (int i = 0; data != null && i < data.length; i++) {
            buf.append(String.valueOf(data[i])).append(' ');
        }
        buf.append('}');
        return buf.toString();
    }


}
