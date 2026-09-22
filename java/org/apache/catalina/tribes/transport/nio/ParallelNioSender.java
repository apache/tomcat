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
package org.apache.catalina.tribes.transport.nio;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.transport.AbstractSender;
import org.apache.catalina.tribes.transport.MultiPointSender;
import org.apache.catalina.tribes.transport.SenderState;
import org.apache.catalina.tribes.util.Logs;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A sender that sends messages to multiple members in parallel using NIO.
 */
public class ParallelNioSender extends AbstractSender implements MultiPointSender {

    private static final Log log = LogFactory.getLog(ParallelNioSender.class);
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ParallelNioSender.class);

    private static final Cleaner cleaner = Cleaner.create();

    private final InternalState state;

    private ExecutorService sendSecureExecutor;

    /**
     * The timeout in milliseconds for the selector select operation.
     */
    protected final long selectTimeout = 5000; // default 5 seconds

    /**
     * Construct a new {@code ParallelNioSender}.
     *
     * @throws IOException If an I/O error occurs during initialization
     */
    public ParallelNioSender() throws IOException {
        state = new InternalState(Selector.open());
        cleaner.register(this, state);
        setConnected(true);
    }


    @Override
    public synchronized void sendMessage(Member[] destination, ChannelMessage msg) throws ChannelException {
        long start = System.currentTimeMillis();
        if ((msg.getOptions() & Channel.SEND_OPTIONS_SECURE) != 0) {
            sendSecure(destination, msg);
            return;
        }
        this.setUdpBased((msg.getOptions() & Channel.SEND_OPTIONS_UDP) == Channel.SEND_OPTIONS_UDP);
        byte[] data = XByteBuffer.createDataPackage((ChannelData) msg);
        NioSender[] senders = setupForSend(destination);
        connect(senders);
        setData(senders, data);

        int remaining = senders.length;
        ChannelException cx = null;
        try {
            // loop until complete, an error happens, or we timeout
            long delta = System.currentTimeMillis() - start;
            boolean waitForAck = (Channel.SEND_OPTIONS_USE_ACK & msg.getOptions()) == Channel.SEND_OPTIONS_USE_ACK;
            while ((remaining > 0) && (delta < getTimeout())) {
                try {
                    SendResult result = doLoop(selectTimeout, getMaxRetryAttempts(), waitForAck, msg);
                    remaining -= result.getCompleted();
                    if (result.getFailed() != null) {
                        remaining -= result.getFailed().getFaultyMembers().length;
                        if (cx == null) {
                            cx = result.getFailed();
                        } else {
                            cx.addFaultyMember(result.getFailed().getFaultyMembers());
                        }
                    }
                } catch (Exception e) {
                    if (log.isTraceEnabled()) {
                        log.trace("Error sending message", e);
                    }
                    if (cx == null) {
                        if (e instanceof ChannelException) {
                            cx = (ChannelException) e;
                        } else {
                            cx = new ChannelException(sm.getString("parallelNioSender.send.failed"), e);
                        }
                    }
                    for (NioSender sender : senders) {
                        if (!sender.isComplete()) {
                            cx.addFaultyMember(sender.getDestination(), e);
                        }
                    }
                    throw cx;
                }
                delta = System.currentTimeMillis() - start;
            }
            if (remaining > 0) {
                // timeout has occurred
                ChannelException cxtimeout = new ChannelException(
                        sm.getString("parallelNioSender.operation.timedout", Long.toString(getTimeout())));
                if (cx == null) {
                    cx = new ChannelException(
                            sm.getString("parallelNioSender.operation.timedout", Long.toString(getTimeout())));
                }
                for (NioSender sender : senders) {
                    if (!sender.isComplete()) {
                        cx.addFaultyMember(sender.getDestination(), cxtimeout);
                    }
                }
                throw cx;
            } else if (cx != null) {
                // there was an error
                throw cx;
            }
        } catch (Exception e) {
            try {
                this.disconnect();
            } catch (Exception ignore) {
                // Ignore
            }
            if (e instanceof ChannelException) {
                throw (ChannelException) e;
            } else {
                throw new ChannelException(e);
            }
        }

    }

    private void sendSecure(Member[] destination, ChannelMessage msg) throws ChannelException {
        if (getSslContext() == null) {
            throw new ChannelException(sm.getString("parallelNioSender.tlsUnavailable"));
        }
        byte[] data = XByteBuffer.createDataPackage((ChannelData) msg);
        boolean waitForAck = (msg.getOptions() & Channel.SEND_OPTIONS_USE_ACK) != 0;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(getTimeout());
        ChannelException failure = null;
        if (destination.length == 0) {
            return;
        }
        if (sendSecureExecutor == null) {
            sendSecureExecutor = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(), 60L,
                    TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                        Thread thread = new Thread(runnable, "Tribes-TLS-Sender");
                        return thread;
                    });
        }
        List<Map.Entry<Member,Future<Void>>> sends = new ArrayList<>(destination.length);
        for (Member member : destination) {
            Future<Void> send = sendSecureExecutor.submit(() -> {
                sendSecure(member, data, waitForAck, deadline);
                return null;
            });
            sends.add(Map.entry(member, send));
        }
        for (Map.Entry<Member,Future<Void>> entry : sends) {
            try {
                long remaining = deadline - System.nanoTime();
                entry.getValue().get(Math.max(0, remaining), TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                if (failure == null) {
                    failure = new ChannelException(sm.getString("parallelNioSender.send.failed"));
                }
                Throwable cause = e.getCause();
                failure.addFaultyMember(entry.getKey(),
                        cause instanceof Exception ? (Exception) cause : new IOException(cause));
            } catch (TimeoutException e) {
                entry.getValue().cancel(true);
                if (failure == null) {
                    failure = new ChannelException(
                            sm.getString("parallelNioSender.operation.timedout", Long.toString(getTimeout())));
                }
                failure.addFaultyMember(entry.getKey(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChannelException(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void sendSecure(Member member, byte[] data, boolean waitForAck, long deadline) throws Exception {
        if (member.getSecurePort() < 0) {
            throw new IOException(sm.getString("parallelNioSender.securePortUnavailable"));
        }
        Exception failure = null;
        int maxRetryAttempts = Math.max(0, getMaxRetryAttempts());
        for (int attempt = 0; attempt <= maxRetryAttempts; attempt++) {
            try {
                int timeout = remainingTimeout(deadline);
                try (SocketChannel socket = SocketChannel.open()) {
                    socket.configureBlocking(true);
                    socket.socket().connect(new java.net.InetSocketAddress(InetAddress.getByAddress(member.getHost()),
                            member.getSecurePort()), timeout);
                    socket.socket().setSoTimeout(remainingTimeout(deadline));
                    try (TlsChannel tls = new TlsChannel(socket.socket(), getSslContext().createClientEngine())) {
                        tls.write(java.nio.ByteBuffer.wrap(data));
                        if (waitForAck) {
                            socket.socket().setSoTimeout(remainingTimeout(deadline));
                            readSecureAck(tls, deadline);
                        }
                    }
                }
                return;
            } catch (Exception e) {
                failure = e;
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void readSecureAck(TlsChannel tls, long deadline) throws IOException {
        XByteBuffer acknowledgements = new XByteBuffer(getRxBufSize(), true);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(getRxBufSize());
        while (!acknowledgements.doesPackageExist()) {
            if (System.nanoTime() >= deadline) {
                throw new IOException(
                        sm.getString("parallelNioSender.operation.timedout", Long.toString(getTimeout())));
            }
            int read = tls.read(buffer);
            if (read < 0) {
                throw new IOException(sm.getString("nioSender.unable.receive.ack"));
            }
            buffer.flip();
            acknowledgements.append(buffer, read);
            buffer.clear();
        }
        byte[] ack = acknowledgements.extractDataPackage(true).getBytes();
        if (java.util.Arrays.equals(ack, org.apache.catalina.tribes.transport.Constants.ACK_DATA)) {
            return;
        }
        if (java.util.Arrays.equals(ack, org.apache.catalina.tribes.transport.Constants.FAIL_ACK_DATA)) {
            if (getThrowOnFailedAck()) {
                throw new RemoteProcessException(sm.getString("nioSender.receive.failedAck"));
            }
            return;
        }
        throw new IOException(sm.getString("parallelNioSender.invalidAck"));
    }

    private int remainingTimeout(long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new IOException(sm.getString("parallelNioSender.operation.timedout", Long.toString(getTimeout())));
        }
        long milliseconds = TimeUnit.NANOSECONDS.toMillis(remaining);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, milliseconds));
    }

    private SendResult doLoop(long selectTimeOut, int maxAttempts, boolean waitForAck, ChannelMessage msg)
            throws ChannelException {
        SendResult result = new SendResult();
        int selectedKeys;
        try {
            selectedKeys = state.selector.select(selectTimeOut);
        } catch (IOException ioe) {
            throw new ChannelException(sm.getString("parallelNioSender.send.failed"), ioe);
        }

        if (selectedKeys == 0) {
            return result;
        }

        Iterator<SelectionKey> it = state.selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey sk = it.next();
            it.remove();
            int readyOps = sk.readyOps();
            sk.interestOps(sk.interestOps() & ~readyOps);
            NioSender sender = (NioSender) sk.attachment();
            try {
                if (sender.process(sk, waitForAck)) {
                    sender.setComplete(true);
                    result.complete(sender);
                    if (Logs.MESSAGES.isTraceEnabled()) {
                        Logs.MESSAGES.trace("ParallelNioSender - Sent msg:" + new UniqueId(msg.getUniqueId()) + " at " +
                                new java.sql.Timestamp(System.currentTimeMillis()) + " to " +
                                sender.getDestination().getName());
                    }
                    SenderState.getSenderState(sender.getDestination()).setReady();
                } // end if
            } catch (Exception e) {
                if (log.isTraceEnabled()) {
                    log.trace("Error while processing send to " + sender.getDestination().getName(), e);
                }
                SenderState state = SenderState.getSenderState(sender.getDestination());
                int attempt = sender.getAttempt() + 1;
                boolean retry = (attempt <= maxAttempts && maxAttempts > 0);
                synchronized (state) {

                    // sk.cancel();
                    if (state.isSuspect()) {
                        state.setFailing();
                    }
                    if (state.isReady()) {
                        state.setSuspect();
                        if (retry) {
                            log.warn(sm.getString("parallelNioSender.send.fail.retrying",
                                    sender.getDestination().getName()));
                        } else {
                            log.warn(sm.getString("parallelNioSender.send.fail", sender.getDestination().getName()), e);
                        }
                    }
                }
                if (!isConnected()) {
                    log.warn(sm.getString("parallelNioSender.sender.disconnected.notRetry",
                            sender.getDestination().getName()));
                    ChannelException cx =
                            new ChannelException(sm.getString("parallelNioSender.sender.disconnected.sendFailed"), e);
                    cx.addFaultyMember(sender.getDestination(), e);
                    result.failed(cx);
                    break;
                }

                byte[] data = sender.getMessage();
                if (retry) {
                    try {
                        sender.disconnect();
                        sender.connect();
                        sender.setAttempt(attempt);
                        sender.setMessage(data);
                    } catch (Exception ignore) {
                        state.setFailing();
                    }
                } else {
                    ChannelException cx = new ChannelException(sm.getString("parallelNioSender.sendFailed.attempt",
                            Integer.toString(sender.getAttempt()), Integer.toString(maxAttempts)), e);
                    cx.addFaultyMember(sender.getDestination(), e);
                    result.failed(cx);
                } // end if
            }
        }
        return result;
    }

    private static class SendResult {
        private final List<NioSender> completeSenders = new ArrayList<>();
        private ChannelException exception = null;

        private void complete(NioSender sender) {
            if (!completeSenders.contains(sender)) {
                completeSenders.add(sender);
            }
        }

        private int getCompleted() {
            return completeSenders.size();
        }

        private void failed(ChannelException cx) {
            if (exception == null) {
                exception = cx;
            }
            exception.addFaultyMember(cx.getFaultyMembers());
        }

        private ChannelException getFailed() {
            return exception;
        }
    }

    private void connect(NioSender[] senders) throws ChannelException {
        ChannelException x = null;
        for (NioSender sender : senders) {
            try {
                sender.connect();
            } catch (IOException io) {
                if (x == null) {
                    x = new ChannelException(io);
                }
                x.addFaultyMember(sender.getDestination(), io);
            }
        }
        if (x != null) {
            throw x;
        }
    }

    private void setData(NioSender[] senders, byte[] data) throws ChannelException {
        ChannelException x = null;
        for (NioSender sender : senders) {
            try {
                sender.setMessage(data);
            } catch (IOException io) {
                if (x == null) {
                    x = new ChannelException(io);
                }
                x.addFaultyMember(sender.getDestination(), io);
            }
        }
        if (x != null) {
            throw x;
        }
    }


    private NioSender[] setupForSend(Member[] destination) throws ChannelException {
        ChannelException cx = null;
        NioSender[] result = new NioSender[destination.length];
        for (int i = 0; i < destination.length; i++) {
            NioSender sender = state.nioSenders.get(destination[i]);
            try {

                if (sender == null) {
                    sender = new NioSender();
                    transferProperties(this, sender);
                    state.nioSenders.put(destination[i], sender);
                }
                sender.reset();
                sender.setDestination(destination[i]);
                sender.setSelector(state.selector);
                sender.setUdpBased(isUdpBased());
                result[i] = sender;
            } catch (UnknownHostException x) {
                if (cx == null) {
                    cx = new ChannelException(sm.getString("parallelNioSender.unable.setup.NioSender"), x);
                }
                cx.addFaultyMember(destination[i], x);
            }
        }
        if (cx != null) {
            throw cx;
        } else {
            return result;
        }
    }

    @Override
    public void connect() {
        // do nothing, we connect on demand
        setConnected(true);
    }


    private synchronized void close() throws ChannelException {
        ChannelException x = null;
        Iterator<Map.Entry<Member,NioSender>> iter = state.nioSenders.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Member,NioSender> entry = iter.next();
            try {
                entry.getValue().disconnect();
            } catch (Exception e) {
                if (x == null) {
                    x = new ChannelException(e);
                }
                x.addFaultyMember(entry.getKey(), e);
            }
            iter.remove();
        }
        if (x != null) {
            throw x;
        }
    }

    @Override
    public void add(Member member) {
        // NOOP
    }

    @Override
    public void remove(Member member) {
        // disconnect senders
        NioSender sender = state.nioSenders.remove(member);
        if (sender != null) {
            sender.disconnect();
        }
    }


    @Override
    public synchronized void disconnect() {
        setConnected(false);
        try {
            close();
        } catch (Exception ignore) {
            // Ignore
        }
        if (sendSecureExecutor != null) {
            sendSecureExecutor.shutdown();
            try {
                // Should stop a lot faster than this as all the sockets have been closed.
                if (!sendSecureExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.warn(sm.getString("parallelNioSender.disconnect.executor.timeout"));
                }
            } catch (InterruptedException e) {
                log.warn(sm.getString("parallelNioSender.disconnect.executor.interrupted"));
            } finally {
                sendSecureExecutor = null;
            }
        }
    }

    @Override
    public synchronized boolean keepalive() {
        boolean result = false;
        for (Iterator<Entry<Member,NioSender>> i = state.nioSenders.entrySet().iterator(); i.hasNext();) {
            Map.Entry<Member,NioSender> entry = i.next();
            NioSender sender = entry.getValue();
            if (sender.keepalive()) {
                // nioSenders.remove(entry.getKey());
                i.remove();
                result = true;
            } else {
                try {
                    sender.read();
                } catch (IOException ioe) {
                    sender.disconnect();
                    sender.reset();
                    // nioSenders.remove(entry.getKey());
                    i.remove();
                    result = true;
                } catch (Exception e) {
                    log.warn(sm.getString("parallelNioSender.error.keepalive", sender), e);
                }
            }
        }
        // clean up any cancelled keys
        if (result) {
            try {
                state.selector.selectNow();
            } catch (Exception ignore) {
                // Ignore
            }
        }
        return result;
    }


    private static class InternalState implements Runnable {

        private final Selector selector;
        private final HashMap<Member,NioSender> nioSenders = new HashMap<>();

        private InternalState(Selector selector) {
            this.selector = selector;
        }

        @Override
        public void run() {
            Iterator<NioSender> iter = nioSenders.values().iterator();
            while (iter.hasNext()) {
                NioSender nioSender = iter.next();
                try {
                    nioSender.disconnect();
                } catch (Exception ignore) {
                    // Ignore
                }
                iter.remove();
            }
            try {
                selector.close();
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("parallelNioSender.selectorCloseFail"), e);
                }
            }
        }
    }
}
