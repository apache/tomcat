/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */

package org.apache.catalina.tribes.group.interceptors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;



/**
 *
 *
 * @author Filip Hanik
 * @version 1.0
 */
public class GzipInterceptor extends ChannelInterceptorBase {

    private static final Log log = LogFactory.getLog(GzipInterceptor.class);

    public static final int DEFAULT_BUFFER_SIZE = 2048;
    
    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        try {
            byte[] data = compress(msg.getMessage().getBytes());
            msg.getMessage().trim(msg.getMessage().getLength());
            msg.getMessage().append(data,0,data.length);
            getNext().sendMessage(destination, msg, payload);
        } catch ( IOException x ) {
            log.error("Unable to compress byte contents");
            throw new ChannelException(x);
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        try {
            byte[] data = decompress(msg.getMessage().getBytes());
            msg.getMessage().trim(msg.getMessage().getLength());
            msg.getMessage().append(data,0,data.length);
            getPrevious().messageReceived(msg);
        } catch ( IOException x ) {
            log.error("Unable to decompress byte contents",x);
        }
    }
    
    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GZIPOutputStream gout = new GZIPOutputStream(bout);
        gout.write(data);
        gout.flush();
        gout.close();
        return bout.toByteArray();
    }
    
    /**
     * @todo Fix to create an automatically growing buffer.
     * @param data byte[]
     * @return byte[]
     * @throws IOException
     */
    public static byte[] decompress(byte[] data) throws IOException {
        ByteArrayInputStream bin = new ByteArrayInputStream(data);
        GZIPInputStream gin = new GZIPInputStream(bin);
        byte[] tmp = new byte[DEFAULT_BUFFER_SIZE];
        int length = gin.read(tmp);
        byte[] result = new byte[length];
        System.arraycopy(tmp,0,result,0,length);
        return result;
    }
    
    public static void main(String[] arg) throws Exception {
        byte[] data = new byte[1024];
        Arrays.fill(data,(byte)1);
        byte[] compress = compress(data);
        decompress(compress);
        System.out.println("Debug test");
        
    }
    
}
