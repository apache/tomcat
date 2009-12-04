/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.http.HttpConnector.RemoteServer;
import org.apache.tomcat.lite.http.HttpMessage.HttpMessageBytes;
import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.IOBuffer;

/*
 * TODO: expectations ? 
 * Fix docs - order matters
 * Crashes in chrome
 */

public class SpdyConnection extends HttpConnector.HttpConnection  {
    
    public static class SpdyConnectionManager 
        extends HttpConnector.HttpConnectionManager {
        @Override
        public HttpConnection newConnection(HttpConnector con) {
            return new SpdyConnection(con);
        }

        @Override
        public HttpConnection getFromPool(RemoteServer t) {
            // TODO: we may initiate multiple SPDY connections with each server
            // Sending frames is synchronized, receiving is muxed
            return t.connections.get(0);
        }
        
    }
    

    protected static Logger log = Logger.getLogger("SpdyConnection");
    
    /**
     * @param spdyConnector
     */
    SpdyConnection(HttpConnector spdyConnector) {
        this.httpConnector = spdyConnector;
    }

    AtomicInteger lastInStream = new AtomicInteger();
    AtomicInteger lastOutStream = new AtomicInteger();

    // TODO: use int map
    Map<Integer, HttpChannel> channels = new HashMap();

    SpdyConnection.Frame currentInFrame = null;

    SpdyConnection.Frame lastFrame = null; // for debug

    BBuffer outFrameBuffer = BBuffer.allocate();
    BBuffer inFrameBuffer = BBuffer.allocate();

    BBuffer headW = BBuffer.wrapper();
    
    // TODO: detect if it's spdy or http based on bit 8

    @Override
    public void withExtraBuffer(BBuffer received) {
        inFrameBuffer = received;
    }
    
    @Override
    public void dataReceived(IOBuffer iob) throws IOException {
        int avail = iob.available();
        while (avail > 0) {
            if (currentInFrame == null) {
                if (inFrameBuffer.remaining() + avail < 8) {
                    return;
                }
                if (inFrameBuffer.remaining() < 8) {
                    int headRest = 8 - inFrameBuffer.remaining();
                    int rd = iob.read(inFrameBuffer, headRest);
                    avail -= rd;
                }
                currentInFrame = new SpdyConnection.Frame(); // TODO: reuse
                currentInFrame.parse(inFrameBuffer);
            }
            if (avail < currentInFrame.length) {
                return;
            }
            // We have a full frame. Process it.
            onFrame(iob);

            // TODO: extra checks, make sure the frame is correct and
            // it consumed all data.
            avail -= currentInFrame.length;
            currentInFrame = null;
        }
    }

