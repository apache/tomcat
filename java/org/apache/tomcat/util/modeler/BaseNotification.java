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
package org.apache.tomcat.util.modeler;

import javax.management.Notification;


/**
 * Base JMX Notification. Supports in int code and notes - for faster 
 * access and dispatching. 
 *
 * @author Costin Manolache
 */
public final class BaseNotification extends Notification {

    // ----------------------------------------------------------- Constructors
    private int code;
    private String type;
    private Object source;
    private long seq;
    private long tstamp;

    /**
     * Private constructor.
     */
    private BaseNotification(String type,
                             Object source,
                             long seq,
                             long tstamp,
                             int code) {
        super(type, source, seq, tstamp);
        init( type, source, seq, tstamp, code );
        this.code=code;
    }

    public void recycle() {

    }

    public void init( String type, Object source,
                      long seq, long tstamp, int code )
    {
        this.type=type;
        this.source = source;
        this.seq=seq;
        this.tstamp=tstamp;
        this.code = code;
    }

    // -------------------- Override base methods  --------------------
    // All base methods need to be overriden - in order to support recycling.


    // -------------------- Information associated with the notification  ----
    // Like events ( which Notification extends ), notifications may store
    // informations related with the event that trigered it. Source and type is
    // one piece, but it is common to store more info.

    /** Action id, useable in switches and table indexes
     */
    public int getCode() {
        return code;
    }

    // XXX Make it customizable - or grow it
    private Object notes[]=new Object[32];

    public final Object getNote(int i ) {
        return notes[i];
    }

    public final void setNote(int i, Object o ) {
        notes[i]=o;
    }
}
