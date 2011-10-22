package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;


/**
 * Wrapper around the real channel, with selector-specific info.
 *
 * It is stored as an attachment in the selector.
 */
public class NioChannel implements ByteChannel {

    public static interface NioChannelCallback {
        public void handleConnected(NioChannel ch) throws IOException;
        public void handleClosed(NioChannel ch) throws IOException;
        public void handleReadable(NioChannel ch) throws IOException;
        public void handleWriteable(NioChannel ch) throws IOException;

    }

    NioChannel(NioThread sel) {
        this.sel = sel;
    }

    // APR long is wrapped in a ByteChannel as well - with few other longs.
    Channel channel;

    // sync access.
    Object selKey;

    NioThread sel;

    /**
     * If != 0 - the callback will be notified closely after this time.
     * Used for timeouts.
     */
    long nextTimeEvent = 0;

    // Callbacks
    Runnable timeEvent;

    NioChannelCallback callback;


    Throwable lastException;

    // True if the callback wants to be notified of read/write
    boolean writeInterest;
    boolean readInterest;

    // shutdownOutput has been called ?
    private boolean outClosed = false;

    // read() returned -1 OR input buffer closed ( no longer interested )
    boolean inClosed = false;

    // Saved to allow debug messages for bad interest/looping
    int lastReadResult;
    int zeroReads = 0;
    int lastWriteResult;

    protected NioChannel() {

    }

    public NioThread getSelectorThread() {
        return sel;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("SelData/")
        .append(writeInterest ? "W/" : "")
        .append(readInterest ? "R/" : "")
        .append(outClosed ? "Out-CLOSE/" : "")
        .append(inClosed ? "In-CLOSE/" : "")
        .append("/")
        .append(channel.toString());

        return sb.toString();
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isOpen() {
        // in and out open
        return channel.isOpen() && !outClosed && !inClosed;
    }

    public int read(ByteBuffer bb) throws IOException {
        return sel.readNonBlocking(this, bb);
    }

    public int write(ByteBuffer bb) throws IOException {
        return sel.writeNonBlocking(this, bb);
    }

    public void readInterest(boolean b) throws IOException {
        sel.readInterest(this, b);
    }

    public void writeInterest() throws IOException {
        sel.writeInterest(this);
    }

    public InetAddress getAddress(boolean remote) {
        return sel.getAddress(this, remote);
    }

    public int getPort(boolean remote) {
        return sel.getPort(this, remote);
    }

    /**
     * Run in selector thread.
     */
    public void runInSelectorThread(Runnable t) throws IOException {
        sel.runInSelectorThread(t);
    }

    /**
     * Request a timer event. The thread will generate the events at
     * a configurable interval - for example no more often than 0.5 sec.
     */
    public void setTimer(long timeMs, Runnable cb) {
        this.nextTimeEvent = timeMs;
        this.timeEvent = cb;
    }

    /**
     *  shutdown out + in
     *  If there is still data in the input buffer - RST will be sent
     *  instead of FIN.
     *
     *
     * The proper way to close a connection is to shutdownOutput() first,
     * wait until read() return -1, then call close().
     *
     * If read() returns -1, you need to finish sending, call shutdownOutput()
     * than close.
     * If read() returns -1 and there is an error - call close()
     * directly.
     *
     */
    @Override
    public void close() throws IOException {
        shutdownOutput();
        inputClosed();
    }

    /**
     *  Send TCP close(FIN). HTTP uses this to transmit end of body. The other end
     *  detects this with a '-1' in read().
     *
     *  All other forms of close() are reported as exceptions in read().
     *
     * @throws IOException
     */
    public void shutdownOutput() throws IOException {
        synchronized (channel) {
            if (!outClosed) {
                outClosed = true;
                try {
                    sel.shutdownOutput(this);
                } catch (IOException ex) {
                    // ignore
                }
            }
            if (inClosed) {
                sel.close(this, null);
            }
        }
    }

    void inputClosed() throws IOException {
        synchronized (channel) {
            if (inClosed) {
                // already closed
                return;
            }
            inClosed = true; // detected end
            if (outClosed) {
                sel.close(this, null);
            } else {
                // Don't close the channel - write may still work ?
                readInterest(false);
            }
        }
    }

    boolean closeCalled = false;
}