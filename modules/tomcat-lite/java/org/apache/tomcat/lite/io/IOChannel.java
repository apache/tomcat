/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;



/**
 * Buffered ByteChannel, backed by a buffer brigade to allow
 * some zero-copy operations.
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
        IOConnector.DataFlushedCallback { //, IOConnector.ClosedCallback {
    
    protected IOChannel net;
    protected IOChannel app;
    
    protected String id;
    protected String target;    

    protected IOConnector connector;

    protected IOConnector.ConnectedCallback connectedCallback;
    protected IOConnector.DataReceivedCallback dataReceivedCallback;
    protected IOConnector.DataFlushedCallback dataFlushedCallback;

    // Last activity timestamp.
    // TODO: update, etc
    public long ts;
    
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

    protected IOChannel() {
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
    
    public void sendHandleFlushedCallback() throws IOException {
        try {
            if (dataFlushedCallback != null) {
                dataFlushedCallback.handleFlushed(this);
            }
            if (app != null) {
                app.handleFlushed(this);
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
     * Notify next channel that data has been received.  
     */
    public void sendHandleReceivedCallback() throws IOException {
        try {
            if (dataReceivedCallback != null) {
                dataReceivedCallback.handleReceived(this);
            }
            if (app != null) {
                app.handleReceived(this);
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
  
    public void close() throws IOException {
        shutdownOutput();
        // Should it read the buffers ? 
        
        if (getIn().isAppendClosed()) {
            return;
        } else {
            getIn().close();
            sendHandleReceivedCallback();
        }
        getIn().hasDataLock.signal(getIn());
    }

    public boolean isOpen() {
        return !getIn().isAppendClosed() && !getOut().isAppendClosed();
    }
    
    public void shutdownOutput() throws IOException {
        if (getOut().isAppendClosed()) {
            return;
        } else {
            getOut().close();
            startSending();
        }
    }

    public void setSink(IOChannel previous) {
        this.net = previous;
    }

    public IOChannel getSink() {
        return net;
    }

    // Chaining/filtering
    
    /** 
     * Called to add an filter _after_ the current channel.
     */
    public IOChannel addFilterAfter(IOChannel next) {
        this.app = next;
        app.setSink(this);

        // TODO: do we want to migrate them automatically ?
        app.setDataReceivedCallback(dataReceivedCallback);
        app.setDataFlushedCallback(dataFlushedCallback);
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
    
    public int getPort(boolean remote) {
        if (net != null) {
            return net.getPort(remote);
        }
        return 80;
    }
    
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
    
    
}
