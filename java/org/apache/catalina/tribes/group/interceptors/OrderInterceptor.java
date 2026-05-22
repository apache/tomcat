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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;


/**
 * The order interceptor guarantees that messages are received in the same order they were sent. This interceptor works
 * best with the ack=true setting. <br>
 * There is no point in using this with the replicationMode="fastasynchqueue" as this mode guarantees ordering.<BR>
 * If you are using the mode ack=false replicationMode=pooled, and have a lot of concurrent threads, this interceptor
 * can really slow you down, as many messages will be completely out of order and the queue might become rather large.
 * If this is the case, then you might want to set the value OrderInterceptor.maxQueue = 25 (meaning that we will never
 * keep more than 25 messages in our queue) <br>
 * <b>Configuration Options</b><br>
 * OrderInterceptor.expire=&lt;milliseconds&gt; - if a message arrives out of order, how long before we act on it
 * <b>default=3000ms</b><br>
 * OrderInterceptor.maxQueue=&lt;max queue size&gt; - how much can the queue grow to ensure ordering. This setting is
 * useful to avoid OutOfMemoryErrors<b>default=Integer.MAX_VALUE</b><br>
 * OrderInterceptor.forwardExpired=&lt;boolean&gt; - this flag tells the interceptor what to do when a message has
 * expired or the queue has grown larger than the maxQueue value. true means that the message is sent up the stack to
 * the receiver that will receive and out of order message false means, forget the message and reset the message
 * counter. <b>default=true</b>
 */
public class OrderInterceptor extends ChannelInterceptorBase {
    /**
     * Constructs an OrderInterceptor.
     */
    public OrderInterceptor() {
    }

    /**
     * String manager for internationalization.
     */
    protected static final StringManager sm = StringManager.getManager(OrderInterceptor.class);
    private final Map<Member,Counter> outcounter = new HashMap<>();
    private final Map<Member,Counter> incounter = new HashMap<>();
    private final Map<Member,MessageOrder> incoming = new HashMap<>();
    private long expire = 3000;
    private boolean forwardExpired = true;
    private int maxQueue = Integer.MAX_VALUE;

    final ReentrantReadWriteLock inLock = new ReentrantReadWriteLock(true);
    final ReentrantReadWriteLock outLock = new ReentrantReadWriteLock(true);

    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        if (!okToProcess(msg.getOptions())) {
            super.sendMessage(destination, msg, payload);
            return;
        }
        ChannelException cx = null;
        for (Member member : destination) {
            try {
                int nr;
                outLock.writeLock().lock();
                try {
                    nr = incCounter(member);
                } finally {
                    outLock.writeLock().unlock();
                }
                // reduce byte copy
                msg.getMessage().append(nr);
                try {
                    getNext().sendMessage(new Member[] { member }, msg, payload);
                } finally {
                    msg.getMessage().trim(4);
                }
            } catch (ChannelException x) {
                if (cx == null) {
                    cx = x;
                }
                cx.addFaultyMember(x.getFaultyMembers());
            }
        } // for
        if (cx != null) {
            throw cx;
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if (!okToProcess(msg.getOptions())) {
            super.messageReceived(msg);
            return;
        }
        int msgnr = XByteBuffer.toInt(msg.getMessage().getBytesDirect(), msg.getMessage().getLength() - 4);
        msg.getMessage().trim(4);
        MessageOrder order = new MessageOrder(msgnr, (ChannelMessage) msg.deepclone());
        inLock.writeLock().lock();
        try {
            if (processIncoming(order)) {
                processLeftOvers(msg.getAddress(), false);
            }
        } finally {
            inLock.writeLock().unlock();
        }
    }

    /**
     * Processes leftover messages for a member.
     *
     * @param member The member
     * @param force Whether to force processing
     */
    protected void processLeftOvers(Member member, boolean force) {
        MessageOrder tmp = incoming.get(member);
        if (force) {
            Counter cnt = getInCounter(member);
            cnt.setCounter(Integer.MAX_VALUE);
        }
        if (tmp != null) {
            processIncoming(tmp);
        }
    }

