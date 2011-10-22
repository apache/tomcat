/*
 */
package org.apache.tomcat.lite.io.jsse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.SslProvider;

class SslChannel extends IOChannel implements Runnable {

    static Logger log = Logger.getLogger("SSL");

    static ByteBuffer EMPTY = ByteBuffer.allocate(0);


    SSLEngine sslEngine;
    // Last result
    SSLEngineResult unwrapR;

    boolean handshakeDone = false;
    boolean handshakeInProgress = false;
    Object handshakeSync = new Object();
    boolean flushing = false;

    IOBuffer in = new IOBuffer(this);
    IOBuffer out = new IOBuffer(this);

    long handshakeTimeout = 20000;
    // Used for session reuse
    String host;
    int port;

    ByteBuffer myAppOutData;
    ByteBuffer myNetOutData;
    private static boolean debugWrap = false;

    /*
     * Special: SSL works in packet mode, and we may receive an incomplete
     * packet. This should be in compacted write mode (i.e. data from 0 to pos,
     * limit at end )
     */
    ByteBuffer myNetInData;
    ByteBuffer myAppInData;
    boolean client = true;

    private SSLContext sslCtx;

    private boolean closeHandshake = false;

    public SslChannel() {
    }

    /**
     * Setting the host/port enables clients to reuse SSL session -
     * less traffic and encryption overhead at startup, assuming the
     * server caches the session ( i.e. single server or distributed cache ).
     *
     * SSL ticket extension is another possibility.
     */
    public SslChannel setTarget(String host, int port) {
        this.host = host;
        this.port = port;
        return this;
    }

    private synchronized void initSsl() throws GeneralSecurityException {
        if (sslEngine != null) {
            log.severe("Double initSsl");
            return;
        }

        if (client) {
            if (port > 0) {
                sslEngine = sslCtx.createSSLEngine(host, port);
            } else {
                sslEngine = sslCtx.createSSLEngine();
            }
            sslEngine.setUseClientMode(client);
        } else {
            sslEngine = sslCtx.createSSLEngine();
            sslEngine.setUseClientMode(false);

        }

        // Some VMs have broken ciphers.
        if (JsseSslProvider.enabledCiphers != null) {
            sslEngine.setEnabledCipherSuites(JsseSslProvider.enabledCiphers);
        }

        SSLSession session = sslEngine.getSession();

        int packetBuffer = session.getPacketBufferSize();
        myAppOutData = ByteBuffer.allocate(session.getApplicationBufferSize());
        myNetOutData = ByteBuffer.allocate(packetBuffer);
        myAppInData = ByteBuffer.allocate(session.getApplicationBufferSize());
        myNetInData = ByteBuffer.allocate(packetBuffer);
        myNetInData.flip();
        myNetOutData.flip();
        myAppInData.flip();
        myAppOutData.flip();
    }

    public SslChannel withServer() {
        client = false;
        return this;
    }


