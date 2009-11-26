/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.Hex;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;

/**
 * Transport decoded buffer, representing the body 
 * of HTTP messages.
 * 
 * Supports:
 *  - Chunked and Content-Length delimitation
 *  - "Close" delimitation ( no content delimitation - TCP close )
 *  
 * TODO: continue support 
 * TODO: gzip encoding
 * 
 * For sending, data is kept in this buffer until flush() is called.
 */
class HttpBody extends IOBuffer {
    protected static Logger log = Logger.getLogger("HttpBody");

    static int DEFAULT_CHUNK_SIZE = 4096;

    private HttpChannel http;
    
    protected boolean chunked = false;
    protected long contentLength = -1; // C-L header
        
    /** True: Http/1.x + chunked || C-L - 
     *  False: for http/0.9, connection:close, errors. -> 
     *     close delimited 
     */    
    boolean frameError = false;


    /** Bytes remaining in the current chunk or body ( if CL ) */
    protected long remaining = 0; // both chunked and C-L
    
    // used for chunk parsing
    ChunkState chunk = new ChunkState();
    
    boolean noBody;

    boolean endSent = false;
    boolean sendBody = false;

    private HttpMessage httpMsg;
    
    HttpBody(HttpChannel asyncHttp, boolean sendBody) {
        this.http = asyncHttp;
        if (sendBody) {
            this.sendBody = true;
            // For flush and close to work - need to fix
            //this.ch = http;
        } else {
            this.ch = null;
        }
    }
    
    public String toString() {
        return "{" + super.toString() + " " + 
        (chunked ? "CNK/" + remaining : "") 
        + (contentLength >= 0 ? "C-L/" + contentLength + "/" + remaining : "")
        + (isAppendClosed() ? ", C" : "") 
        + "}";
    }
    
    public void recycle() {
        chunked = false;
        remaining = 0; 
        contentLength = -1;
        chunk.recycle();
        chunk.recycle();
        super.recycle();
        frameError = false;
        noBody = false;
        endSent = false;
    }
    
    public boolean isContentDelimited() {
        return chunked || contentLength >= 0;
    }

    

    /**
     * Updates chunked, contentLength, remaining
     */
    protected void processContentDelimitation() {

        contentLength = httpMsg.getContentLength();
        if (contentLength >= 0) {
            remaining = contentLength;
        }        
        
        // TODO: multiple transfer encoding headers, only process the last
        String transferEncodingValue = httpMsg.getHeader(HttpChannel.TRANSFERENCODING);
        if (transferEncodingValue != null) {
            int startPos = 0;
            int commaPos = transferEncodingValue.indexOf(',');
            String encodingName = null;
            while (commaPos != -1) {
                encodingName = transferEncodingValue.substring
                (startPos, commaPos).toLowerCase().trim();
                if ("chunked".equalsIgnoreCase(encodingName)) {
                    chunked = true;
                }
                startPos = commaPos + 1;
                commaPos = transferEncodingValue.indexOf(',', startPos);
            }
            encodingName = transferEncodingValue.substring(startPos)
                .toLowerCase().trim();
            if ("chunked".equals(encodingName)) {
                chunked = true;
                httpMsg.chunked = true;
            } else {
                System.err.println("TODO: ABORT 501");
                //return 501; // Currently only chunked is supported for 
                // transfer encoding.
            }
        }

        if (chunked) {
            remaining = 0;
        }
    }    
        
    void updateCloseOnEnd() {
        if (!isContentDelimited() && !noBody) {
            http.closeStreamOnEnd("not content delimited");
        }
    }
    
    void processContentEncoding() {
        // Content encoding - set it on the buffer, will be processed in blocking
        // mode, after transfer encoding.
//        MessageBytes contentEncodingValueMB =
//            headers.getValue("content-encoding");

//        if (contentEncodingValueMB != null) {
//            if (contentEncodingValueMB.equals("gzip")) {
//                buffer.addActiveFilter(gzipIF);
//            }
//            // TODO: other encoding filters
//            // TODO: this should be separate layer
//        }    
    }

