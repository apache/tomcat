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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * When combined with the <code>TwoPhaseCommitInterceptor</code>, this interceptor must be declared after it in the
 * channel configuration, so that the two-phase commit interceptor confirms complete messages and never individual
 * fragments. All fragments of a message share its unique id, which the two-phase commit interceptor uses as its only
 * correlation key.
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
     * The maximum number of fragments that will be accepted for a single message. The fragment count is received as
     * part of the message data, so it is validated against this limit before it is used to allocate the fragment
     * storage. The limit is far above any realistic message: with the default maxSize it allows messages of close to
     * ten GB.
     */
    public static final int MAX_FRAGMENTS = 100000;

    /**
     * Map of fragment keys to their fragment collections for reassembly.
     */
    protected final Map<FragKey,FragCollection> fragpieces = new ConcurrentHashMap<>();
    private int maxSize = 1024 * 100;
    private long expire = 1000 * 60; // one minute expiration
    /**
     * Fragments are always deep cloned before storage.
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
        ChannelMessage complete = null;
        try {
            FragCollection coll = getFragCollection(key, msg);
            // The deep clone does not touch shared state, so it happens before the
            // monitor is entered. The fragment storage of a collection is shared
            // state. Synchronise on the collection so that adding a fragment and
            // checking for completion form a single atomic sequence even when
            // fragments arrive on multiple threads.
            ChannelMessage stored = (ChannelMessage) msg.deepclone();
            synchronized (coll) {
                coll.addMessage(stored);
                complete = coll.assembleIfComplete();
                if (complete != null) {
                    // Remove only this collection. A heartbeat expiry may already
                    // have removed it and a late fragment may have installed a new
                    // collection under the same key, which must not be evicted.
                    fragpieces.remove(key, coll);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Fragment metadata received as part of the message data failed validation
            log.warn(sm.getString("fragmentationInterceptor.fragment.discarded"), e);
            return;
        }

        if (complete != null) {
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
        if (count > MAX_FRAGMENTS) {
            // The receiver rejects fragment counts above MAX_FRAGMENTS, so fail here
            // rather than sending fragments that are guaranteed to be discarded.
            throw new ChannelException(sm.getString("fragmentationInterceptor.fragments.exceed-max",
                    Integer.toString(size), Integer.toString(count), Integer.toString(MAX_FRAGMENTS)));
        }
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
        if (maxSize < 1) {
            throw new IllegalArgumentException(sm.getString("fragmentationInterceptor.maxSize.tooSmall"));
        }
        this.maxSize = maxSize;
    }

    @Override
    public void setExpire(long expire) {
        if (expire < 1) {
            throw new IllegalArgumentException(sm.getString("fragmentationInterceptor.expire.tooSmall"));
        }
        this.expire = expire;
    }

    /**
     * Collection that holds the fragments of a message for reassembly.
     */
    public static class FragCollection {
        private final long received = System.currentTimeMillis();
        private final ChannelMessage msg;
        private final XByteBuffer[] frags;
        private boolean assembled = false;

        /**
         * Creates a new fragment collection for the given message.
         *
         * @param msg The channel message containing fragment metadata
         */
        public FragCollection(ChannelMessage msg) {
            // get the total messages
            int length = msg.getMessage().getLength();
            if (length < 4) {
                throw new IllegalArgumentException(
                        sm.getString("fragmentationInterceptor.fragment.too-short", Integer.toString(length)));
            }
            int count = XByteBuffer.toInt(msg.getMessage().getBytesDirect(), length - 4);
            if (count < 1 || count > MAX_FRAGMENTS) {
                throw new IllegalArgumentException(
                        sm.getString("fragmentationInterceptor.invalid.frag.count", Integer.toString(count)));
            }
            frags = new XByteBuffer[count];
            this.msg = msg;
        }

        /**
         * Adds a fragment message to this collection.
         *
         * @param msg The fragment message to add
         */
        public synchronized void addMessage(ChannelMessage msg) {
            // At least the fragment number and the fragment count must be present.
            // Check before trimming, trimming more bytes than are available throws
            // an ArrayIndexOutOfBoundsException.
            int length = msg.getMessage().getLength();
            if (length < 8) {
                throw new IllegalArgumentException(
                        sm.getString("fragmentationInterceptor.fragment.too-short", Integer.toString(length)));
            }
            // remove the total messages
            msg.getMessage().trim(4);
            // get the msg nr
            int nr = XByteBuffer.toInt(msg.getMessage().getBytesDirect(), msg.getMessage().getLength() - 4);
            // remove the msg nr
            msg.getMessage().trim(4);
            if (nr < 0 || nr >= frags.length) {
                throw new IllegalArgumentException(sm.getString("fragmentationInterceptor.invalid.frag.nr",
                        Integer.toString(nr), Integer.toString(frags.length)));
            }
            frags[nr] = msg.getMessage();

        }

        /**
         * Checks if all fragments have been received.
         *
         * @return {@code true} if all fragments are present
         */
        public synchronized boolean complete() {
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
        public synchronized ChannelMessage assemble() {
            if (!complete()) {
                throw new IllegalStateException(sm.getString("fragmentationInterceptor.fragments.missing"));
            }
            long buffersize = 0;
            for (XByteBuffer frag : frags) {
                buffersize += frag.getLength();
            }
            if (buffersize > Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        sm.getString("fragmentationInterceptor.fragments.too-large", Long.toString(buffersize)));
            }
            XByteBuffer buf = new XByteBuffer((int) buffersize, false);
            msg.setMessage(buf);
            for (XByteBuffer frag : frags) {
                msg.getMessage().append(frag.getBytesDirect(), 0, frag.getLength());
            }
            return msg;
        }

        /**
         * Assembles the complete message if all fragments have been received and this
         * collection has not been assembled before. The one-shot guarantee makes the
         * completion of a message delivered exactly once, even when the last fragments
         * are processed concurrently on multiple threads.
         *
         * @return The assembled channel message, or <code>null</code> if the message is
         *         incomplete or was assembled by an earlier call
         */
        public synchronized ChannelMessage assembleIfComplete() {
            if (!assembled && complete()) {
                assembled = true;
                return assemble();
            }
            return null;
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
            return Arrays.hashCode(uniqueId);
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