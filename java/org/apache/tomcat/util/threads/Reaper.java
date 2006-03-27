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

package org.apache.tomcat.util.threads;


/**
 * The reaper is a background thread with which ticks every minute
 * and calls registered objects to allow reaping of old session
 * data.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Costin Manolache
 */
public class Reaper extends Thread {
    
    
    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog(Reaper.class );
    
    private boolean daemon = false;

    public Reaper() {
        if (daemon)
            this.setDaemon(true);
        this.setName("TomcatReaper");
    }

    public Reaper(String name) {
        if (daemon)
            this.setDaemon(true);
        this.setName(name);
    }

    private long interval = 1000 * 60; //ms

    // XXX TODO Allow per/callback interval, find next, etc
    // Right now the "interval" is used for all callbacks
    // and it represent a sleep between runs.

    ThreadPoolRunnable cbacks[] = new ThreadPoolRunnable[30]; // XXX max
    Object tdata[][] = new Object[30][]; // XXX max
    int count = 0;

    /** Adding and removing callbacks is synchronized
     */
    Object lock = new Object();
    static boolean running = true;

    // XXX Should be called 'interval' not defaultInterval

    public void setDefaultInterval(long t) {
        interval = t;
    }

    public long getDefaultIntervale() {
        return interval;
    }

    public int addCallback(ThreadPoolRunnable c, int interval) {
        synchronized (lock) {
            cbacks[count] = c;
            count++;
            return count - 1;
        }
    }

    public void removeCallback(int idx) {
        synchronized (lock) {
            count--;
            cbacks[idx] = cbacks[count];
            cbacks[count] = null;
        }
    }

    public void startReaper() {
        running = true;
        this.start();
    }

    public synchronized void stopReaper() {
        running = false;
        if (log.isDebugEnabled())
            log.debug("Stop reaper ");
        this.interrupt(); // notify() doesn't stop sleep
    }

    public void run() {
        while (running) {
            if (!running)
                break;
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ie) {
                // sometimes will happen
            }

            if (!running)
                break;
            for (int i = 0; i < count; i++) {
                ThreadPoolRunnable callB = cbacks[i];
                // it may be null if a callback is removed.
                //  I think the code is correct
                if (callB != null) {
                    callB.runIt(tdata[i]);
                }
                if (!running)
                    break;
            }
        }
    }
}
