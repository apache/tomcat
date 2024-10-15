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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.transport.AbstractSender;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * This class is NOT thread safe and should never be used with more than one thread at a time This is a state machine,
 * handled by the process method States are:
 * <ul>
 * <li>NOT_CONNECTED -&gt; connect() -&gt; CONNECTED</li>
 * <li>CONNECTED -&gt; setMessage() -&gt; READY TO WRITE</li>
 * <li>READY_TO_WRITE -&gt; write() -&gt; READY TO WRITE | READY TO READ</li>
 * <li>READY_TO_READ -&gt; read() -&gt; READY_TO_READ | TRANSFER_COMPLETE</li>
 * <li>TRANSFER_COMPLETE -&gt; CONNECTED</li>
 * </ul>
 */
public class NioSender extends AbstractSender {

    private static final Log log = LogFactory.getLog(NioSender.class);
    protected static final StringManager sm = StringManager.getManager(NioSender.class);


    protected Selector selector;
    protected SocketChannel socketChannel = null;
    protected DatagramChannel dataChannel = null;

    /*
     * STATE VARIABLES *
     */
    protected ByteBuffer readbuf = null;
    protected ByteBuffer writebuf = null;
    protected volatile byte[] current = null;
    protected final XByteBuffer ackbuf = new XByteBuffer(128, true);
    protected int remaining = 0;
    protected boolean complete;

    protected boolean connecting = false;

    public NioSender() {
        super();

    }

