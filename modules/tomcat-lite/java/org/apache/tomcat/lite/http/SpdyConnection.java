/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpConnectionPool.RemoteServer;
import org.apache.tomcat.lite.http.HttpMessage.HttpMessageBytes;
import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.DumpChannel;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;

/*
 * TODO: expectations ?
 * Fix docs - order matters
 * Crashes in chrome
 *
 * Test with unit tests or:
 *  google-chrome --use-flip=no-ssl
 *    --user-data-dir=/home/$USER/.config/google-chrome/Test
 *    http://localhost:8802/hello
 */

public class SpdyConnection extends HttpConnector.HttpConnection
        implements IOConnector.ConnectedCallback {


    /** Use compression for headers. Will magically turn to false
     * if the first request doesn't have x8xx ( i.e. compress header )
     */
    boolean headerCompression = true;
    boolean firstFrame = true;

    public static long DICT_ID = 3751956914L;
    private static String SPDY_DICT_S =
        "optionsgetheadpostputdeletetraceacceptaccept-charsetaccept-encodingaccept-" +
        "languageauthorizationexpectfromhostif-modified-sinceif-matchif-none-matchi" +
        "f-rangeif-unmodifiedsincemax-forwardsproxy-authorizationrangerefererteuser" +
        "-agent10010120020120220320420520630030130230330430530630740040140240340440" +
        "5406407408409410411412413414415416417500501502503504505accept-rangesageeta" +
        "glocationproxy-authenticatepublicretry-afterservervarywarningwww-authentic" +
        "ateallowcontent-basecontent-encodingcache-controlconnectiondatetrailertran" +
        "sfer-encodingupgradeviawarningcontent-languagecontent-lengthcontent-locati" +
        "oncontent-md5content-rangecontent-typeetagexpireslast-modifiedset-cookieMo" +
        "ndayTuesdayWednesdayThursdayFridaySaturdaySundayJanFebMarAprMayJunJulAugSe" +
        "pOctNovDecchunkedtext/htmlimage/pngimage/jpgimage/gifapplication/xmlapplic" +
        "ation/xhtmltext/plainpublicmax-agecharset=iso-8859-1utf-8gzipdeflateHTTP/1" +
        ".1statusversionurl ";
    public static byte[] SPDY_DICT = SPDY_DICT_S.getBytes();
    // C code uses this - not in the spec
    static {
        SPDY_DICT[SPDY_DICT.length - 1] = (byte) 0;
    }


    protected static Logger log = Logger.getLogger("SpdyConnection");

    /**
     * @param spdyConnector
     * @param remoteServer
     */
    SpdyConnection(HttpConnector spdyConnector, RemoteServer remoteServer) {
        this.httpConnector = spdyConnector;
        this.remoteHost = remoteServer;
        this.target = remoteServer.target;
    }

    AtomicInteger streamErrors = new AtomicInteger();

    AtomicInteger lastInStream = new AtomicInteger();
    AtomicInteger lastOutStream = new AtomicInteger();

    // TODO: use int map
    Map<Integer, HttpChannel> channels = new HashMap();

    SpdyConnection.Frame currentInFrame = null;

    SpdyConnection.Frame lastFrame = null; // for debug

    BBuffer outFrameBuffer = BBuffer.allocate();
    BBuffer inFrameBuffer = BBuffer.allocate();

    BBuffer headW = BBuffer.wrapper();

    CompressFilter headCompressIn = new CompressFilter()
        .setDictionary(SPDY_DICT, DICT_ID);

    CompressFilter headCompressOut = new CompressFilter()
        .setDictionary(SPDY_DICT, DICT_ID);

    IOBuffer headerCompressBuffer = new IOBuffer();
    IOBuffer headerDeCompressBuffer = new IOBuffer();

    AtomicInteger inFrames = new AtomicInteger();
    AtomicInteger inDataFrames = new AtomicInteger();
    AtomicInteger inSyncStreamFrames = new AtomicInteger();
    AtomicInteger inBytes = new AtomicInteger();

    AtomicInteger outFrames = new AtomicInteger();
    AtomicInteger outDataFrames = new AtomicInteger();
    AtomicInteger outBytes = new AtomicInteger();


    volatile boolean connecting = false;
    volatile boolean connected = false;

    // TODO: detect if it's spdy or http based on bit 8

    @Override
    public void withExtraBuffer(BBuffer received) {
        inFrameBuffer = received;
    }

    @Override
    public void dataReceived(IOBuffer iob) throws IOException {
        // Only one thread doing receive at a time.
        synchronized (inFrameBuffer) {
            while (true) {
                int avail = iob.available();
                if (avail == 0) {
                    return;
                }
                if (currentInFrame == null) {
                    if (inFrameBuffer.remaining() + avail < 8) {
                        return;
                    }
                    if (inFrameBuffer.remaining() < 8) {
                        int headRest = 8 - inFrameBuffer.remaining();
                        int rd = iob.read(inFrameBuffer, headRest);
                    }
                    currentInFrame = new SpdyConnection.Frame(); // TODO: reuse
                    currentInFrame.parse(this, inFrameBuffer);
                }
                if (iob.available() < currentInFrame.length) {
                    return;
                }
                // We have a full frame. Process it.
                onFrame(iob);

                // TODO: extra checks, make sure the frame is correct and
                // it consumed all data.
                currentInFrame = null;
            }
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
        inFrames.incrementAndGet();
        inBytes.addAndGet(currentInFrame.length + 8);

        if (currentInFrame.c) {
            if (currentInFrame.type == SpdyConnection.Frame.TYPE_HELO) {
                // receivedHello = currentInFrame;
            } else if (currentInFrame.type == SpdyConnection.Frame.TYPE_SYN_STREAM) {
                inSyncStreamFrames.incrementAndGet();
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

                synchronized (channels) {
                        channels.put(ch.channelId, ch);
                }

                try {
                    // pri and unused
                    SpdyConnection.readShort(iob);

                    HttpMessageBytes reqBytes = ch.getRequest().getMsgBytes();

                    processHeaders(iob, ch, reqBytes);
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "Error parsing head", t);
                    abort("Error reading headers " + t);
                    return;
                }
                ch.getRequest().processReceivedHeaders();

                ch.handleHeadersReceived(ch.getRequest());

                if ((currentInFrame.flags & SpdyConnection.Frame.FLAG_HALF_CLOSE) != 0) {
                    ch.getIn().close();
                    ch.handleEndReceive();
                }
            } else if (currentInFrame.type == SpdyConnection.Frame.TYPE_SYN_REPLY) {
                int chId = SpdyConnection.readInt(iob);
                HttpChannel ch;
                synchronized (channels) {
                    ch = channels.get(chId);
                    if (ch == null) {
                        abort("Channel not found");
                    }
                }
                try {
                    SpdyConnection.readShort(iob);

                    HttpMessageBytes resBytes = ch.getResponse().getMsgBytes();

                    BBuffer head = processHeaders(iob, ch, resBytes);
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "Error parsing head", t);
                    abort("Error reading headers " + t);
                    return;
                }
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
            inDataFrames.incrementAndGet();
            // data frame - part of an existing stream
            HttpChannel ch;
            synchronized (channels) {
                ch = channels.get(currentInFrame.streamId);
            }
            if (ch == null) {
                log.warning("Unknown stream ");
                net.close();
                net.startSending();
                return;
            }
            int len = currentInFrame.length;
            while (len > 0) {
                BBucket bb = iob.peekFirst();
                if (bb == null) {
                    // we should have all data
                    abort("Unexpected short read");
                    return;
                }
                if (len > bb.remaining()) {
                    ch.getIn().append(bb);
                    len -= bb.remaining();
                    bb.position(bb.limit());
                } else {
                    ch.getIn().append(bb, len);
                    bb.position(bb.position() + len);
                    len = 0;
                }
            }
            ch.sendHandleReceivedCallback();

            if ((currentInFrame.flags & SpdyConnection.Frame.FLAG_HALF_CLOSE) != 0) {
                ch.handleEndReceive();
            }
        }
        firstFrame = false;
    }

    /**
     * On frame error.
     */
    private void abort(String msg) throws IOException {
        streamErrors.incrementAndGet();
        synchronized(channels) {
            for (HttpChannel ch : channels.values()) {
                ch.abort(msg);
            }
        }
        close();
    }

    private BBuffer processHeaders(IOBuffer iob, HttpChannel ch,
            HttpMessageBytes reqBytes) throws IOException {
        int nvCount = 0;
        if (firstFrame) {
            int res = iob.peek() & 0xFF;
            if ((res & 0x0F) !=  8) {
                headerCompression = false;
            }
        }
        headRecvBuf.recycle();
        if (headerCompression) {
            // 0x800 headers seems a bit too much - assume compressed.
            // I wish this was a flag...
            headerDeCompressBuffer.recycle();
            // stream id ( 4 ) + unused ( 2 )
            // nvCount is compressed in impl - spec is different
            headCompressIn.decompress(iob, headerDeCompressBuffer,
                    currentInFrame.length - 6);
            headerDeCompressBuffer.copyAll(headRecvBuf);
            headerDeCompressBuffer.recycle();
            nvCount = readShort(headRecvBuf);
        } else {
            nvCount = readShort(iob);
            // 8 = stream Id (4) + pri/unused (2) + nvCount (2)
            // we know we have enough data
            int rd = iob.read(headRecvBuf, currentInFrame.length - 8);
            if (rd != currentInFrame.length - 8) {
                abort("Unexpected incomplete read");
            }
        }
        // Wrapper - so we don't change position in head
        headRecvBuf.wrapTo(headW);

        BBuffer nameBuf = BBuffer.wrapper();
        BBuffer valBuf = BBuffer.wrapper();

        for (int i = 0; i < nvCount; i++) {

            int nameLen = SpdyConnection.readShort(headW);
            if (nameLen > headW.remaining()) {
                abort("Name too long");
            }

            nameBuf.setBytes(headW.array(), headW.position(),
                            nameLen);
            headW.advance(nameLen);

            int valueLen = SpdyConnection.readShort(headW);
            valBuf.setBytes(headW.array(), headW.position(),
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
        }
        return headW;
    }

    @Override
    protected synchronized void sendRequest(HttpChannel http) throws IOException {
        if (serverMode) {
            throw new IOException("Only in client mode");
        }
        if (!checkConnection(http)) {
            return;
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

        if (headerCompression && httpConnector.compression) {
            headerCompressBuffer.recycle();
            headCompressOut.compress(headBuf, headerCompressBuffer, false);
            headBuf.recycle();
            headerCompressBuffer.copyAll(headBuf);
        }

        // Frame head - 8
        BBuffer out = BBuffer.allocate();
        // Syn-reply
        out.putByte(0x80);
        out.putByte(0x01);
        out.putByte(0x00);
        out.putByte(0x01);

        CBuffer method = http.getRequest().method();
        if (method.equals("GET") || method.equals("HEAD")) {
            http.getOut().close();
        }

        if (http.getOut().isAppendClosed()) {
            out.putByte(0x01); // closed
        } else {
            out.putByte(0x00);
        }

        // Length, channel id (4) + unused (2) - headBuf has header count
        // and headers
        SpdyConnection.append24(out, headBuf.remaining() + 6);

        if (serverMode) {
            http.channelId = 2 * lastOutStream.incrementAndGet();
        } else {
            http.channelId = 2 * lastOutStream.incrementAndGet() + 1;
        }
        SpdyConnection.appendInt(out, http.channelId);

        http.setConnection(this);

        synchronized (channels) {
            channels.put(http.channelId, http);
        }

        out.putByte(0x00); // no priority
        out.putByte(0x00);

        sendFrame(out, headBuf);

        if (http.outMessage.state == HttpMessage.State.HEAD) {
            http.outMessage.state = HttpMessage.State.BODY_DATA;
        }
        if (http.getOut().isAppendClosed()) {
            http.handleEndSent();
        }

        // Any existing data
        //sendData(http);
    }


    public synchronized Collection<HttpChannel> getActives() {
        synchronized(channels) {
            return channels.values();
        }
    }

    @Override
    protected synchronized void sendResponseHeaders(HttpChannel http) throws IOException {
        if (!serverMode) {
            throw new IOException("Only in server mode");
        }

        if (http.getResponse().isCommitted()) {
            return;
        }
        http.getResponse().setCommitted(true);

        MultiMap mimeHeaders = http.getResponse().getMimeHeaders();

        BBuffer headBuf = BBuffer.allocate();


        //mimeHeaders.remove("content-length");
        BBuffer headers = headBuf;
        if (headerCompression) {
            headers = BBuffer.allocate();
        }

        //SpdyConnection.appendInt(headers, http.channelId);
        //headers.putByte(0);
        //headers.putByte(0);
        SpdyConnection.appendShort(headers, mimeHeaders.size() + 2);

        // chrome will crash if we don't send the header
        serializeMime(mimeHeaders, headers);

        // Must be at the end
        SpdyConnection.appendAsciiHead(headers, "status");
        SpdyConnection.appendAsciiHead(headers,
                Integer.toString(http.getResponse().getStatus()));

        SpdyConnection.appendAsciiHead(headers, "version");
        SpdyConnection.appendAsciiHead(headers, "HTTP/1.1");

        if (headerCompression) {
            headerCompressBuffer.recycle();
            headCompressOut.compress(headers, headerCompressBuffer, false);
            headerCompressBuffer.copyAll(headBuf);
            headerCompressBuffer.recycle();
        }

        BBuffer frameHead = BBuffer.allocate();
        // Syn-reply
        frameHead.putByte(0x80); // Control
        frameHead.putByte(0x01); // version
        frameHead.putByte(0x00); // 00 02 - SYN_REPLY
        frameHead.putByte(0x02);

        // It seems piggibacking data is not allowed
        frameHead.putByte(0x00);

        int len = headBuf.remaining() + 6;
        SpdyConnection.append24(frameHead, len);

//        // Stream-Id, unused
        SpdyConnection.appendInt(frameHead, http.channelId);
        frameHead.putByte(0);
        frameHead.putByte(0);

        sendFrame(frameHead, headBuf);
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
        outBytes.addAndGet(out.remaining());
        net.getOut().append(out);
        if (headBuf != null) {
            net.getOut().append(headBuf);
            outBytes.addAndGet(headBuf.remaining());
        }
        net.startSending();
        outFrames.incrementAndGet();
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

        outBytes.addAndGet(outFrameBuffer.remaining() + avail);
        net.getOut().append(outFrameBuffer);

        if (avail > 0) {
            net.getOut().append(out2, avail);
        }
        net.startSending();
        outDataFrames.incrementAndGet();
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

        public void parse(SpdyConnection spdyConnection,
                BBuffer iob) throws IOException {
            int b0 = iob.read();
            if (b0 < 128) {
                // data frame
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
                if (version != 1) {
                    spdyConnection.abort("Wrong version");
                    return;
                }
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

    @Override
    protected void endSendReceive(HttpChannel http) throws IOException {
        synchronized (channels) {
            HttpChannel doneHttp = channels.remove(http.channelId);
            if (doneHttp != http) {
                log.severe("Error removing " + doneHttp + " " + http);
            }
        }
        httpConnector.cpool.afterRequest(http, this, true);
    }

    /**
     * Framing error, client interrupt, etc.
     */
    public void abort(HttpChannel http, String t) throws IOException {
        // TODO: send interrupt signal

    }


    private boolean checkConnection(HttpChannel http) throws IOException {
        synchronized(this) {
            if (net == null || !isOpen()) {
                connected = false;
            }

            if (!connected) {
                if (!connecting) {
                    // TODO: secure set at start ?
                    connecting = true;
                    httpConnector.cpool.httpConnect(http,
                            target.toString(),
                            http.getRequest().isSecure(), this);
                }

                synchronized (remoteHost) {
                    remoteHost.pending.add(http);
                    httpConnector.cpool.queued.incrementAndGet();
                }
                return false;
            }
        }

        return true;
    }

    @Override
    public void handleConnected(IOChannel net) throws IOException {
        HttpChannel httpCh = null;
        if (!net.isOpen()) {
            while (true) {
                synchronized (remoteHost) {
                    if (remoteHost.pending.size() == 0) {
                        return;
                    }
                    httpCh = remoteHost.pending.remove();
                }
                httpCh.abort("Can't connect");
            }
        }

        synchronized (remoteHost) {
            httpCh = remoteHost.pending.peek();
        }
        if (httpCh != null) {
            secure = httpCh.getRequest().isSecure();
            if (secure) {
                if (httpConnector.debugHttp) {
                    net = DumpChannel.wrap("SPDY-SSL", net);
                }
                String[] hostPort = httpCh.getTarget().split(":");

                IOChannel ch1 = httpConnector.sslProvider.channel(net,
                        hostPort[0], Integer.parseInt(hostPort[1]));
                //net.setHead(ch1);
                net = ch1;
            }
        }
        if (httpConnector.debugHttp) {
            net = DumpChannel.wrap("SPDY", net);
        }

        setSink(net);

        synchronized(this) {
            connecting = false;
            connected = true;
        }

        while (true) {
            synchronized (remoteHost) {
                if (remoteHost.pending.size() == 0) {
                    return;
                }
                httpCh = remoteHost.pending.remove();
            }
            sendRequest(httpCh);
        }

    }
}