    @Override
    public synchronized void setSink(IOChannel net) throws IOException {
        try {
            if (sslEngine == null) {
                initSsl();
            }
            super.setSink(net);
        } catch (GeneralSecurityException e) {
            log.log(Level.SEVERE, "Error initializing ", e);
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
     * Typically called when a dataReceived callback is passed up.
     * It's up to the higher layer to decide if it can handle more data
     * and disable read interest and manage its buffers.
     *
     * We have to use one buffer.
     * @throws IOException
     */
    public int processInput(IOBuffer netIn, IOBuffer appIn) throws IOException {
        if (log.isLoggable(Level.FINEST)) {
            log.info("JSSE: processInput " + handshakeInProgress + " " + netIn.getBufferCount());
        }
        synchronized(handshakeSync) {
            if (!handshakeDone && !handshakeInProgress) {
                handshakeInProgress = true;
                handleHandshking();
                return 0;
            }
            if (handshakeInProgress) {
                return 0; // leave it there
            }
        }
        return processRealInput(netIn, appIn);
    }

    private synchronized int processRealInput(IOBuffer netIn, IOBuffer appIn) throws IOException {
        int rd = 0;
        boolean needsMore = true;
        boolean notEnough = false;

        while (needsMore) {
            if (netIn.isClosedAndEmpty()) {
                appIn.close();
                sendHandleReceivedCallback();
                return -1;
            }
            myNetInData.compact();
            int rdNow;
            try {
                rdNow = netIn.read(myNetInData);
            } finally {
                myNetInData.flip();
            }
            if (rdNow == 0 && (myNetInData.remaining() == 0 ||
                    notEnough)) {
                return rd;
            }
            if (rdNow == -1) {
                appIn.close();
                sendHandleReceivedCallback();
                return rd;
            }

            notEnough = true; // next read of 0
            while (myNetInData.remaining() > 0) {
                myAppInData.compact();
                try {
                    unwrapR = sslEngine.unwrap(myNetInData, myAppInData);
                } catch (SSLException ex) {
                    log.warning("Read error: " + ex);
                    close();
                    return -1;
                } finally {
                    myAppInData.flip();
                }
                if (myAppInData.remaining() > 0) {
                    in.write(myAppInData); // all will be written
                }
                if (unwrapR.getStatus() == Status.CLOSED) {
                    in.close();
                    if (unwrapR.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                        // TODO: send/receive one more packet ( handshake mode ? )
                        synchronized(handshakeSync) {
                            handshakeInProgress = true;
                            closeHandshake  = true;
                        }
                        handleHandshking();

                        startSending();
                    }
                    break;
                }

                if (unwrapR.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    tasks();
                }
                if (unwrapR.getStatus() == Status.BUFFER_OVERFLOW) {
                    log.severe("Unhandled overflow " + unwrapR);
                    break;
                }
                if (unwrapR.getStatus() == Status.BUFFER_UNDERFLOW) {
                    // harmless
                    //log.severe("Unhandled underflow " + unwrapR);
                    break;
                }
            }
            sendHandleReceivedCallback();


        }
        return rd;
    }

    protected SSLEngineResult.HandshakeStatus tasks() {
        Runnable r = null;
        while ( (r = sslEngine.getDelegatedTask()) != null) {
            r.run();
        }
        return sslEngine.getHandshakeStatus();
    }

    public void startSending() throws IOException {

        flushing = true;
        boolean needHandshake = false;
        synchronized(handshakeSync) {
            if (handshakeInProgress) {
                return; // don't bother me.
            }
            if (!handshakeDone) {
                handshakeInProgress = true;
                needHandshake = true;
            }
        }
        if (needHandshake) {
            handleHandshking();
            return; // can't write yet.
        }

        startRealSending();
    }

    public void close() throws IOException {
        if (net.getOut().isAppendClosed()) {
            return;
        }
        sslEngine.closeOutbound(); // mark as closed
        synchronized(myNetOutData) {
            myNetOutData.compact();

            SSLEngineResult wrap;
            try {
                wrap = sslEngine.wrap(EMPTY, myNetOutData);
                if (wrap.getStatus() != Status.CLOSED) {
                    log.warning("Unexpected close status " + wrap);
                }
            } catch (Throwable t ) {
                log.info("Error wrapping " + myNetOutData);
            } finally {
                myNetOutData.flip();
            }
            if (myNetOutData.remaining() > 0) {
                net.getOut().write(myNetOutData);
            }
        }
        // TODO: timer to close socket if we don't get
        // clean close handshake
        super.close();
    }

    private Object sendLock = new Object();

    private JsseSslProvider sslProvider;

    private void startRealSending() throws IOException {
        // Only one thread at a time
        synchronized (sendLock) {
            while (true) {

                myAppOutData.compact();
                int rd;
                try {
                    rd = out.read(myAppOutData);
                } finally {
                    myAppOutData.flip();
                }
                if (rd == 0) {
                    break;
                }
                if (rd < 0) {
                    close();
                    break;
                }

                SSLEngineResult wrap;
                synchronized(myNetOutData) {
                    myNetOutData.compact();
                    try {
                        wrap = sslEngine.wrap(myAppOutData,
                                myNetOutData);
                    } finally {
                        myNetOutData.flip();
                    }
                    net.getOut().write(myNetOutData);
                }
                if (wrap != null) {
                    switch (wrap.getStatus()) {
                    case BUFFER_UNDERFLOW: {
                        break;
                    }
                    case OK: {
                        break;
                    }
                    case BUFFER_OVERFLOW: {
                        throw new IOException("Overflow");
                    }
                    }
                }
            }
        }

        net.startSending();
    }


    // SSL handshake require slow tasks - that will need to be executed in a
    // thread anyways. Better to keep it simple ( the code is very complex ) -
    // and do the initial handshake in a thread, not in the IO thread.
    // We'll need to unregister and register again from the selector.
    private void handleHandshking() {
        if (log.isLoggable(Level.FINEST)) {
            log.info("Starting handshake");
        }
        synchronized(handshakeSync) {
            handshakeInProgress = true;
        }

        sslProvider.handshakeExecutor.execute(this);
    }

    private void endHandshake() throws IOException {
        if (log.isLoggable(Level.FINEST)) {
            log.info("Handshake done " + net.getIn().available());
        }
        synchronized(handshakeSync) {
            handshakeDone = true;
            handshakeInProgress = false;
        }
        if (flushing) {
            flushing = false;
            startSending();
        }
        if (myNetInData.remaining() > 0 || net.getIn().available() > 0) {
            // Last SSL packet also includes data.
            handleReceived(net);
        }
    }

    /**
     * Actual handshake magic, in background thread.
     */
    public void run() {
        try {
            boolean initial = true;
            SSLEngineResult wrap = null;

            HandshakeStatus hstatus = sslEngine.getHandshakeStatus();
            if (!closeHandshake &&
                    (hstatus == HandshakeStatus.NOT_HANDSHAKING || initial)) {
                sslEngine.beginHandshake();
                hstatus = sslEngine.getHandshakeStatus();
            }

            long t0 = System.currentTimeMillis();

            while (hstatus != HandshakeStatus.NOT_HANDSHAKING
                    && hstatus != HandshakeStatus.FINISHED
                    && !net.getIn().isAppendClosed()) {
                if (System.currentTimeMillis() - t0 > handshakeTimeout) {
                    throw new TimeoutException();
                }
                if (wrap != null && wrap.getStatus() == Status.CLOSED) {
                    break;
                }
                if (log.isLoggable(Level.FINEST)) {
                    log.info("-->doHandshake() loop: status = " + hstatus + " " +
                            sslEngine.getHandshakeStatus());
                }

                if (hstatus == HandshakeStatus.NEED_WRAP) {
                    // || initial - for client
                    initial = false;
                    synchronized(myNetOutData) {
                        while (hstatus == HandshakeStatus.NEED_WRAP) {
                            myNetOutData.compact();
                            try {
                                wrap = sslEngine.wrap(myAppOutData, myNetOutData);
                            } catch (Throwable t) {
                                log.log(Level.SEVERE, "Wrap error", t);
                                close();
                                return;
                            } finally {
                                myNetOutData.flip();
                            }
                            if (myNetOutData.remaining() > 0) {
                                net.getOut().write(myNetOutData);
                            }
                            hstatus = wrap.getHandshakeStatus();
                        }
                    }
                    net.startSending();
                } else if (hstatus == HandshakeStatus.NEED_UNWRAP) {
                    while (hstatus == HandshakeStatus.NEED_UNWRAP) {
                        // If we have few remaining bytes - process them
                        if (myNetInData.remaining() > 0) {
                            myAppInData.clear();
                            if (debugWrap) {
                                log.info("UNWRAP: rem=" + myNetInData.remaining());
                            }
                            wrap = sslEngine.unwrap(myNetInData, myAppInData);
                            hstatus = wrap.getHandshakeStatus();
                            myAppInData.flip();
                            if (myAppInData.remaining() > 0) {
                                log.severe("Unexpected data after unwrap");
                            }
                            if (wrap.getStatus() == Status.CLOSED) {
                                break;
                            }
                        }
                        // Still need unwrap
                        if (wrap == null
                                || wrap.getStatus() == Status.BUFFER_UNDERFLOW
                                || (hstatus == HandshakeStatus.NEED_UNWRAP && myNetInData.remaining() == 0)) {
                            myNetInData.compact();
                            // non-blocking
                            int rd;
                            try {
                                rd = net.getIn().read(myNetInData);
                                if (debugWrap) {
                                    log.info("Read: " + rd);
                                }
                            } finally {
                                myNetInData.flip();
                            }
                            if (rd == 0) {
                                if (debugWrap) {
                                    log.info("Wait: " + handshakeTimeout);
                                }
                                net.getIn().waitData(handshakeTimeout);
                                rd = net.getIn().read(myNetInData);
                                if (debugWrap) {
                                    log.info("Read after wait: " + rd);
                                }
                            }
                            if (rd < 0) {
                                // in closed
                                break;
                            }
                        }
                        if (log.isLoggable(Level.FINEST)) {
                            log.info("Unwrap chunk done " + hstatus + " " + wrap
                                + " " + sslEngine.getHandshakeStatus());
                        }

                    }

                    // rd may have some input bytes.
                } else if (hstatus == HandshakeStatus.NEED_TASK) {
                    long t0task = System.currentTimeMillis();
                    Runnable r;
                    while ((r = sslEngine.getDelegatedTask()) != null) {
                        r.run();
                    }
                    long t1task = System.currentTimeMillis();
                    hstatus = sslEngine.getHandshakeStatus();
                    if (log.isLoggable(Level.FINEST)) {
                        log.info("Tasks done in " + (t1task - t0task) + " new status " +
                                hstatus);
                    }

                }
                if (hstatus == HandshakeStatus.NOT_HANDSHAKING) {
                    //log.warning("NOT HANDSHAKING " + this);
                    break;
                }
            }
            endHandshake();
            processRealInput(net.getIn(), in);
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Error handshaking", t);
            try {
                close();
                net.close();
                sendHandleReceivedCallback();
            } catch (IOException ex) {
                log.log(Level.SEVERE, "Error closing", ex);
            }
        }
    }


    @Override
    public void handleReceived(IOChannel ch) throws IOException {
        processInput(net.getIn(), in);
        // Maybe we don't have data - that's fine.
        sendHandleReceivedCallback();
    }

    SslChannel setSslContext(SSLContext sslCtx) {
        this.sslCtx = sslCtx;
        return this;
    }

    SslChannel setSslProvider(JsseSslProvider con) {
        this.sslProvider = con;
        return this;
    }

    public Object getAttribute(String name) {
        if (SslProvider.ATT_SSL_CERT.equals(name)) {
            try {
                return sslEngine.getSession().getPeerCertificateChain();
            } catch (SSLPeerUnverifiedException e) {
                return null; // no re-negotiation
            }
        } else if (SslProvider.ATT_SSL_CIPHER.equals(name)) {
            return sslEngine.getSession().getCipherSuite();
        } else if (SslProvider.ATT_SSL_KEY_SIZE.equals(name)) {
            // looks like we need to get it from the string cipher
            CipherData c_aux[] = ciphers;

            int size = 0;
            String cipherSuite = sslEngine.getSession().getCipherSuite();
            for (int i = 0; i < c_aux.length; i++) {
                if (cipherSuite.indexOf(c_aux[i].phrase) >= 0) {
                    size = c_aux[i].keySize;
                    break;
                }
            }
            return size;
        } else if (SslProvider.ATT_SSL_SESSION_ID.equals(name)) {
            byte [] ssl_session = sslEngine.getSession().getId();
            if ( ssl_session == null)
                return null;
            StringBuilder buf=new StringBuilder();
            for(int x=0; x<ssl_session.length; x++) {
                String digit=Integer.toHexString(ssl_session[x]);
                if (digit.length()<2) buf.append('0');
                if (digit.length()>2) digit=digit.substring(digit.length()-2);
                buf.append(digit);
            }
            return buf.toString();
        }

        if (net != null) {
            return net.getAttribute(name);
        }
        return null;
    }


     /**
      * Simple data class that represents the cipher being used, along with the
      * corresponding effective key size.  The specified phrase must appear in the
      * name of the cipher suite to be recognized.
      */

     static final class CipherData {

         public String phrase = null;

         public int keySize = 0;

         public CipherData(String phrase, int keySize) {
             this.phrase = phrase;
             this.keySize = keySize;
         }

     }


     /**
      * A mapping table to determine the number of effective bits in the key
      * when using a cipher suite containing the specified cipher name.  The
      * underlying data came from the TLS Specification (RFC 2246), Appendix C.
      */
      static final CipherData ciphers[] = {
         new CipherData("_WITH_NULL_", 0),
         new CipherData("_WITH_IDEA_CBC_", 128),
         new CipherData("_WITH_RC2_CBC_40_", 40),
         new CipherData("_WITH_RC4_40_", 40),
         new CipherData("_WITH_RC4_128_", 128),
         new CipherData("_WITH_DES40_CBC_", 40),
         new CipherData("_WITH_DES_CBC_", 56),
         new CipherData("_WITH_3DES_EDE_CBC_", 168),
         new CipherData("_WITH_AES_128_CBC_", 128),
         new CipherData("_WITH_AES_256_CBC_", 256)
     };

}
