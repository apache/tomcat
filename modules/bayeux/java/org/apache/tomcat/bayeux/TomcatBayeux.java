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
 * limitations under the License.
 */
package org.apache.tomcat.bayeux;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.UUIDGenerator;
import org.apache.cometd.bayeux.Bayeux;
import org.apache.cometd.bayeux.Channel;
import org.apache.cometd.bayeux.Client;
import org.apache.cometd.bayeux.Listener;
import org.apache.cometd.bayeux.Message;
import org.apache.cometd.bayeux.SecurityPolicy;
/**
 *
 * @author Filip Hanik
 * @version 1.0
 */
public class TomcatBayeux implements Bayeux {


    protected int reconnectInterval = 5000;
    /**
     * a list of all active clients
     */
    protected HashMap<String,Client> clients = new HashMap<String,Client>();

    /**
     * a list of all active channels
     */
    protected LinkedHashMap<String, Channel> channels = new LinkedHashMap<String,Channel>();

    /**
     * security policy to be used.
     */
    protected SecurityPolicy securityPolicy = null;
    /**
     * default client to use when we need to send an error message but don't have a client valid reference
     */
    protected static ClientImpl errorClient = new ClientImpl("error-no-client",false);

    /**
     * returns the default error client
     * @return ClientImpl
     */
    public static ClientImpl getErrorClient() {
        return errorClient;
    }

    protected TomcatBayeux() {
    }

    /**
     * should be invoked when the servlet is destroyed or when the context shuts down
     */
    public void destroy() {
        throw new UnsupportedOperationException("TomcatBayeux.destroy() not yet implemented");
    }

    public Channel getChannel(String channelId, boolean create) {
        Channel result = channels.get(channelId);
        if (result==null && create) {
            result = new ChannelImpl(channelId);
            channels.put(channelId,result);
        }
        return result;
    }

    public Channel remove(Channel channel) {
        return channels.remove(channel.getId());
    }

    public Client remove(Client client) {
        if (client==null) return null;
        for (Channel ch : getChannels()) {
            ch.unsubscribe(client);
        }
        return clients.remove(client.getId());
    }

    public Client getClient(String clientId) {
        return clients.get(clientId);
    }

    public boolean hasClient(String clientId) {
        return clients.containsKey(clientId);
    }

    public List<Client> getClients() {
        return java.util.Arrays.asList(clients.values().toArray(new Client[0]));
    }

    public SecurityPolicy getSecurityPolicy() {
        return securityPolicy;
    }

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    public boolean hasChannel(String channel) {
        return channels.containsKey(channel);
    }

    public Client newClient(String idprefix, Listener listener, boolean local, CometEvent event) {
        String id = createUUID(idprefix);
        ClientImpl client = new ClientImpl(id, local);
        client.setListener(listener);
        clients.put(id, client);
        return client;
    }

    public Client newClient(String idprefix, Listener listener) {
        assert listener!=null;
        //if this method gets called, someone is using the API inside
        //the JVM, this is a local client
        return newClient(idprefix,listener,true, null);
    }

    protected ClientImpl getClientImpl(CometEvent event) {
        return (ClientImpl)event.getHttpServletRequest().getAttribute(ClientImpl.COMET_EVENT_ATTR);
    }

    protected void remove(CometEvent event) {
        ClientImpl client = getClientImpl(event);
        if (client!=null) {
            client.removeCometEvent(event);
        }
    }

    public String createUUID(String idprefix) {
        if (idprefix==null) idprefix="";
        return idprefix + Arrays.toString(UUIDGenerator.randomUUID(false));
    }

    public List<Channel> getChannels() {
        return java.util.Arrays.asList(channels.entrySet().toArray(new Channel[0]));
    }

    protected Message newMessage() {
        String id = createUUID("msg-");
        return new MessageImpl(id);
    }

    public Message newMessage(Client from) {
        MessageImpl msg = (MessageImpl)newMessage();
        msg.setClient(from);
        return msg;
    }
    public void setSecurityPolicy(SecurityPolicy securityPolicy) {
        this.securityPolicy = securityPolicy;
    }

    public void setReconnectInterval(int reconnectTimeout) {
        this.reconnectInterval = reconnectTimeout;
    }

}
