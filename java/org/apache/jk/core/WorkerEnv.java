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

package org.apache.jk.core;

import java.util.Hashtable;
import javax.management.ObjectName;

/**
 * The controller object. It manages all other jk objects, acting as the root of
 * the jk object model.
 *
 * @author Gal Shachor
 * @author Henri Gomez [hgomez@apache.org]
 * @author Dan Milstein [danmil@shore.net]
 * @author Keith Wannamaker [Keith@Wannamaker.org]
 * @author Kevin Seguin
 * @author Costin Manolache
 */
public class WorkerEnv {

    Hashtable properties;

    public static final int ENDPOINT_NOTE=0;
    public static final int REQUEST_NOTE=1;
    public static final int SSL_CERT_NOTE=16;
    int noteId[]=new int[4];
    String noteName[][]=new String[4][];
    private Object notes[]=new Object[32];

    Hashtable handlersMap=new Hashtable();
    JkHandler handlersTable[]=new JkHandler[20];
    int handlerCount=0;
    
    // base dir for the jk webapp
    String home;
    int localId=0;
    
    public WorkerEnv() {
        for( int i=0; i<noteId.length; i++ ) {
            noteId[i]=7;
            noteName[i]=new String[20];
        }
    }

    public void setLocalId(int id) {
        localId=id;
    }
    
    public int getLocalId() {
        return localId;
    }
    
    public void setJkHome( String s ) {
        home=s;
    }

    public String getJkHome() {
        return home;
    }
    
    public final Object getNote(int i ) {
        return notes[i];
    }

    public final void setNote(int i, Object o ) {
        notes[i]=o;
    }

    public int getNoteId( int type, String name ) {
        for( int i=0; i<noteId[type]; i++ ) {
            if( name.equals( noteName[type][i] ))
                return i;
        }
        int id=noteId[type]++;
        noteName[type][id]=name;
        return id;
    }

    public void addHandler( String name, JkHandler w ) {
        JkHandler oldH = getHandler(name);
        if(oldH == w) {
            // Already added
            return;
        }
        w.setWorkerEnv( this );
        w.setName( name );
        handlersMap.put( name, w );
        if( handlerCount > handlersTable.length ) {
            JkHandler newT[]=new JkHandler[ 2 * handlersTable.length ];
            System.arraycopy( handlersTable, 0, newT, 0, handlersTable.length );
            handlersTable=newT;
        }
        if(oldH == null) {
            handlersTable[handlerCount]=w;
            w.setId( handlerCount );
            handlerCount++;
        } else {
            handlersTable[oldH.getId()]=w;
            w.setId(oldH.getId());
        }

        // Notify all other handlers of the new one
        // XXX Could be a Coyote action ?
        for( int i=0; i< handlerCount ; i++ ) {
            handlersTable[i].addHandlerCallback( w );
        }
    }

    public final JkHandler getHandler( String name ) {
        return (JkHandler)handlersMap.get(name);
    }

    public final JkHandler getHandler( int id ) {
        return handlersTable[id];
    }

    public final int getHandlerCount() {
        return handlerCount;
    }
    
    public ObjectName[] getHandlersObjectName() {
        
        ObjectName onames[]=new ObjectName[ handlerCount ];
        for( int i=0; i<handlerCount; i++ ) {
            onames[i]=handlersTable[i].getObjectName();
        }
        return onames;
    }

}