    /**
     * Frame received. Must consume all data for the frame.
     * 
     * @param iob
     * @throws IOException
     */
    protected void onFrame(IOBuffer iob) throws IOException {
        // TODO: make sure we have enough data.
        lastFrame = currentInFrame;
        
        if (currentInFrame.c) {
            if (currentInFrame.type == SpdyConnection.Frame.TYPE_HELO) {
                // receivedHello = currentInFrame;
            } else if (currentInFrame.type == SpdyConnection.Frame.TYPE_SYN_STREAM) {
                HttpChannel ch = new HttpChannel(); // TODO: reuse
                ch.channelId = SpdyConnection.readInt(iob);
                ch.setConnection(this);
                ch.httpConnector = this.httpConnector;
                if (serverMode) {
                    ch.serverMode(true);
                }
                if (this.httpConnector.defaultService != null) {
                    ch.setHttpService(this.httpConnector.defaultService);
                }

                channels.put(ch.channelId, ch);

                // pri and unused
                SpdyConnection.readShort(iob);

                HttpMessageBytes reqBytes = ch.getRequest().getMsgBytes();
                
                BBuffer head = processHeaders(iob, ch, reqBytes);

                ch.getRequest().processReceivedHeaders();

                ch.handleHeadersReceived(ch.getRequest());

                if ((currentInFrame.flags & SpdyConnection.Frame.FLAG_HALF_CLOSE) != 0) {
                    ch.getIn().close();
                    ch.handleEndReceive();
                }
            } else if (currentInFrame.type == SpdyConnection.Frame.TYPE_SYN_REPLY) {
                int chId = SpdyConnection.readInt(iob);
                HttpChannel ch = channels.get(chId);
                
                SpdyConnection.readShort(iob);
        
                HttpMessageBytes resBytes = ch.getResponse().getMsgBytes();
                
                BBuffer head = processHeaders(iob, ch, resBytes);

                ch.getResponse().processReceivedHeaders();

                ch.handleHeadersReceived(ch.getResponse());

                if ((currentInFrame.flags & SpdyConnection.Frame.FLAG_HALF_CLOSE) != 0) {
                    ch.getIn().close();
                    ch.handleEndReceive();
                }
            } else {
                log.warning("Unknown frame type " + currentInFrame.type);
                iob.advance(currentInFrame.length);
            }
        } else {
            // data frame - part of an existing stream
            HttpChannel ch = channels.get(currentInFrame.streamId);
            if (ch == null) {
                log.warning("Unknown stream ");
                net.close();
                net.startSending();
                return;
            }
            int len = currentInFrame.length;
            while (len > 0) {
                BBucket bb = iob.peekFirst();
                if (len > bb.remaining()) {
                    ch.getIn().append(bb);
                    len += bb.remaining();
                    bb.position(bb.limit());
                } else {
                    ch.getIn().append(bb, len);
                    bb.position(bb.position() + len);
                    len = 0;
                }
            }
            ch.sendHandleReceivedCallback();
            
            if ((currentInFrame.flags & SpdyConnection.Frame.FLAG_HALF_CLOSE) != 0) {
                ch.getIn().close();
                ch.handleEndReceive();
            }
        }
    }

    private BBuffer processHeaders(IOBuffer iob, HttpChannel ch,
            HttpMessageBytes reqBytes) throws IOException {
        int nvCount = SpdyConnection.readShort(iob);
        int read = 8;

        iob.read(headRecvBuf, currentInFrame.length - 8);

        // Wrapper - so we don't change position in head
        headRecvBuf.wrapTo(headW);
        
        BBuffer nameBuf = BBuffer.wrapper();
        BBuffer valBuf = BBuffer.wrapper();

        for (int i = 0; i < nvCount; i++) {

            int nameLen = SpdyConnection.readShort(headW);

            nameBuf
                    .setBytes(headW.array(), headW.position(),
                            nameLen);
            headW.advance(nameLen);

            int valueLen = SpdyConnection.readShort(headW);
            valBuf
                    .setBytes(headW.array(), headW.position(),
                            valueLen);
            headW.advance(valueLen);

            // TODO: no need to send version, method if default

            if (nameBuf.equals("method")) {
                valBuf.wrapTo(reqBytes.method());
            } else if (nameBuf.equals("version")) {
                valBuf.wrapTo(reqBytes.protocol());
            } else if (nameBuf.equals("url")) {
                valBuf.wrapTo(reqBytes.url());
                // TODO: spdy uses full URL, we may want to trim
                // also no host header
            } else {
                int idx = reqBytes.addHeader();
                nameBuf.wrapTo(reqBytes.getHeaderName(idx));
                valBuf.wrapTo(reqBytes.getHeaderValue(idx));
            }

            // TODO: repeated values are separated by a 0
            // pretty weird...
            read += nameLen + valueLen + 4;
        }
        return headW;
    }

