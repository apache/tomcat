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
package org.apache.catalina.tribes.group.interceptors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * The fragmentation interceptor splits up large messages into smaller messages and assembles them on the other end.
 * This is very useful when you don't want large messages hogging the sending sockets and smaller messages can make it
 * through. <br>
 * <b>Configuration Options</b><br>
 * FragmentationInterceptor.expire=&lt;milliseconds&gt; - how long do we keep the fragments in memory and wait for the
 * rest to arrive <b>default=60,000ms -&gt; 60seconds</b> This setting is useful to avoid OutOfMemoryErrors<br>
 * FragmentationInterceptor.maxSize=&lt;max message size&gt; - message size in bytes <b>default=1024*100 (around a tenth
 * of a MB)</b><br>
 */
public class FragmentationInterceptor extends ChannelInterceptorBase implements FragmentationInterceptorMBean {
    /**
     * Creates a new FragmentationInterceptor instance.
     */
    public FragmentationInterceptor() {
        // Default constructor
    }

    private static final Log log = LogFactory.getLog(FragmentationInterceptor.class);
    /**
     * String manager for internationalization support.
     */
    protected static final StringManager sm = StringManager.getManager(FragmentationInterceptor.class);

    /**
     * Map of fragment keys to their fragment collections for reassembly.
     */
    protected final HashMap<FragKey,FragCollection> fragpieces = new HashMap<>();
    private int maxSize = 1024 * 100;
    private long expire = 1000 * 60; // one minute expiration
    /**
     * Flag indicating whether deep cloning is enabled for fragments.
     */
    protected final boolean deepclone = true;


    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        int size = msg.getMessage().getLength();
        boolean frag = (size > maxSize) && okToProcess(msg.getOptions());
        if (frag) {
            frag(destination, msg, payload);
        } else {
            msg.getMessage().append(frag);
            super.sendMessage(destination, msg, payload);
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        boolean isFrag = XByteBuffer.toBoolean(msg.getMessage().getBytesDirect(), msg.getMessage().getLength() - 1);
        msg.getMessage().trim(1);
        if (isFrag) {
            defrag(msg);
        } else {
            super.messageReceived(msg);
        }
    }


    /**
     * Gets the fragment collection for the given key, creating one if it does not exist.
     *
     * @param key The fragment key
     * @param msg The channel message used to initialize a new collection if needed
     *
     * @return The fragment collection for the given key
     */
    public FragCollection getFragCollection(FragKey key, ChannelMessage msg) {
        FragCollection coll = fragpieces.get(key);
        if (coll == null) {
            synchronized (fragpieces) {
                coll = fragpieces.get(key);
                if (coll == null) {
                    coll = new FragCollection(msg);
                    fragpieces.put(key, coll);
                }
            }
        }
        return coll;
    }

    /**
     * Removes the fragment collection for the given key.
     *
     * @param key The fragment key to remove
     */
    public void removeFragCollection(FragKey key) {
        fragpieces.remove(key);
    }

    /**
     * Reassembles a fragmented message from its parts.
     *
     * @param msg The channel message fragment to process
     */
    public void defrag(ChannelMessage msg) {
        FragKey key = new FragKey(msg.getUniqueId());
        FragCollection coll = getFragCollection(key, msg);
        coll.addMessage((ChannelMessage) msg.deepclone());

        if (coll.complete()) {
            removeFragCollection(key);
            ChannelMessage complete = coll.assemble();
            super.messageReceived(complete);

        }
    }

    /**
     * Fragments a large message into smaller pieces and sends them.
     *
     * @param destination The destination members
     * @param msg The channel message to fragment
     * @param payload The interceptor payload
     *
     * @throws ChannelException if an error occurs during fragmentation
     */
    public void frag(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        int size = msg.getMessage().getLength();

        int count = ((size / maxSize) + (size % maxSize == 0 ? 0 : 1));
        ChannelMessage[] messages = new ChannelMessage[count];
        int remaining = size;
        for (int i = 0; i < count; i++) {
            ChannelMessage tmp = (ChannelMessage) msg.clone();
            int offset = (i * maxSize);
            int length = Math.min(remaining, maxSize);
            tmp.getMessage().clear();
            tmp.getMessage().append(msg.getMessage().getBytesDirect(), offset, length);
            // add the msg nr
            // tmp.getMessage().append(XByteBuffer.toBytes(i),0,4);
            tmp.getMessage().append(i);
            // add the total nr of messages
            // tmp.getMessage().append(XByteBuffer.toBytes(count),0,4);
            tmp.getMessage().append(count);
            // add true as the frag flag
            // byte[] flag = XByteBuffer.toBytes(true);
            // tmp.getMessage().append(flag,0,flag.length);
            tmp.getMessage().append(true);
            messages[i] = tmp;
            remaining -= length;

        }
        for (ChannelMessage message : messages) {
            super.sendMessage(destination, message, payload);
        }
    }

