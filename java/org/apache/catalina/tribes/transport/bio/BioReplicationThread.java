/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.catalina.tribes.transport.bio;
import java.io.IOException;

import org.apache.catalina.tribes.io.ObjectReader;
import org.apache.catalina.tribes.transport.Constants;
import org.apache.catalina.tribes.transport.WorkerThread;
import java.net.Socket;
import java.io.InputStream;
import org.apache.catalina.tribes.transport.ReceiverBase;
import java.io.OutputStream;
import org.apache.catalina.tribes.io.ListenCallback;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.BufferPool;

/**
 * A worker thread class which can drain channels and echo-back the input. Each
 * instance is constructed with a reference to the owning thread pool object.
 * When started, the thread loops forever waiting to be awakened to service the
 * channel associated with a SelectionKey object. The worker is tasked by
 * calling its serviceChannel() method with a SelectionKey object. The
 * serviceChannel() method stores the key reference in the thread object then
 * calls notify() to wake it up. When the channel has been drained, the worker
 * thread returns itself to its parent pool.
 * 
 * @author Filip Hanik
 * 
 * @version $Revision: 378050 $, $Date: 2006-02-15 12:30:02 -0600 (Wed, 15 Feb 2006) $
 */
public class BioReplicationThread extends WorkerThread {


    protected static org.apache.juli.logging.Log log = org.apache.juli.logging.LogFactory.getLog( BioReplicationThread.class );
    
    protected Socket socket;
    protected ObjectReader reader;
    
    public BioReplicationThread (ListenCallback callback) {
        super(callback);
    }

    // loop forever waiting for work to do
    public synchronized void run()
    {
        this.notify();
        while (isDoRun()) {
            try {
                // sleep and release object lock
                this.wait();
            } catch (InterruptedException e) {
                if(log.isInfoEnabled())
                    log.info("TCP worker thread interrupted in cluster",e);
                // clear interrupt status
                Thread.interrupted();
            }
            if ( socket == null ) continue;
            try {
                drainSocket();
            } catch ( Exception x ) {
                log.error("Unable to service bio socket");
            }finally {
                try {socket.close();}catch ( Exception ignore){}
                try {reader.close();}catch ( Exception ignore){}
                reader = null;
                socket = null;
            }
            // done, ready for more, return to pool
            if ( getPool() != null ) getPool().returnWorker (this);
            else setDoRun(false);
        }
    }

    
    public synchronized void serviceSocket(Socket socket, ObjectReader reader) {
        this.socket = socket;
        this.reader = reader;
        this.notify();		// awaken the thread
    }
    
    protected void execute(ObjectReader reader) throws Exception{
        int pkgcnt = reader.count();

        if ( pkgcnt > 0 ) {
            ChannelMessage[] msgs = reader.execute();
            for ( int i=0; i<msgs.length; i++ ) {
                /**
                 * Use send ack here if you want to ack the request to the remote 
                 * server before completing the request
                 * This is considered an asynchronized request
                 */
                if (ChannelData.sendAckAsync(msgs[i].getOptions())) sendAck(Constants.ACK_COMMAND);
                try {
                    //process the message
                    getCallback().messageDataReceived(msgs[i]);
                    /**
                     * Use send ack here if you want the request to complete on this
                     * server before sending the ack to the remote server
                     * This is considered a synchronized request
                     */
                    if (ChannelData.sendAckSync(msgs[i].getOptions())) sendAck(Constants.ACK_COMMAND);
                }catch  ( Exception x ) {
                    if (ChannelData.sendAckSync(msgs[i].getOptions())) sendAck(Constants.FAIL_ACK_COMMAND);
                    log.error("Error thrown from messageDataReceived.",x);
                }
                if ( getUseBufferPool() ) {
                    BufferPool.getBufferPool().returnBuffer(msgs[i].getMessage());
                    msgs[i].setMessage(null);
                }
            }                       
        }

       
    }

    /**
     * The actual code which drains the channel associated with
     * the given key.  This method assumes the key has been
     * modified prior to invocation to turn off selection
     * interest in OP_READ.  When this method completes it
     * re-enables OP_READ and calls wakeup() on the selector
     * so the selector will resume watching this channel.
     */
    protected void drainSocket () throws Exception {
        InputStream in = socket.getInputStream();
        // loop while data available, channel is non-blocking
        byte[] buf = new byte[1024];
        int length = in.read(buf);
        while ( length >= 0 ) {
            int count = reader.append(buf,0,length,true);
            if ( count > 0 ) execute(reader);
            length = in.read(buf);
        }
    }




    /**
     * send a reply-acknowledgement (6,2,3)
     * @param key
     * @param channel
     */
    protected void sendAck(byte[] command) {
        try {
            OutputStream out = socket.getOutputStream();
            out.write(command);
            out.flush();
            if (log.isTraceEnabled()) {
                log.trace("ACK sent to " + socket.getPort());
            }
        } catch ( java.io.IOException x ) {
            log.warn("Unable to send ACK back through channel, channel disconnected?: "+x.getMessage());
        }
    }
    
    public void close() {
        setDoRun(false);
        try {socket.close();}catch ( Exception ignore){}
        try {reader.close();}catch ( Exception ignore){}
        reader = null;
        socket = null;
        super.close();
    }
}