    @Override
    protected void sendRequest(HttpChannel http) throws IOException {
        if (serverMode) {
            throw new IOException("Only in client mode");
        }

        MultiMap mimeHeaders = http.getRequest().getMimeHeaders();
        BBuffer headBuf = BBuffer.allocate();
        
        SpdyConnection.appendShort(headBuf, mimeHeaders.size() + 3);
        
        serializeMime(mimeHeaders, headBuf);

        // TODO: url - with host prefix , method
        // optimize...
        SpdyConnection.appendAsciiHead(headBuf, "version");
        SpdyConnection.appendAsciiHead(headBuf, "HTTP/1.1");
        
        SpdyConnection.appendAsciiHead(headBuf, "method");
        SpdyConnection.appendAsciiHead(headBuf, http.getRequest().getMethod());
        
        SpdyConnection.appendAsciiHead(headBuf, "url");
        // TODO: url
        SpdyConnection.appendAsciiHead(headBuf, http.getRequest().requestURL());
        
        
        BBuffer out = BBuffer.allocate();
        // Syn-reply 
        out.putByte(0x80); 
        out.putByte(0x01); 
        out.putByte(0x00); 
        out.putByte(0x01);
        
        if (http.getOut().isAppendClosed()) {
            out.putByte(0x01); // closed
        } else {
            out.putByte(0x00); 
        }
        SpdyConnection.append24(out, headBuf.remaining() + http.getOut().available() + 4);
        
        if (serverMode) {
            http.channelId = 2 * lastOutStream.incrementAndGet();
        } else {
            http.channelId = 2 * lastOutStream.incrementAndGet() + 1;            
        }
        SpdyConnection.appendInt(out, http.channelId);
        
        channels.put(http.channelId, http);
        
        out.putByte(0x00); // no priority 
        out.putByte(0x00); 
        
        sendFrame(out, headBuf); 

        // Any existing data
        sendData(http);
    }
    
    @Override
    protected void sendResponseHeaders(HttpChannel http) throws IOException {
        if (!serverMode) {
            throw new IOException("Only in server mode");
        }

        if (http.getResponse().isCommitted()) {
            return; 
        }
        http.getResponse().setCommitted(true);

        MultiMap mimeHeaders = http.getResponse().getMimeHeaders();

        BBuffer headBuf = BBuffer.allocate();

        SpdyConnection.appendInt(headBuf, http.channelId);
        headBuf.putByte(0);
        headBuf.putByte(0);

        //mimeHeaders.remove("content-length");
        
        SpdyConnection.appendShort(headBuf, mimeHeaders.size() + 2);
        
        // chrome will crash if we don't send the header
        serializeMime(mimeHeaders, headBuf);

        // Must be at the end
        SpdyConnection.appendAsciiHead(headBuf, "status");
        SpdyConnection.appendAsciiHead(headBuf, 
                Integer.toString(http.getResponse().getStatus()));

        SpdyConnection.appendAsciiHead(headBuf, "version");
        SpdyConnection.appendAsciiHead(headBuf, "HTTP/1.1");

        
        BBuffer out = BBuffer.allocate();
        // Syn-reply 
        out.putByte(0x80); // Control
        out.putByte(0x01); // version
        out.putByte(0x00); // 00 02 - SYN_REPLY
        out.putByte(0x02);
        
        // It seems piggibacking data is not allowed
        out.putByte(0x00); 

        SpdyConnection.append24(out, headBuf.remaining());
        
        sendFrame(out, headBuf);
    }
    
    
    public void startSending(HttpChannel http) throws IOException {
        http.send(); // if needed
        
        if (net != null) {
            sendData(http);
            net.startSending();
        }
    }
    
    private void sendData(HttpChannel http) throws IOException {
        int avail = http.getOut().available();
        boolean closed = http.getOut().isAppendClosed();
        if (avail > 0 || closed) {
            sendDataFrame(http.getOut(), avail,
                    http.channelId, closed);
            if (avail > 0) {
                getOut().advance(avail);
            }
        }
        if (closed) {
            http.handleEndSent();
        }
    }

    private BBuffer serializeMime(MultiMap mimeHeaders, BBuffer headBuf) 
            throws IOException {

        // TODO: duplicated headers not allowed
        for (int i = 0; i < mimeHeaders.size(); i++) {
            CBuffer name = mimeHeaders.getName(i);
            CBuffer value = mimeHeaders.getValue(i);
            if (name.length() == 0 || value.length() == 0) {
                continue;
            }
            SpdyConnection.appendShort(headBuf, name.length());
            name.toAscii(headBuf);
            SpdyConnection.appendShort(headBuf, value.length());
            value.toAscii(headBuf);
        }
        return headBuf;
    }