    /**
     * State machine to send data.
     *
     * @param key        The key to use
     * @param waitForAck Wait for an ack
     *
     * @return <code>true</code> if the processing was successful
     *
     * @throws IOException An IO error occurred
     */
    public boolean process(SelectionKey key, boolean waitForAck) throws IOException {
        int ops = key.readyOps();
        key.interestOps(key.interestOps() & ~ops);
        // in case disconnect has been called
        if ((!isConnected()) && (!connecting)) {
            throw new IOException(sm.getString("nioSender.sender.disconnected"));
        }
        if (!key.isValid()) {
            throw new IOException(sm.getString("nioSender.key.inValid"));
        }
        if (key.isConnectable()) {
            if (socketChannel.finishConnect()) {
                completeConnect();
                if (current != null) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                }
                return false;
            } else {
                // wait for the connection to finish
                key.interestOps(key.interestOps() | SelectionKey.OP_CONNECT);
                return false;
            } // end if
        } else if (key.isWritable()) {
            boolean writecomplete = write();
            if (writecomplete) {
                // we are completed, should we read an ack?
                if (waitForAck) {
                    // register to read the ack
                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                } else {
                    // if not, we are ready, setMessage will reregister us for another write interest
                    // do a health check, we have no way of verify a disconnected
                    // socket since we don't register for OP_READ on waitForAck=false
                    read();// this causes overhead
                    setRequestCount(getRequestCount() + 1);
                    return true;
                }
            } else {
                // we are not complete, lets write some more
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            } // end if
        } else if (key.isReadable()) {
            boolean readcomplete = read();
            if (readcomplete) {
                setRequestCount(getRequestCount() + 1);
                return true;
            } else {
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            } // end if
        } else {
            // unknown state, should never happen
            log.warn(sm.getString("nioSender.unknown.state", Integer.toString(ops)));
            throw new IOException(sm.getString("nioSender.unknown.state", Integer.toString(ops)));
        } // end if
        return false;
    }

    private void configureSocket() throws IOException {
        if (socketChannel != null) {
            socketChannel.configureBlocking(false);
            socketChannel.socket().setSendBufferSize(getTxBufSize());
            socketChannel.socket().setReceiveBufferSize(getRxBufSize());
            socketChannel.socket().setSoTimeout((int) getTimeout());
            socketChannel.socket().setSoLinger(getSoLingerOn(), getSoLingerOn() ? getSoLingerTime() : 0);
            socketChannel.socket().setTcpNoDelay(getTcpNoDelay());
            socketChannel.socket().setKeepAlive(getSoKeepAlive());
            socketChannel.socket().setReuseAddress(getSoReuseAddress());
            socketChannel.socket().setOOBInline(getOoBInline());
            socketChannel.socket().setSoLinger(getSoLingerOn(), getSoLingerTime());
            socketChannel.socket().setTrafficClass(getSoTrafficClass());
        } else if (dataChannel != null) {
            dataChannel.configureBlocking(false);
            dataChannel.socket().setSendBufferSize(getUdpTxBufSize());
            dataChannel.socket().setReceiveBufferSize(getUdpRxBufSize());
            dataChannel.socket().setSoTimeout((int) getTimeout());
            dataChannel.socket().setReuseAddress(getSoReuseAddress());
            dataChannel.socket().setTrafficClass(getSoTrafficClass());
        }
    }

    private void completeConnect() {
        // we connected, register ourselves for writing
        setConnected(true);
        connecting = false;
        setRequestCount(0);
        setConnectTime(System.currentTimeMillis());
    }


    protected boolean read() throws IOException {
        // if there is no message here, we are done
        if (current == null) {
            return true;
        }
        int read = isUdpBased() ? dataChannel.read(readbuf) : socketChannel.read(readbuf);
        // end of stream
        if (read == -1) {
            throw new IOException(sm.getString("nioSender.unable.receive.ack"));
        } else if (read == 0) {
            return false;
        }
        readbuf.flip();
        ackbuf.append(readbuf, read);
        readbuf.clear();
        if (ackbuf.doesPackageExist()) {
            byte[] ackcmd = ackbuf.extractDataPackage(true).getBytes();
            boolean ack = Arrays.equals(ackcmd, org.apache.catalina.tribes.transport.Constants.ACK_DATA);
            boolean fack = Arrays.equals(ackcmd, org.apache.catalina.tribes.transport.Constants.FAIL_ACK_DATA);
            if (fack && getThrowOnFailedAck()) {
                throw new RemoteProcessException(sm.getString("nioSender.receive.failedAck"));
            }
            return ack || fack;
        } else {
            return false;
        }
    }


    protected boolean write() throws IOException {
        if ((!isConnected()) || (this.socketChannel == null && this.dataChannel == null)) {
            throw new IOException(sm.getString("nioSender.not.connected"));
        }
        if (current != null) {
            if (remaining > 0) {
                // we have written everything, or we are starting a new package
                // protect against buffer overwrite
                int byteswritten = isUdpBased() ? dataChannel.write(writebuf) : socketChannel.write(writebuf);
                remaining -= byteswritten;
                // if the entire message was written from the buffer
                // reset the position counter
                if (remaining < 0) {
                    remaining = 0;
                }
            }
            return (remaining == 0);
        }
        // no message to send, we can consider that complete
        return true;
    }

    @Override
    public synchronized void connect() throws IOException {
        if (connecting || isConnected()) {
            return;
        }
        connecting = true;
        if (isConnected()) {
            throw new IOException(sm.getString("nioSender.already.connected"));
        }
        if (readbuf == null) {
            readbuf = getReadBuffer();
        } else {
            readbuf.clear();
        }
        if (writebuf == null) {
            writebuf = getWriteBuffer();
        } else {
            writebuf.clear();
        }

        if (isUdpBased()) {
            InetSocketAddress daddr = new InetSocketAddress(getAddress(), getUdpPort());
            if (dataChannel != null) {
                throw new IOException(sm.getString("nioSender.datagram.already.established"));
            }
            dataChannel = DatagramChannel.open();
            configureSocket();
            dataChannel.connect(daddr);
            completeConnect();
            dataChannel.register(getSelector(), SelectionKey.OP_WRITE, this);

        } else {
            InetSocketAddress addr = new InetSocketAddress(getAddress(), getPort());
            if (socketChannel != null) {
                throw new IOException(sm.getString("nioSender.socketChannel.already.established"));
            }
            socketChannel = SocketChannel.open();
            configureSocket();
            if (socketChannel.connect(addr)) {
                completeConnect();
                socketChannel.register(getSelector(), SelectionKey.OP_WRITE, this);
            } else {
                socketChannel.register(getSelector(), SelectionKey.OP_CONNECT, this);
            }
        }
    }


    @Override
    public void disconnect() {
        try {
            connecting = false;
            setConnected(false);
            if (socketChannel != null) {
                try {
                    try {
                        socketChannel.socket().close();
                    } catch (Exception x) {
                        // Ignore
                    }
                    // error free close, all the way
                    // try {socket.shutdownOutput();}catch ( Exception x){}
                    // try {socket.shutdownInput();}catch ( Exception x){}
                    // try {socket.close();}catch ( Exception x){}
                    try {
                        socketChannel.close();
                    } catch (Exception x) {
                        // Ignore
                    }
                } finally {
                    socketChannel = null;
                }
            }
            if (dataChannel != null) {
                try {
                    try {
                        dataChannel.socket().close();
                    } catch (Exception x) {
                        // Ignore
                    }
                    // error free close, all the way
                    // try {socket.shutdownOutput();}catch ( Exception x){}
                    // try {socket.shutdownInput();}catch ( Exception x){}
                    // try {socket.close();}catch ( Exception x){}
                    try {
                        dataChannel.close();
                    } catch (Exception x) {
                        // Ignore
                    }
                } finally {
                    dataChannel = null;
                }
            }
        } catch (Exception x) {
            log.error(sm.getString("nioSender.unable.disconnect", x.getMessage()));
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("nioSender.unable.disconnect", x.getMessage()), x);
            }
        }
    }

    public void reset() {
        if (isConnected() && readbuf == null) {
            readbuf = getReadBuffer();
        }
        if (readbuf != null) {
            readbuf.clear();
        }
        if (writebuf != null) {
            writebuf.clear();
        }
        current = null;
        ackbuf.clear();
        remaining = 0;
        complete = false;
        setAttempt(0);
        setUdpBased(false);
    }

    private ByteBuffer getReadBuffer() {
        return getBuffer(getRxBufSize());
    }

    private ByteBuffer getWriteBuffer() {
        return getBuffer(getTxBufSize());
    }

    private ByteBuffer getBuffer(int size) {
        return getDirectBuffer() ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }

    /**
     * Send message.
     *
     * @param data ChannelMessage
     *
     * @throws IOException if an error occurs
     */
    public void setMessage(byte[] data) throws IOException {
        setMessage(data, 0, data.length);
    }

    public void setMessage(byte[] data, int offset, int length) throws IOException {
        if (data != null) {
            synchronized (this) {
                current = data;
                remaining = length;
                ackbuf.clear();
                if (writebuf != null) {
                    writebuf.clear();
                } else {
                    writebuf = getBuffer(length);
                }
                if (writebuf.capacity() < length) {
                    writebuf = getBuffer(length);
                }

                // TODO use ByteBuffer.wrap to avoid copying the data.
                writebuf.put(data, offset, length);
                writebuf.flip();
                if (isConnected()) {
                    if (isUdpBased()) {
                        dataChannel.register(getSelector(), SelectionKey.OP_WRITE, this);
                    } else {
                        socketChannel.register(getSelector(), SelectionKey.OP_WRITE, this);
                    }
                }
            }
        }
    }

    public byte[] getMessage() {
        return current;
    }


    public boolean isComplete() {
        return complete;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }


    public void setComplete(boolean complete) {
        this.complete = complete;
    }
}