    /**
     * Determine if we must drop the connection because of the HTTP status
     * code.  Use the same list of codes as Apache/httpd.
     */
    protected boolean statusDropsConnection(int status) {
        return status == 400 /* SC_BAD_REQUEST */ ||
        status == 408 /* SC_REQUEST_TIMEOUT */ ||
        status == 411 /* SC_LENGTH_REQUIRED */ ||
        status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
        status == 414 /* SC_REQUEST_URI_TOO_LARGE */ ||
        status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
        status == 503 /* SC_SERVICE_UNAVAILABLE */ ||
        status == 501 /* SC_NOT_IMPLEMENTED */;
    }
    
    static final int NEED_MORE = -1;
    static final int ERROR = -4;
    static final int DONE = -5;
    
    class ChunkState {
        int partialChunkLen;
        boolean readDigit = false;
        boolean trailer = false;
        protected boolean needChunkCrlf = false;
        
        // Buffer used for chunk length conversion.
        protected byte[] sendChunkLength = new byte[10];

        /** End chunk marker - will include chunked end or empty */
        protected BBuffer endSendBuffer = BBuffer.wrapper();

        public ChunkState() {
            sendChunkLength[8] = (byte) '\r';
            sendChunkLength[9] = (byte) '\n';            
        }
        
        void recycle() {
            partialChunkLen = 0;
            readDigit = false;
            trailer = false;
            needChunkCrlf = false;
            endSendBuffer.recycle();
        }
        
        /**
         * Parse the header of a chunk.
         * A chunk header can look like 
         * A10CRLF
         * F23;chunk-extension to be ignoredCRLF
         * The letters before CRLF but after the trailer mark, must be valid hex digits, 
         * we should not parse F23IAMGONNAMESSTHISUP34CRLF as a valid header
         * according to spec
         */
        int parseChunkHeader(IOBuffer buffer) throws IOException {
            if (buffer.peekFirst() == null) {
                return NEED_MORE;
            }
            if (needChunkCrlf) {
                // TODO: Trailing headers
                int c = buffer.read();
                if (c == BBuffer.CR) {
                    if (buffer.peekFirst() == null) {
                        return NEED_MORE;
                    }
                    c = buffer.read();
                }
                if (c == BBuffer.LF) {
                    needChunkCrlf = false;
                } else {
                    System.err.println("Bad CRLF " + c);
                    return ERROR;
                }
            }

            while (true) {
                if (buffer.peekFirst() == null) {
                    return NEED_MORE;
                }
                int c = buffer.read();

                if (c == BBuffer.CR) {
                    continue;
                } else if (c == BBuffer.LF) {
                    break;
                } else if (c == HttpChannel.SEMI_COLON) {
                    trailer = true;
                } else if (c == BBuffer.SP) {
                    // ignore
                } else if (trailer) {
                    // ignore
                } else {
                    //don't read data after the trailer
                    if (Hex.DEC[c] != -1) {
                        readDigit = true;
                        partialChunkLen *= 16;
                        partialChunkLen += Hex.DEC[c];
                    } else {
                        //we shouldn't allow invalid, non hex characters
                        //in the chunked header
                        log.info("Chunk parsing error1 " + c + " " + buffer);
                        http.abort("Chunk error");
                        return ERROR;
                    }
                }
            }

            if (!readDigit) {
                log.info("Chunk parsing error2 " + buffer);
                return ERROR;
            }

            needChunkCrlf = true;  // next time I need to parse CRLF
            int result = partialChunkLen;
            partialChunkLen = 0;
            trailer = false;
            readDigit = false;
            return result;
        }
        

        ByteBuffer prepareChunkHeader(int current) {
            int pos = 7; // 8, 9 are CRLF
            while (current > 0) {
                int digit = current % 16;
                current = current / 16;
                sendChunkLength[pos--] = Hex.HEX[digit];
            }
            if (needChunkCrlf) {
                sendChunkLength[pos--] = (byte) '\n';
                sendChunkLength[pos--] = (byte) '\r';
            } else {
                needChunkCrlf = true;
            }
            // TODO: pool - this may stay in the queue while we flush more
            ByteBuffer chunkBB = ByteBuffer.allocate(16);
            chunkBB.put(sendChunkLength, pos + 1, 9 - pos);
            chunkBB.flip();
            return chunkBB;
        }