    private synchronized void sendFrame(BBuffer out, BBuffer headBuf)
            throws IOException {
        if (net == null) {
            return; // unit test
        }
        net.getOut().append(out);
        if (headBuf != null) {
            net.getOut().append(headBuf);
        }
        net.startSending();
    }

    public synchronized void sendDataFrame(IOBuffer out2, int avail,
            int channelId, boolean last) throws IOException {
        if (net == null) {
            return; // unit test
        }
        outFrameBuffer.recycle();
        SpdyConnection.appendInt(outFrameBuffer, channelId); // first bit 0 ?
        if (last) {
            outFrameBuffer.putByte(0x01); // closed
        } else {
            outFrameBuffer.putByte(0x00);
        }

        // TODO: chunk if too much data ( at least at 24 bits)
        SpdyConnection.append24(outFrameBuffer, avail);

        net.getOut().append(outFrameBuffer);
        if (avail > 0) {
            net.getOut().append(out2, avail);
        }
        net.startSending();
    }

    static void appendInt(BBuffer headBuf, int length) throws IOException {
        headBuf.putByte((length & 0xFF000000) >> 24);
        headBuf.putByte((length & 0xFF0000) >> 16);
        headBuf.putByte((length & 0xFF00) >> 8);
        headBuf.putByte((length & 0xFF));
    }

    static void append24(BBuffer headBuf, int length) throws IOException {
        headBuf.putByte((length & 0xFF0000) >> 16);
        headBuf.putByte((length & 0xFF00) >> 8);
        headBuf.putByte((length & 0xFF));
    }

    static void appendAsciiHead(BBuffer headBuf, CBuffer s) throws IOException {
        appendShort(headBuf, s.length());
        for (int i = 0; i < s.length(); i++) {
            headBuf.append(s.charAt(i));
        }
    }

    static void appendShort(BBuffer headBuf, int length) throws IOException {
        if (length > 0xFFFF) {
            throw new IOException("Too long");
        }
        headBuf.putByte((length & 0xFF00) >> 8);
        headBuf.putByte((length & 0xFF));
    }

    static void appendAsciiHead(BBuffer headBuf, String s) throws IOException {
        SpdyConnection.appendShort(headBuf, s.length());
        for (int i = 0; i < s.length(); i++) {
            headBuf.append(s.charAt(i));
        }
    }

    static int readShort(BBuffer iob) throws IOException {
        int res = iob.readByte();
        return res << 8 | iob.readByte();
    }

    static int readShort(IOBuffer iob) throws IOException {
        int res = iob.read();
        return res << 8 | iob.read();
    }

    static int readInt(IOBuffer iob) throws IOException {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            int b0 = iob.read();
            res = res << 8 | b0;
        }
        return res;
    }

    public static class Frame {
        int flags;
    
        int length;
    
        boolean c; // for control
    
        int version;
    
        int type;
    
        int streamId; // for data
    
        static int TYPE_HELO = 4;
    
        static int TYPE_SYN_STREAM = 1;

        static int TYPE_SYN_REPLY = 2;
    
        static int FLAG_HALF_CLOSE = 1;
    
        public void parse(BBuffer iob) throws IOException {
            int b0 = iob.read();
            if (b0 < 128) {
                c = false;
                streamId = b0;
                for (int i = 0; i < 3; i++) {
                    b0 = iob.read();
                    streamId = streamId << 8 | b0;
                }
            } else {
                c = true;
                b0 -= 128;
                version = ((b0 << 8) | iob.read());
                b0 = iob.read();
                type = ((b0 << 8) | iob.read());
            }
    
            flags = iob.read();
            for (int i = 0; i < 3; i++) {
                b0 = iob.read();
                length = length << 8 | b0;
            }

            iob.recycle();
        }
    
    }
    
    /** 
     * Framing error, client interrupt, etc.
     */
    public void abort(HttpChannel http, String t) throws IOException {
        // TODO: send interrupt signal
    }


}