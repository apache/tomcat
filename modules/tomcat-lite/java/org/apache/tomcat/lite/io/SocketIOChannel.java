/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import org.apache.tomcat.lite.io.NioChannel.NioChannelCallback;

/**
 * Buffered socket channel
 */
public class SocketIOChannel extends IOChannel implements NioChannelCallback {
    IOBuffer out;
    IOBuffer in;
    
    NioChannel ch;
        
    SocketIOChannel(IOConnector connector, NioChannel data, 
            String target)
            throws IOException {
        this.connector = connector;
        in = new IOBuffer(this);
        out = new IOBuffer(this);
        this.ch = data;
        setOutBuffer(out);
        setChannel(data);
        this.target = target;
    }

    void setChannel(NioChannel data) {
        this.ch = data;
        if (ch != null) {
            ch.callback = this;
        }
    }
    
    
    @Override
    public IOBuffer getIn() {
        return in;
    }

    @Override
    public IOBuffer getOut() {
        return out;
    }
    
    /** 
     * Both in and out open
     */
    public boolean isOpen() {
        if (ch == null) {
            return false;
        }
        return ch.isOpen() && ch.channel != null && 
            ch.channel.isOpen() && !getIn().isAppendClosed() &&
            !getOut().isAppendClosed();
    }

    NioChannel getSelectorChannel() {
        return ch;
    }

    public String toString() {
        return ch.toString();
    }
    
    public void setOutBuffer(IOBuffer out) {
        this.out = out;
    }
    
    ByteBuffer flushBuffer;

    /**
     * Send as much as possible. 
     * 
     * Adjust write interest so we can send more when possible.
     */
    private void flush(NioChannel ch) throws IOException {
        synchronized (this) {
            if (ch == null) {
                if (out.isClosedAndEmpty()) {
                    return;
                }
                throw new IOException("flush() with closed socket");
            }
            while (true) {
                if (out.isClosedAndEmpty()) {
                    ch.shutdownOutput();
                    break;
                }
                BBucket bb = out.peekFirst(); 
                if (bb == null) {
                    break;
                }
                flushBuffer = getReadableBuffer(flushBuffer, bb);
                int before = flushBuffer.position();
                
                int done = 0;
                while (flushBuffer.remaining() > 0) {
                    try {
                        done = ch.write(flushBuffer);
                    } catch (IOException ex) {
                        // can't write - was closed !
                        done = -1;
                    }

                    if (done < 0) {
                        ch.close();
                        out.close();
                        handleFlushed(this);
                        //throw new IOException("Closed while writting ");
                        return;
                    }
                    if (done == 0) {
                        bb.position(flushBuffer.position());
                        ch.writeInterest(); // it is cleared on next dataWriteable
                        return;
                    }
                }
                releaseReadableBuffer(flushBuffer, bb);
            }
            handleFlushed(this);

        }
    }
    
    /**
     * Data available for read, called from IO thread.
     * You MUST read all data ( i.e. until read() returns 0).
     *  
     * OP_READ remain active - call readInterest(false) to disable - 
     * for example to suspend reading if buffer is full.
     */
    public void handleReceived(IOChannel net) throws IOException {
        // All data will go to currentReceiveBuffer, until it's full.
        // Then a new buffer will be allocated/pooled.
        
        // When we fill the buffers or finish this round of reading - 
        // we place the Buckets in the queue, as 'readable' buffers.
        boolean newData = false;
        try {
            synchronized(in) {
                // data between 0 and position
                while (true) {
                    if (in.isAppendClosed()) { // someone closed me ?
                        ch.inputClosed(); // remove read interest.
                        // if outClosed - close completely
                        super.sendHandleReceivedCallback();
                        return;
                    }
                    
                    ByteBuffer bb = in.getWriteBuffer();
                    int read = ch.read(bb);
                    in.releaseWriteBuffer(read);
    
                    if (in == null) {
                        // Detached.
                        if (newData) {
                            sendHandleReceivedCallback();
                        }
                        return;
                    }
                    
                    if (read < 0) {
                        ch.inputClosed();
                        // mark the in buffer as closed
                        in.close();
                        sendHandleReceivedCallback();
                        return;
                    }
                    if (read == 0) {
                        if (newData) {
                            super.sendHandleReceivedCallback();
                        }
                        return;
                    }
                    newData = true;
                }
            }
        } catch (Throwable t) {
            close();
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException(t.toString());
            }
        }
    }

    public static final ByteBuffer getReadableBuffer(ByteBuffer orig, BBucket bucket) {
        if (orig == null || orig.array() != bucket.array()) {
            orig = ByteBuffer.wrap(bucket.array());
        }
        orig.position(bucket.position());
        orig.limit(bucket.limit());
        return orig;
    }
    
    public static final void releaseReadableBuffer(ByteBuffer bb, BBucket bucket) {
        bucket.position(bb.position());
    }
    
    
    
    public void readInterest(boolean b) throws IOException {
        ch.readInterest(b);
    }
    
    public InetAddress getAddress(boolean remote) {
        return ch.getAddress(remote);
    }

    public int getPort(boolean remote) {
        return ch.getPort(remote);
    }

    public String getRemoteAddress() {
        return getAddress(true).toString();
    }

    public int getRemotePort() {
        return getPort(true);
    }


    public void startSending() throws IOException {
        flush(ch);
    }

    public void shutdownOutput() throws IOException {
        getOut().close();
        if (ch != null) {
            startSending();
        }
    }
    
    @Override
    public void handleClosed(NioChannel ch) throws IOException {
        closed(); // our callback.
    }
    
    public void closed() throws IOException {
        getIn().close();
        sendHandleReceivedCallback();
        //super.closed();
    }
    
    @Override
    public void handleConnected(NioChannel ch) throws IOException {
        setChannel(ch);
        connectedCallback.handleConnected(this);
    }

    @Override
    public void handleReadable(NioChannel ch) throws IOException {
        handleReceived(this);
    }

    @Override
    public void handleWriteable(NioChannel ch) throws IOException {
        flush(ch);
    }
}
