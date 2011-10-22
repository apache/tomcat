/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;



/**
 * Buffered, non-blocking ByteChannel.
 *
 * write() data will be added to the buffer. Call startSending() to
 * flush.
 *
 *
 *
 * - you can use it as a normal non-blocking ByteChannel.
 * - you can call getRead
 *
 * Very different from MINA IoFilters, also much lower level.
 *
 *
 * @author Costin Manolache
 */
public abstract class IOChannel implements ByteChannel, IOConnector.DataReceivedCallback,
        IOConnector.DataFlushedCallback {

    /**
     * If this channel wraps another channel - for example a socket.
     * Will be null if this is the 'root' channel - a socket, memory.
     */
    protected IOChannel net;

    /**
     * Set with another channel layered on top of the current channel.
     */
    protected IOChannel head;

    protected String id;

    /**
     * A string that can be parsed to extract the target.
     * host:port for normal sockets
     */
    protected CharSequence target;

    /**
     * Connector that created the channel.
     */
    protected IOConnector connector;

    /**
     * Callbacks. Will be moved if a new head is inserted.
     */
    protected IOConnector.ConnectedCallback connectedCallback;

    /**
     * Will be called if any data is received.
     * Will also be called on close. Close with lastException set indicates
     * an error condition.
     */
    protected IOConnector.DataReceivedCallback dataReceivedCallback;

    /**
     * Out data is buffered, then sent with startSending.
     * This callback indicates the data has been sent. Can be used
     * to implement blocking flush.
     */
    protected IOConnector.DataFlushedCallback dataFlushedCallback;

    // Last activity timestamp.
    // TODO: update and use it ( placeholder )
    public long ts;

    /**
     * If an async exception happens.
     */
    protected Throwable lastException;

    protected IOChannel() {
    }

    public void setConnectedCallback(IOConnector.ConnectedCallback connectedCallback) {
        this.connectedCallback = connectedCallback;
    }

    public void setDataReceivedCallback(IOConnector.DataReceivedCallback dataReceivedCallback) {
        this.dataReceivedCallback = dataReceivedCallback;
    }

    /**
     * Callback called when the bottom ( OS ) channel has finished flushing.
     *
     * @param dataFlushedCallback
     */
    public void setDataFlushedCallback(IOConnector.DataFlushedCallback dataFlushedCallback) {
        this.dataFlushedCallback = dataFlushedCallback;
    }

    // Input
    public abstract IOBuffer getIn();

    // Output
    public abstract IOBuffer getOut();


    /**
     * From downstream ( NET ). Pass it to the next channel.
     */
    public void handleReceived(IOChannel net) throws IOException {
        sendHandleReceivedCallback();
    }

    /**
     * Called from lower layer (NET) when the last flush is
     * done and all buffers have been sent to OS ( or
     * intended recipient ).
     *
     * Will call the callback or next filter, may do additional
     * processing.
     *
     * @throws IOException
     */
    public void handleFlushed(IOChannel net) throws IOException {
        sendHandleFlushedCallback();
    }

    private void sendHandleFlushedCallback() throws IOException {
        try {
            if (dataFlushedCallback != null) {
                dataFlushedCallback.handleFlushed(this);
            }
            if (head != null) {
                head.handleFlushed(this);
            }
        } catch (Throwable t) {
            close();
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new WrappedException("Error in handleFlushed", t);
            }
        }
    }


    /**
     * Notify next channel or callback that data has been received.
     * Called after a lower channel gets more data ( in the IOThread
     * for example ).
     *
     * Also called when closed stream is detected. Can be called
     * to just force upper layers to check for data.
     */
    public void sendHandleReceivedCallback() throws IOException {
        try {
            if (dataReceivedCallback != null) {
                dataReceivedCallback.handleReceived(this);
            }
            if (head != null) {
                head.handleReceived(this);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                close();
            } catch(Throwable t2) {
                t2.printStackTrace();
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new WrappedException(t);
            }
        }
    }

    /**
     * Return last IO exception.
     *
     * The channel is async, exceptions can happen at any time.
     * The normal callback will be called ( connected, received ), it
     * should check if the channel is closed and the exception.
     */
    public Throwable lastException() {
        return lastException;
    }

    public void close() throws IOException {
        shutdownOutput();
        // Should it read the buffers ?

        if (getIn() == null || getIn().isAppendClosed()) {
            return;
        } else {
            getIn().close();
            sendHandleReceivedCallback();
        }
        getIn().hasDataLock.signal(getIn());
    }

    public boolean isOpen() {
        return getIn() != null &&
        getOut() != null &&
        !getIn().isAppendClosed() && !getOut().isAppendClosed();
    }

    public void shutdownOutput() throws IOException {
        if (getOut() == null || getOut().isAppendClosed()) {
            return;
        } else {
            getOut().close();
            startSending();
        }
    }

    public void setSink(IOChannel previous) throws IOException {
        this.net = previous;
    }

    public IOChannel getSink() {
        return net;
    }

    // Chaining/filtering

    /**
     * Called to add an filter after the current channel, for
     * example set SSL on top of a socket channel.
     *
     * The 'next' channel will have the received/flushed callbacks
     * of the current channel. The current channel's callbacks will
     * be reset.
     *
     * "Head" is from STREAMS.
     *
     * @throws IOException
     */
    public IOChannel setHead(IOChannel head) throws IOException {
        this.head = head;
        head.setSink(this);

        // TODO: do we want to migrate them automatically ?
        head.setDataReceivedCallback(dataReceivedCallback);
        head.setDataFlushedCallback(dataFlushedCallback);
        // app.setClosedCallback(closedCallback);

        dataReceivedCallback = null;
        dataFlushedCallback = null;
        return this;
    }

    public IOChannel getFirst() {
        IOChannel first = this;
        while (true) {
            if (!(first instanceof IOChannel)) {
                return first;
            }
            IOChannel before = ((IOChannel) first).getSink();
            if (before == null) {
                return first;
            } else {
                first = before;
            }
        }
    }

    // Socket support

    public void readInterest(boolean b) throws IOException {
        if (net != null) {
            net.readInterest(b);
        }
    }

    // Helpers

    public int read(ByteBuffer bb) throws IOException {
        return getIn().read(bb);
    }

    public int readNonBlocking(ByteBuffer bb) throws IOException {
        return getIn().read(bb);
    }

    public void waitFlush(long timeMs) throws IOException {
        return;
    }

    public int readBlocking(ByteBuffer bb, long timeMs) throws IOException {
        getIn().waitData(timeMs);
        return getIn().read(bb);
    }

    /**
     * Capture all output in a buffer.
     */
    public BBuffer readAll(BBuffer chunk, long to)
            throws IOException {
        if (chunk == null) {
            chunk = BBuffer.allocate();
        }
        while (true) {
            getIn().waitData(to);
            BBucket next = getIn().peekFirst();
            if (getIn().isClosedAndEmpty() && next == null) {
                return chunk;
            }
            if (next == null) {
                continue; // false positive
            }
            chunk.append(next.array(), next.position(), next.remaining());
            getIn().advance(next.remaining());
        }
    }

    public int write(ByteBuffer bb) throws IOException {
        return getOut().write(bb);
    }

    public void write(byte[] data) throws IOException {
        getOut().append(data, 0, data.length);
    }

    public void write(String string) throws IOException {
        write(string.getBytes());
    }

    /**
     * Send data in out to the intended recipient.
     * This is not blocking.
     */
    public abstract void startSending() throws IOException;


    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public CharSequence getTarget() {
        if (net != null) {
            return net.getTarget();
        }
        return target;
    }

    public void setTarget(CharSequence target) {
        this.target = target;
    }

    public static final String ATT_REMOTE_HOSTNAME = "RemoteHostname";
    public static final String ATT_LOCAL_HOSTNAME = "LocalHostname";
    public static final String ATT_REMOTE_PORT = "RemotePort";
    public static final String ATT_LOCAL_PORT = "LocalPort";
    public static final String ATT_LOCAL_ADDRESS = "LocalAddress";
    public static final String ATT_REMOTE_ADDRESS = "RemoteAddress";

    public Object getAttribute(String name) {
        if (net != null) {
            return net.getAttribute(name);
        }
        return null;
    }

}
