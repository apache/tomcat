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
package org.apache.tomcat.websocket.pojo;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.ApplicationListener;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.pojo.Util.ServerConfigListener;
import org.apache.tomcat.websocket.pojo.Util.SingletonConfigurator;
import org.apache.tomcat.websocket.server.WsListener;

public class TestEncodingDecoding extends TomcatBaseTest {

    private static final String MESSAGE_ONE = "message-one";

    @Test
    public void test() throws Exception {

        // Set up utility classes
        Server server = new Server();
        SingletonConfigurator.setInstance(server);
        ServerConfigListener.setPojoClazz(Server.class);

        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(new ApplicationListener(
                WsListener.class.getName(), false));
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        WebSocketContainer wsContainer =
                ContainerProvider.getWebSocketContainer();


        tomcat.start();

        Client client = new Client();
        URI uri = new URI("ws://localhost:" + getPort() + "/");
        Session session = wsContainer.connectToServer(client, uri);

        MsgString msg1 = new MsgString();
        msg1.setData(MESSAGE_ONE);
        session.getBasicRemote().sendObject(msg1);

        // Should not take very long
        int i = 0;
        while (i < 20) {
            if (server.received.size() > 0 && client.received.size() > 0) {
                break;
            }
            Thread.sleep(100);
        }

        // Check messages were received
        Assert.assertEquals(1, server.received.size());
        Assert.assertEquals(1, client.received.size());

        // Check correct messages were received
        Assert.assertEquals(MESSAGE_ONE,
                ((MsgString) server.received.peek()).getData());
        Assert.assertEquals(MESSAGE_ONE,
                ((MsgString) client.received.peek()).getData());
    }

    @ClientEndpoint(decoders={MsgStringDecoder.class, MsgByteDecoder.class},
            encoders={MsgStringEncoder.class, MsgByteEncoder.class})
    public static class Client {
        private Queue<Object> received = new ConcurrentLinkedQueue<>();

        @OnMessage
        public void rx(MsgString in) {
            received.add(in);
        }

        @OnMessage
        public void  rx(MsgByte in) {
            received.add(in);
        }
    }


    @ServerEndpoint(value="/",
            decoders={MsgStringDecoder.class, MsgByteDecoder.class},
            encoders={MsgStringEncoder.class, MsgByteEncoder.class},
            configurator=SingletonConfigurator.class)
    public static class Server {
        private Queue<Object> received = new ConcurrentLinkedQueue<>();

        @OnMessage
        public MsgString rx(MsgString in) {
            received.add(in);
            // Echo the message back
            return in;
        }

        @OnMessage
        public MsgByte rx(MsgByte in) {
            received.add(in);
            // Echo the message back
            return in;
        }
    }


    public static class MsgString {
        private String data;

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
    }


    public static class MsgStringEncoder implements Encoder.Text<MsgString> {

        @Override
        public void init(EndpointConfig endpointConfig) {
            // NO-OP
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        @Override
        public String encode(MsgString msg) throws EncodeException {
            return "MsgString:" + msg.getData();
        }
    }


    public static class MsgStringDecoder implements Decoder.Text<MsgString> {

        @Override
        public void init(EndpointConfig endpointConfig) {
            // NO-OP
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        @Override
        public MsgString decode(String s) throws DecodeException {
            MsgString result = new MsgString();
            result.setData(s.substring(10));
            return result;
        }

        @Override
        public boolean willDecode(String s) {
            return s.startsWith("MsgString:");
        }
    }


    public static class MsgByte {
        private byte[] data;

        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }
    }


    public static class MsgByteEncoder implements Encoder.Binary<MsgByte> {

        @Override
        public void init(EndpointConfig endpointConfig) {
            // NO-OP
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        @Override
        public ByteBuffer encode(MsgByte msg) throws EncodeException {
            byte[] data = msg.getData();
            ByteBuffer reply = ByteBuffer.allocate(2 + data.length);
            reply.put((byte) 0x12);
            reply.put((byte) 0x34);
            reply.put(data);
            return reply;
        }
    }


    public static class MsgByteDecoder implements Decoder.Binary<MsgByte> {

        @Override
        public void init(EndpointConfig endpointConfig) {
            // NO-OP
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        @Override
        public MsgByte decode(ByteBuffer bb) throws DecodeException {
            MsgByte result = new MsgByte();
            bb.position(bb.position() + 2);
            byte[] data = new byte[bb.limit() - bb.position()];
            bb.get(data);
            result.setData(data);
            return result;
        }

        @Override
        public boolean willDecode(ByteBuffer bb) {
            bb.mark();
            if (bb.get() == 0x12 && bb.get() == 0x34) {
                return true;
            }
            bb.reset();
            return false;
        }
    }
}