        public BBuffer endChunk() {
            if (! needChunkCrlf) { 
                endSendBuffer.setBytes(HttpChannel.END_CHUNK_BYTES, 2, 
                        HttpChannel.END_CHUNK_BYTES.length - 2); // CRLF
            } else { // 0
                endSendBuffer.setBytes(HttpChannel.END_CHUNK_BYTES, 0, 
                        HttpChannel.END_CHUNK_BYTES.length);                
            }
            return endSendBuffer;
        }
    }
    
    private int receiveDone(boolean frameError) throws IOException {
        // Content-length case, we're done reading
        close();
        
        this.frameError = frameError;
        if (frameError) {
            http.closeStreamOnEnd("frame error");
        }

        return DONE;        
    }
    
    /** 
     * Called when raw body data is received.
     * Callback should not consume past the end of the body.
     * @param rawReceiveBuffers 
     *  
     */
    void rawDataReceived(IOBuffer rawReceiveBuffers) throws IOException {
        // TODO: Make sure we don't process more than we need ( eat next req ).
        // If we read too much: leave it in readBuf, the finalzation code
        // should skip KeepAlive and start processing it.
        // we need to read at least something - to detect -1 ( we could 
        // suspend right away, but seems safer.
        while (http.inMessage.state == HttpMessage.State.BODY_DATA) {
            //log.info("RAW DATA: " + this + " RAW: " + rawReceiveBuffers);
            if (noBody) {
                receiveDone(false);
                return;
            }
            if (rawReceiveBuffers.isClosedAndEmpty()) {
                if (isContentDelimited()) {
                    if (contentLength >= 0 && remaining == 0) {
                        receiveDone(false);
                    } else {
                        // End of input - other side closed, no more data
                        //log.info("CLOSE while reading " + this);    
                        // they're not supposed to close !
                        receiveDone(true);
                    }
                } else {
                    receiveDone(false); // ok
                }
                // input connection closed ? 
                http.closeStreamOnEnd("Closed input");
                return;
            }
            BBucket rawBuf = rawReceiveBuffers.peekFirst();
            if (rawBuf == null) {
                return;  // need more data                 
            }

            if (!isContentDelimited()) {
                while (true) {
                    BBucket first = rawReceiveBuffers.popFirst();
                    if (first == null) {
                        break; // will go back to check if done.
                    } else {
                        super.queue(first);
                    }
                }
            } else {
                
                if (contentLength >= 0 && remaining == 0) {
                    receiveDone(false);
                    return;
                }

                if (chunked && remaining == 0) {
                    int rc = NEED_MORE;
                    while (rc == NEED_MORE) {
                        rc = chunk.parseChunkHeader(rawReceiveBuffers);
                        if (rc == ERROR) {
                            receiveDone(true);
                            return;
                        } else if (rc == NEED_MORE) {
                            return;
                        }
                    }
                    if (rc == 0) { // last chunk
                        receiveDone(false);
                        return;
                    } else {
                        remaining = rc;
                    }
                }

                rawBuf = (BBucket) rawReceiveBuffers.peekFirst();
                if (rawBuf == null) {
                    return;  // need more data                 
                }
                

                if (remaining < rawBuf.remaining()) {
                    // To buffer has more data than we need.
                    int lenToConsume = (int) remaining;
                    BBucket sb = rawReceiveBuffers.popLen(lenToConsume);
                    super.queue(sb);
                    //log.info("Queue received buffer " + this + " " + lenToConsume);
                    remaining = 0;
                } else {
                    BBucket first = rawReceiveBuffers.popFirst();
                    remaining -= first.remaining();
                    super.queue(first);
                    //log.info("Queue full received buffer " + this + " RAW: " + rawReceiveBuffers);
                }
                if (contentLength >= 0 && remaining == 0) {
                    // Content-Length, all done
                    super.close();
                    receiveDone(false);
                }
            }
        }
    }
    