    @Override
    public void heartbeat() {
        try {
            Set<FragKey> set = fragpieces.keySet();
            Object[] keys = set.toArray();
            for (Object o : keys) {
                FragKey key = (FragKey) o;
                if (key != null && key.expired(getExpire())) {
                    removeFragCollection(key);
                }
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(sm.getString("fragmentationInterceptor.heartbeat.failed"), e);
            }
        }
        super.heartbeat();
    }

    @Override
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public long getExpire() {
        return expire;
    }

    @Override
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void setExpire(long expire) {
        this.expire = expire;
    }

    /**
     * Collection that holds the fragments of a message for reassembly.
     */
    public static class FragCollection {
        private final long received = System.currentTimeMillis();
        private final ChannelMessage msg;
        private final XByteBuffer[] frags;

        /**
         * Creates a new fragment collection for the given message.
         *
         * @param msg The channel message containing fragment metadata
         */
        public FragCollection(ChannelMessage msg) {
            // get the total messages
            int count = XByteBuffer.toInt(msg.getMessage().getBytesDirect(), msg.getMessage().getLength() - 4);
            frags = new XByteBuffer[count];
            this.msg = msg;
        }

        /**
         * Adds a fragment message to this collection.
         *
         * @param msg The fragment message to add
         */
        public void addMessage(ChannelMessage msg) {
            // remove the total messages
            msg.getMessage().trim(4);
            // get the msg nr
            int nr = XByteBuffer.toInt(msg.getMessage().getBytesDirect(), msg.getMessage().getLength() - 4);
            // remove the msg nr
            msg.getMessage().trim(4);
            frags[nr] = msg.getMessage();

        }

        /**
         * Checks if all fragments have been received.
         *
         * @return {@code true} if all fragments are present
         */
        public boolean complete() {
            boolean result = true;
            for (int i = 0; (i < frags.length) && (result); i++) {
                result = (frags[i] != null);
            }
            return result;
        }

        /**
         * Assembles all fragments into a single complete message.
         *
         * @return The assembled channel message
         *
         * @throws IllegalStateException if not all fragments have been received
         */
        public ChannelMessage assemble() {
            if (!complete()) {
                throw new IllegalStateException(sm.getString("fragmentationInterceptor.fragments.missing"));
            }
            int buffersize = 0;
            for (XByteBuffer frag : frags) {
                buffersize += frag.getLength();
            }
            XByteBuffer buf = new XByteBuffer(buffersize, false);
            msg.setMessage(buf);
            for (XByteBuffer frag : frags) {
                msg.getMessage().append(frag.getBytesDirect(), 0, frag.getLength());
            }
            return msg;
        }

        /**
         * Checks if this fragment collection has expired.
         *
         * @param expire The expiration time in milliseconds
         *
         * @return {@code true} if the collection has expired
         */
        public boolean expired(long expire) {
            return (System.currentTimeMillis() - received) > expire;
        }
    }

    /**
     * Key used to identify a set of fragments belonging to the same original message.
     */
    public static class FragKey {
        private final byte[] uniqueId;
        private final long received = System.currentTimeMillis();

        /**
         * Creates a new fragment key with the given unique identifier.
         *
         * @param id The unique identifier for the message
         */
        public FragKey(byte[] id) {
            this.uniqueId = id;
        }

        @Override
        public int hashCode() {
            return XByteBuffer.toInt(uniqueId, 0);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof FragKey) {
                return Arrays.equals(uniqueId, ((FragKey) o).uniqueId);
            } else {
                return false;
            }

        }

        /**
         * Checks if this fragment key has expired.
         *
         * @param expire The expiration time in milliseconds
         *
         * @return {@code true} if the key has expired
         */
        public boolean expired(long expire) {
            return (System.currentTimeMillis() - received) > expire;
        }

    }

}