/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;


public class SslChannel extends IOChannel implements Runnable {

    static Logger log = Logger.getLogger("SSL");

    SSLEngine sslEngine;
    // Last result
    SSLEngineResult unwrapR;
    
    boolean handshakeDone = false;
    boolean handshakeInProgress = false;
    boolean flushing = false;
    
    IOBuffer in = new IOBuffer(this);
    IOBuffer out = new IOBuffer(this);
    
    public SslChannel() {
        
    }
    
    ByteBuffer myAppOutData;
    ByteBuffer myNetOutData; 

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
    
    private void initSsl() throws GeneralSecurityException {
        if (sslEngine != null) {
            return;
        }
        
        if (client) {
            sslEngine = sslCtx.createSSLEngine();
            sslEngine.setUseClientMode(client);
        } else {
            sslEngine = sslCtx.createSSLEngine();
            sslEngine.setUseClientMode(false);
            String[] cs = sslEngine.getEnabledCipherSuites();
            cs =sslEngine.getSupportedCipherSuites();
            
        }
        SSLSession session = sslEngine.getSession();
    
        myAppOutData = ByteBuffer.allocate(session.getApplicationBufferSize());
        myNetOutData = ByteBuffer.allocate(session.getPacketBufferSize());
        myAppInData = ByteBuffer.allocate(session.getApplicationBufferSize());
        myNetInData = ByteBuffer.allocate(session.getPacketBufferSize());
        myNetInData.flip();
        myNetOutData.flip();
        myAppInData.flip();
        myAppOutData.flip();
        
        // TODO: enable anon suites
        //sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());
    }
    
    public SslChannel withServer() {
        client = false;
        return this;
    }
    
    
    @Override
    public void setSink(IOChannel net) throws IOException {
        try {
            initSsl();
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
            log.finest("JSSE: processInput " + handshakeInProgress + " " + netIn.getBufferCount());
        }
        if (!handshakeDone && !handshakeInProgress) {
            handshakeInProgress = true;
            handleHandshking();
            return 0; 
        }
        if (handshakeInProgress) {
            return 0; // leave it there
        }
        return processRealInput(netIn, appIn);
    }
    
    private synchronized int processRealInput(IOBuffer netIn, IOBuffer appIn) throws IOException {
        int rd = 0;
        boolean needsMore = true;

        while (needsMore) {
            if (netIn.isClosedAndEmpty()) {
                appIn.close();
                sendHandleReceivedCallback();
                return -1;
            }
            myNetInData.compact();
            int rdNow = netIn.read(myNetInData);
            myNetInData.flip();
            if (rdNow == 0) {
                return rd;
            }
            if (rdNow == -1) {
                appIn.close();
                sendHandleReceivedCallback();
                return rd;
            }

            while (myNetInData.remaining() > 0) {
                myAppInData.compact();
                unwrapR = sslEngine.unwrap(myNetInData, myAppInData);
                myAppInData.flip();
                if (myAppInData.remaining() > 0) {
                    in.write(myAppInData); // all will be written
                }
                if (unwrapR.getStatus() == Status.CLOSED) {
                    in.close();
                    if (unwrapR.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                        // TODO: send/receive one more packet ( handshake mode ? )
                        handshakeInProgress = true;
                        closeHandshake  = true;
                        handleHandshking();
                        
                        startSending();
                    }
                    break;
                }
                
                if (unwrapR.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    tasks();                    
                }
                if (unwrapR.getStatus() == Status.BUFFER_OVERFLOW ||
                        unwrapR.getStatus() == Status.BUFFER_UNDERFLOW) {
                    log.severe("Unhandled overflow");
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

    static ByteBuffer EMPTY = ByteBuffer.allocate(0);
    
    public void startSending() throws IOException {
        
        flushing = true;

        if (handshakeInProgress) {
            return; // don't bother me.
        }
        
        if (!handshakeDone) {
            handshakeInProgress = true;
            handleHandshking();
            return; // can't write yet.
        }
        startRealSending();
    }
    
    public void close() throws IOException {
        sslEngine.closeOutbound(); // mark as closed
        myNetOutData.compact();
        SSLEngineResult wrap = sslEngine.wrap(EMPTY, 
                myNetOutData);
        myNetOutData.flip();
        if (wrap.getStatus() != Status.CLOSED) {
            System.err.println("Unexpected status " + wrap);
        }
        net.getOut().write(myNetOutData);
        // TODO: timer to close socket if we don't get
        // clean close handshake
    }
    
    private synchronized void startRealSending() throws IOException {
        while (true) {
        
            myAppOutData.compact();
            int rd = out.read(myAppOutData);
            myAppOutData.flip();
            if (rd == 0) {
                break;
            }
            if (rd < 0) {
                close();
                break;
            }
    
            myNetOutData.compact();
            SSLEngineResult wrap = sslEngine.wrap(myAppOutData, 
                    myNetOutData);
            myNetOutData.flip();
            net.getOut().write(myNetOutData);
                        
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
        
        net.startSending();
    }


    
    // SSL handshake require slow tasks - that will need to be executed in a 
    // thread anyways. Better to keep it simple ( the code is very complex ) - 
    // and do the initial handshake in a thread, not in the IO thread.
    // We'll need to unregister and register again from the selector.
    private void handleHandshking() { 
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Starting handshake");
        }
        handshakeInProgress = true;

        new Thread(this).start();
    }
    
    private void endHandshake() throws IOException {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Handshake done");
        }
        handshakeDone = true;
        handshakeInProgress = false;
        if (flushing) {
            flushing = false;
            startSending();
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
            
            while (hstatus != HandshakeStatus.FINISHED) {
                if (wrap != null && wrap.getStatus() == Status.CLOSED) {
                    break;
                }
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("-->doHandshake() loop: status = " + hstatus + " " +
                            sslEngine.getHandshakeStatus());
                }
                
                if (hstatus == HandshakeStatus.NEED_WRAP) {
                    // || initial - for client
                    initial = false;
                    myNetOutData.compact();
                    
                    wrap = sslEngine.wrap(myAppOutData, myNetOutData);
                    myNetOutData.flip();
                    
                    hstatus = wrap.getHandshakeStatus();

                    net.getOut().write(myNetOutData);
                    net.startSending();

                    
                } else if (hstatus == HandshakeStatus.NEED_UNWRAP) {

                    while (hstatus == HandshakeStatus.NEED_UNWRAP) {
                        // If we have few remaining bytes - process them
                        if (myNetInData.remaining() > 0) {
                            myAppInData.clear();
                            wrap = sslEngine.unwrap(myNetInData, myAppInData);
                            hstatus = wrap.getHandshakeStatus();

                            myAppInData.flip();
                            if (myAppInData.remaining() > 0) {
                                throw new IOException("Unexpected data");
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
                            int rd = net.getIn().read(myNetInData);
                            myNetInData.flip();
                            if (rd == 0) {
                                net.getIn().waitData(10000);
                                continue;
                            }
                            if (rd < 0) {
                                // in closed
                                break;
                            }
                        }
                        if (log.isLoggable(Level.FINEST)) {
                            log.finest("Unwrap chunk done " + hstatus + " " + wrap 
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
                        log.finest("Tasks done in " + (t1task - t0task) + " new status " +
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

    public SslChannel setSslContext(SSLContext sslCtx) {
        this.sslCtx = sslCtx;
        return this;
    }
}
