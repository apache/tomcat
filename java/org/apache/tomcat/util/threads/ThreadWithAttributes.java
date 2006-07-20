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

import java.util.Hashtable;

/** Special thread that allows storing of attributes and notes.
 *  A guard is used to prevent untrusted code from accessing the
 *  attributes.
 *
 *  This avoids hash lookups and provide something very similar
 * with ThreadLocal ( but compatible with JDK1.1 and faster on
 * JDK < 1.4 ).
 *
 * The main use is to store 'state' for monitoring ( like "processing
 * request 'GET /' ").
 */
public class ThreadWithAttributes extends Thread {
    
    private Object control;
    public static int MAX_NOTES=16;
    private Object notes[]=new Object[MAX_NOTES];
    private Hashtable attributes=new Hashtable();
    private String currentStage;
    private Object param;
    
    private Object thData[];

    public ThreadWithAttributes(Object control, Runnable r) {
        super(r);
        this.control=control;
    }
    
    public final Object[] getThreadData(Object control ) {
        return thData;
    }
    
    public final void setThreadData(Object control, Object thData[] ) {
        this.thData=thData;
    }

    /** Notes - for attributes that need fast access ( array )
     * The application is responsible for id management
     */
    public final void setNote( Object control, int id, Object value ) {
        if( this.control != control ) return;
        notes[id]=value;
    }

    /** Information about the curent performed operation
     */
    public final String getCurrentStage(Object control) {
        if( this.control != control ) return null;
        return currentStage;
    }

    /** Information about the current request ( or the main object
     * we are processing )
     */
    public final Object getParam(Object control) {
        if( this.control != control ) return null;
        return param;
    }

    public final void setCurrentStage(Object control, String currentStage) {
        if( this.control != control ) return;
        this.currentStage = currentStage;
    }

    public final void setParam( Object control, Object param ) {
        if( this.control != control ) return;
        this.param=param;
    }

    public final Object getNote(Object control, int id ) {
        if( this.control != control ) return null;
        return notes[id];
    }

    /** Generic attributes. You'll need a hashtable lookup -
     * you can use notes for array access.
     */
    public final Hashtable getAttributes(Object control) {
        return attributes;
    }
}