    void flushToNext() throws IOException {
        http.sendHeaders(); // if needed

        if (getNet() == null) {
            return; // not connected yet.
        }
        
        synchronized (this) {
            if (noBody) {
                for (int i = 0; i < super.getBufferCount(); i++) {
                    Object bc = super.peekBucket(i);
                    if (bc instanceof BBucket) {
                        ((BBucket) bc).release();
                    }
                }                    
                super.clear();
                return;
            }
            // TODO: only send < remainingWrite, if buffer
            // keeps changing after startWrite() is called (shouldn't)
            boolean done = false;
            
            if (chunked) {
                done = sendChunked();
            } else if (contentLength >= 0) {
                // content-length based
                done = sendContentLen();
            } else {
                // Close delimitation
                while (true) {
                    Object bc = popFirst();
                    if (bc == null) {
                        break;
                    }
                    getNet().getOut().queue(bc);
                }
                if (super.isClosedAndEmpty()) {
                    done = true;
                    if (getNet() != null) {
                        getNet().getOut().close(); // no content-delimitation
                    }
                }
            }
        }
    }

    private boolean sendContentLen() throws IOException {
        while (true) {
            BBucket bucket = super.peekFirst();
            if (bucket == null) {
                break;
            }
            int len = bucket.remaining();
            if (len <= remaining) {
                remaining -= len;
                bucket = super.popFirst();
                getNet().getOut().queue(bucket);
            } else {
                // Write over the end of the buffer !
                log.severe(http.dbgName + 
                        ": write more than Content-Length");
                len = (int) remaining;
                // data between position and limit
                bucket = popLen((int) remaining); 
                getNet().getOut().queue(bucket);
                while (bucket != null) {
                    bucket = super.popFirst();
                    if (bucket != null) {
                        bucket.release();
                    }
                }
                
                // forced close
                //close();
                remaining = 0;
                return true;
            }
        }
        if (super.isClosedAndEmpty()) {
            //http.rawSendBuffers.queue(IOBrigade.MARK);
            if (remaining > 0) {
                http.closeStreamOnEnd("sent more than content-length");
                log.severe("Content-Length > body");
            }
            return true;
        }
        return false;
    }
    
    public void close() throws IOException {
        if (sendBody && !http.error) {
            flushToNext(); // will send any remaining data.
        }

        if (isContentDelimited() && !http.error) {
            if (!chunked && remaining > 0) {
                log.severe("CLOSE CALLED WITHOUT FULL LEN");
                // TODO: abort ?
            } else {
                super.close();
                if (sendBody) {
                    flushToNext(); // will send '0'
                }
            }
        } else {
            super.close();
            if (sendBody) {
                flushToNext();
            }
        }
    }
    
    private boolean sendChunked() throws IOException {
        int len = 0;
        int cnt = super.getBufferCount();
        for (int i = 0; i < cnt; i++) {
            BBucket iob = super.peekBucket(i);
            len += iob.remaining();
        }

        if (len > 0) {
            ByteBuffer sendChunkBuffer = chunk.prepareChunkHeader(len); 
            remaining = len;
            getNet().getOut().queue(sendChunkBuffer);
            while (cnt > 0) {
                Object bc = super.popFirst();
                getNet().getOut().queue(bc);
                cnt --;
            }
        }

        if (super.isClosedAndEmpty()) {
            if (!endSent) {
                getNet().getOut().append(chunk.endChunk());
                endSent = true;
            }
            //http.rawSendBuffers.queue(IOBrigade.MARK);
            return true;
        } else {
            return false;
        }
    }

    boolean isDone() {
        if (noBody) {
            return true;
        }
        if (isContentDelimited()) {
            if (!chunked && remaining == 0) {
                return true;
            } else if (chunked && super.isAppendClosed()) {
                return true;
            }
        }
        return false;
    }

    IOChannel getNet() {
        return http.getNet();
    }

    public void setMessage(HttpMessage httpMsg) {
        this.httpMsg = httpMsg;
    }
}
