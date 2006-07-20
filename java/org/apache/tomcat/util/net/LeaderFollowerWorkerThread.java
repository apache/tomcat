/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.net;

import java.net.Socket;
import org.apache.tomcat.util.threads.ThreadPoolRunnable;

/*
 * I switched the threading model here.
 *
 * We used to have a "listener" thread and a "connection"
 * thread, this results in code simplicity but also a needless
 * thread switch.
 *
 * Instead I am now using a pool of threads, all the threads are
 * simmetric in their execution and no thread switch is needed.
 */
class LeaderFollowerWorkerThread implements ThreadPoolRunnable {
    /* This is not a normal Runnable - it gets attached to an existing
       thread, runs and when run() ends - the thread keeps running.

       It's better to keep the name ThreadPoolRunnable - avoid confusion.
       We also want to use per/thread data and avoid sync wherever possible.
    */
    PoolTcpEndpoint endpoint;
    
    public LeaderFollowerWorkerThread(PoolTcpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Object[] getInitData() {
        // no synchronization overhead, but 2 array access 
        Object obj[]=new Object[2];
        obj[1]= endpoint.getConnectionHandler().init();
        obj[0]=new TcpConnection();
        return obj;
    }
    
    public void runIt(Object perThrData[]) {

        // Create per-thread cache
        if (endpoint.isRunning()) {

            // Loop if endpoint is paused
            while (endpoint.isPaused()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            // Accept a new connection
            Socket s = null;
            try {
                s = endpoint.acceptSocket();
            } finally {
                // Continue accepting on another thread...
                if (endpoint.isRunning()) {
                    endpoint.tp.runIt(this);
                }
            }

            // Process the connection
            if (null != s) {
                endpoint.processSocket(s, (TcpConnection) perThrData[0], (Object[]) perThrData[1]);
            }

        }
    }
    
}