    /**
     * Processes incoming messages, handling expiration and ordering.
     *
     * @param order The message order to process
     * @return true if a message expired and was processed
     */
    protected boolean processIncoming(MessageOrder order) {
        boolean result = false;
        Member member = order.getMessage().getAddress();
        Counter cnt = getInCounter(member);

        MessageOrder tmp = incoming.get(member);
        if (tmp != null) {
            order = MessageOrder.add(tmp, order);
        }


        while ((order != null) && (order.getMsgNr() <= cnt.getCounter())) {
            // we are right on target. process orders
            if (order.getMsgNr() == cnt.getCounter()) {
                cnt.inc();
            } else if (order.getMsgNr() > cnt.getCounter()) {
                cnt.setCounter(order.getMsgNr());
            }
            super.messageReceived(order.getMessage());
            order.setMessage(null);
            order = order.next;
        }
        MessageOrder head = order;
        MessageOrder prev = null;
        tmp = order;
        // flag to empty out the queue when it larger than maxQueue
        boolean empty = order != null && order.getCount() >= maxQueue;
        while (tmp != null) {
            // process expired messages or empty out the queue
            if (tmp.isExpired(expire) || empty) {
                // reset the head
                if (tmp == head) {
                    head = tmp.next;
                }
                cnt.setCounter(tmp.getMsgNr() + 1);
                if (getForwardExpired()) {
                    super.messageReceived(tmp.getMessage());
                }
                tmp.setMessage(null);
                tmp = tmp.next;
                if (prev != null) {
                    prev.next = tmp;
                }
                result = true;
            } else {
                prev = tmp;
                tmp = tmp.next;
            }
        }
        if (head == null) {
            incoming.remove(member);
        } else {
            incoming.put(member, head);
        }
        return result;
    }

    @Override
    public void memberAdded(Member member) {
        // notify upwards
        super.memberAdded(member);
    }

    @Override
    public void memberDisappeared(Member member) {
        // reset counters - lock free
        incounter.remove(member);
        outcounter.remove(member);
        // clear the remaining queue
        processLeftOvers(member, true);
        // notify upwards
        super.memberDisappeared(member);
    }

    /**
     * Increments the outgoing message counter for a member.
     *
     * @param mbr The member
     * @return The new counter value
     */
    protected int incCounter(Member mbr) {
        Counter cnt = getOutCounter(mbr);
        return cnt.inc();
    }

    /**
     * Returns the incoming counter for a member, creating one if needed.
     *
     * @param mbr The member
     * @return The counter
     */
    protected Counter getInCounter(Member mbr) {
        Counter cnt = incounter.get(mbr);
        if (cnt == null) {
            cnt = new Counter();
            cnt.inc(); // always start at 1 for incoming
            incounter.put(mbr, cnt);
        }
        return cnt;
    }

    /**
     * Returns the outgoing counter for a member, creating one if needed.
     *
     * @param mbr The member
     * @return The counter
     */
    protected Counter getOutCounter(Member mbr) {
        Counter cnt = outcounter.get(mbr);
        if (cnt == null) {
            cnt = new Counter();
            outcounter.put(mbr, cnt);
        }
        return cnt;
    }

    /**
     * Counter for tracking message sequence numbers.
     */
    protected static class Counter {
        /**
         * Constructs a Counter with initial value 0.
         */
        public Counter() {
        }

        private final AtomicInteger value = new AtomicInteger(0);

        /**
         * Returns the current counter value.
         *
         * @return The counter value
         */
        public int getCounter() {
            return value.get();
        }

        /**
         * Sets the counter value.
         *
         * @param counter The new counter value
         */
        public void setCounter(int counter) {
            this.value.set(counter);
        }

        /**
         * Increments the counter and returns the new value.
         *
         * @return The new counter value
         */
        public int inc() {
            return value.addAndGet(1);
        }
    }

