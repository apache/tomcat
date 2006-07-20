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

package org.apache.tomcat.util.collections;

import java.util.Vector;

/**
 * A simple FIFO queue class which causes the calling thread to wait
 * if the queue is empty and notifies threads that are waiting when it
 * is not empty.
 *
 * @author Anil V (akv@eng.sun.com)
 */
public class Queue {
    private Vector vector = new Vector();
    private boolean stopWaiting=false;
    private boolean waiting=false;
    
    /** 
     * Put the object into the queue.
     * 
     * @param	object		the object to be appended to the
     * 				queue. 
     */
    public synchronized void put(Object object) {
	vector.addElement(object);
	notify();
    }

    /** Break the pull(), allowing the calling thread to exit
     */
    public synchronized void stop() {
	stopWaiting=true;
	// just a hack to stop waiting 
	if( waiting ) notify();
    }
    
    /**
     * Pull the first object out of the queue. Wait if the queue is
     * empty.
     */
    public synchronized Object pull() {
	while (isEmpty()) {
	    try {
		waiting=true;
		wait();
	    } catch (InterruptedException ex) {
	    }
	    waiting=false;
	    if( stopWaiting ) return null;
	}
	return get();
    }

    /**
     * Get the first object out of the queue. Return null if the queue
     * is empty. 
     */
    public synchronized Object get() {
	Object object = peek();
	if (object != null)
	    vector.removeElementAt(0);
	return object;
    }

    /**
     * Peek to see if something is available.
     */
    public Object peek() {
	if (isEmpty())
	    return null;
	return vector.elementAt(0);
    }
    
    /**
     * Is the queue empty?
     */
    public boolean isEmpty() {
	return vector.isEmpty();
    }

    /**
     * How many elements are there in this queue?
     */
    public int size() {
	return vector.size();
    }
}
