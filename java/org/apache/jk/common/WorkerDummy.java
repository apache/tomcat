/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.jk.common;

import java.io.IOException;

import org.apache.jk.core.JkHandler;
import org.apache.jk.core.Msg;
import org.apache.jk.core.MsgContext;
import org.apache.jk.core.WorkerEnv;
import org.apache.tomcat.util.buf.MessageBytes;


/** A dummy worker, will just send back a dummy response.
 *  Used for testing and tunning.
 */
public class WorkerDummy extends JkHandler
{
    public WorkerDummy()
    {
        String msg="HelloWorld";
        byte b[]=msg.getBytes();
        body.setBytes(b, 0, b.length);
    }

    /* ==================== Start/stop ==================== */

    /** Initialize the worker. After this call the worker will be
     *  ready to accept new requests.
     */
    public void init() throws IOException {
        headersMsgNote=wEnv.getNoteId( WorkerEnv.ENDPOINT_NOTE, "headerMsg" );
    }
 
    MessageBytes body=MessageBytes.newInstance();
    private int headersMsgNote;
    
    public int invoke( Msg in, MsgContext ep ) 
        throws IOException
    {
        MsgAjp msg=(MsgAjp)ep.getNote( headersMsgNote );
        if( msg==null ) {
            msg=new MsgAjp();
            ep.setNote( headersMsgNote, msg );
        }

        msg.reset();
        msg.appendByte(AjpConstants.JK_AJP13_SEND_HEADERS);
        msg.appendInt(200);
        msg.appendBytes(null);

        msg.appendInt(0);

        ep.setType( JkHandler.HANDLE_SEND_PACKET );
        ep.getSource().invoke( msg, ep );
        //         msg.dump("out:" );

        msg.reset();
        msg.appendByte( AjpConstants.JK_AJP13_SEND_BODY_CHUNK);
        msg.appendInt( body.getLength() );
        msg.appendBytes( body );

        
        ep.getSource().invoke(msg, ep);

        msg.reset();
        msg.appendByte( AjpConstants.JK_AJP13_END_RESPONSE );
        msg.appendInt( 1 );
        
        ep.getSource().invoke(msg, ep );
        return OK;
    }
    
    private static final int dL=0;
}