    /**
     * Represents a message in the ordering queue.
     */
    protected static class MessageOrder {
        private final long received = System.currentTimeMillis();
        private MessageOrder next;
        private final int msgNr;
        private ChannelMessage msg;

        /**
         * Constructs a MessageOrder with the given message number and message.
         *
         * @param msgNr The message number
         * @param msg The channel message
         */
        public MessageOrder(int msgNr, ChannelMessage msg) {
            this.msgNr = msgNr;
            this.msg = msg;
        }

        /**
         * Checks if this message has expired.
         *
         * @param expireTime The expiration time in milliseconds
         * @return true if the message has expired
         */
        public boolean isExpired(long expireTime) {
            return (System.currentTimeMillis() - received) > expireTime;
        }

        /**
         * Returns the channel message.
         *
         * @return The channel message
         */
        public ChannelMessage getMessage() {
            return msg;
        }

        /**
         * Sets the channel message.
         *
         * @param msg The channel message
         */
        public void setMessage(ChannelMessage msg) {
            this.msg = msg;
        }

        /**
         * Sets the next message in the order.
         *
         * @param order The next message order
         */
        public void setNext(MessageOrder order) {
            this.next = order;
        }

        /**
         * Returns the next message in the order.
         *
         * @return The next message order
         */
        public MessageOrder getNext() {
            return next;
        }

        /**
         * Returns the count of messages in this chain.
         *
         * @return The message count
         */
        public int getCount() {
            int counter = 1;
            MessageOrder tmp = next;
            while (tmp != null) {
                counter++;
                tmp = tmp.next;
            }
            return counter;
        }

        /**
         * Adds a message order to the end of the chain.
         *
         * @param head The head of the chain
         * @param add The message order to add
         * @return The head of the updated chain
         */
        @SuppressWarnings("null") // prev cannot be null
        public static MessageOrder add(MessageOrder head, MessageOrder add) {
            if (head == null) {
                return add;
            }
            if (add == null) {
                return head;
            }
            if (head == add) {
                return add;
            }

            if (head.getMsgNr() > add.getMsgNr()) {
                add.next = head;
                return add;
            }

            MessageOrder iter = head;
            MessageOrder prev = null;
            while (iter.getMsgNr() < add.getMsgNr() && (iter.next != null)) {
                prev = iter;
                iter = iter.next;
            }
            if (iter.getMsgNr() < add.getMsgNr()) {
                // add after
                add.next = iter.next;
                iter.next = add;
            } else if (iter.getMsgNr() > add.getMsgNr()) {
                // add before
                prev.next = add; // prev cannot be null here, warning suppressed
                add.next = iter;
            } else {
                throw new ArithmeticException(sm.getString("orderInterceptor.messageAdded.sameCounter"));
            }

            return head;
        }

        /**
         * Returns the message number.
         *
         * @return The message number
         */
        public int getMsgNr() {
            return msgNr;
        }


    }

    /**
     * Sets the message expiration time in milliseconds.
     *
     * @param expire The expiration time
     */
    public void setExpire(long expire) {
        this.expire = expire;
    }

    /**
     * Sets whether expired messages should be forwarded.
     *
     * @param forwardExpired true to forward expired messages
     */
    public void setForwardExpired(boolean forwardExpired) {
        this.forwardExpired = forwardExpired;
    }

    /**
     * Sets the maximum queue size.
     *
     * @param maxQueue The maximum queue size
     */
    public void setMaxQueue(int maxQueue) {
        this.maxQueue = maxQueue;
    }

    /**
     * Returns the message expiration time in milliseconds.
     *
     * @return The expiration time
     */
    public long getExpire() {
        return expire;
    }

    /**
     * Returns whether expired messages are forwarded.
     *
     * @return true if expired messages are forwarded
     */
    public boolean getForwardExpired() {
        return forwardExpired;
    }

    /**
     * Returns the maximum queue size.
     *
     * @return The maximum queue size
     */
    public int getMaxQueue() {
        return maxQueue;
    }

}
