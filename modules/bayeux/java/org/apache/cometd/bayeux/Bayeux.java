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
package org.apache.cometd.bayeux;

import java.util.List;

/** Bayeux Interface.<br/>
 * This interface represents the server side API for the Bayeux messaging protocol.
 * Bayeux is a simple subscribe/publish/receive methodology, not far from JMS, but much simplified.<br/>
 * It is used both by the actual implementation and by server side clients.<br/>
 * Server side clients use this to create, retrieve and subscribe to channels.
 * Server side clients are represented, just like remote clients, through the Client interface.
 * <br/>
 * The Bayeux implementations is intended to be thread safe and multiple threads may simultaneously call Bayeux methods.
 * <br/>
 * The Bayeux object, is the starting point for any cometd application relying on the Bayeux object.
 * Dependent on the container, the Bayeux object will be stored in the <code>javax.servlet.ServletContext</code> object
 * as an attribute under the name <code>Bayeux.DOJOX_COMETD_BAYEUX</code><br/>
 * To retrieve this object, one would simply call<br/>
 * <code>Bayeux bx = (Bayeux)getServletContext().getAttribute(Bayeux.DOJOX_COMETD_BAYEUX);
 * <br/><br/>
 * The Bayeux protocol is pretty straight forward and includes a bunch of messaging that is not needed to be known to clients,
 * both server side and remote clients.
 * This object gets initialized by a container dependent servlet, and the servlet then handles all Bayeux communication from the client.
 * Remote messsages are delivered to channels, and to server side clients using the <code>Listener</code> interface.<br/>
 * <br/>
 * A <code>Bayeux session</code> is active as long as the webapp hosting the Bayeux object is active.<br/>
 * When the webapplication shuts down, the Bayeux object will unsubscribe all clients and remove all the active channels.
 *
 * @author Greg Wilkins
 * @author Filip Hanik
 */
public interface Bayeux {

    /**Meta definitions for channels*/
    public static final String META="/meta";
    /**Meta definitions for channels*/
    public static final String META_SLASH="/meta/";
    /**Meta definitions for channels - connect message*/
    public static final String META_CONNECT="/meta/connect";
    /**Meta definitions for channels - client messsage*/
    public static final String META_CLIENT="/meta/client";
    /**Meta definitions for channels - disconnect messsage*/
    public static final String META_DISCONNECT="/meta/disconnect";
    /**Meta definitions for channels - handshake messsage*/
    public static final String META_HANDSHAKE="/meta/handshake";
    /**Meta definitions for channels - ping messsage*/
    public static final String META_PING="/meta/ping";
    /**Meta definitions for channels - reconnect messsage
     * @deprecated
     */
    public static final String META_RECONNECT="/meta/reconnect";
    /**Meta definitions for channels - status messsage*/
    public static final String META_STATUS="/meta/status";
    /**Meta definitions for channels - subscribe messsage*/
    public static final String META_SUBSCRIBE="/meta/subscribe";
    /**Meta definitions for channels - unsubscribe messsage*/
    public static final String META_UNSUBSCRIBE="/meta/unsubscribe";
    /*Field names inside Bayeux messages*/
    /**Field names inside Bayeux messages - clientId field*/
    public static final String CLIENT_FIELD="clientId";
    /**Field names inside Bayeux messages - data field*/
    public static final String DATA_FIELD="data";
    /**Field names inside Bayeux messages - channel field*/
    public static final String CHANNEL_FIELD="channel";
    /**Field names inside Bayeux messages - id field*/
    public static final String ID_FIELD="id";
    /**Field names inside Bayeux messages - error field*/
    public static final String ERROR_FIELD="error";
    /**Field names inside Bayeux messages - timestamp field*/
    public static final String TIMESTAMP_FIELD="timestamp";
    /**Field names inside Bayeux messages - transport field*/
    public static final String TRANSPORT_FIELD="transport";
    /**Field names inside Bayeux messages - advice field*/
    public static final String ADVICE_FIELD="advice";
    /**Field names inside Bayeux messages - successful field*/
    public static final String SUCCESSFUL_FIELD="successful";
    /**Field names inside Bayeux messages - subscription field*/
    public static final String SUBSCRIPTION_FIELD="subscription";
    /**Field names inside Bayeux messages - ext field*/
    public static final String EXT_FIELD="ext";
    /**Field names inside Bayeux messages - connectionType field*/
    public static final String CONNECTION_TYPE_FIELD="connectionType";
    /**Field names inside Bayeux messages - version field*/
    public static final String VERSION_FIELD="version";
    /**Field names inside Bayeux messages - minimumVersion field*/
    public static final String MIN_VERSION_FIELD="minimumVersion";
    /**Field names inside Bayeux messages - supportedConnectionTypes field*/
    public static final String SUPP_CONNECTION_TYPE_FIELD="supportedConnectionTypes";
    /**Field names inside Bayeux messages - json-comment-filtered field*/
    public static final String JSON_COMMENT_FILTERED_FIELD="json-comment-filtered";
    /**Field names inside Bayeux messages - reconnect field*/
    public static final String RECONNECT_FIELD = "reconnect";
    /**Field names inside Bayeux messages - interval field*/
    public static final String INTERVAL_FIELD = "interval";
    /**Field values inside Bayeux messages - retry response*/
    public static final String RETRY_RESPONSE = "retry";
    /**Field values inside Bayeux messages - handshake response*/
    public static final String HANDSHAKE_RESPONSE = "handshake";
    /**Field values inside Bayeux messages - none response*/
    public static final String NONE_RESPONSE = "none";
    /**Service channel names-starts with*/
    public static final String SERVICE="/service";
    /**Service channel names-trailing slash*/
    public static final String SERVICE_SLASH="/service/";
    /*Transport types*/
    /**Transport types - long polling*/
    public static final String TRANSPORT_LONG_POLL="long-polling";
    /**Transport types - callback polling*/
    public static final String TRANSPORT_CALLBACK_POLL="callback-polling";
    /**Transport types - iframe*/
    public static final String TRANSPORT_IFRAME="iframe";
    /**Transport types - flash*/
    public static final String TRANSPORT_FLASH="flash";
    /** ServletContext attribute name used to obtain the Bayeux object */
    public static final String DOJOX_COMETD_BAYEUX="dojox.cometd.bayeux";
    /*http field names*/
    /**http helpers - text/json content type*/
    public static final String JSON_CONTENT_TYPE="text/json";
    /**http helpers - parameter name for json message*/
    public static final String MESSAGE_PARAMETER="message";
    /**http helpers - name of the jsonp parameter*/
    public static final String JSONP_PARAMETER="jsonp";
    /**http helpers - default name of the jsonp callback function*/
    public static final String JSONP_DEFAULT_NAME="jsonpcallback";

    /*--Client----------------------------------------------------------- */
    /**
     * Creates a new server side client. This method is to be invoked
     * by server side objects only. You cannot create a remote client by using this method.
     * A client represents an entity that can subscribe to channels and publish and receive messages
     * through these channels
     * @param idprefix String - the prefix string for the id generated, can be null
     * @param listener Listener - a callback object to be called when messages are to be delivered to the new client
     * @return Client - returns an implementation of the client interface.
     */
    public Client newClient(String idprefix, Listener listener);

    /**
     * retrieve a client based on an ID. Will return null if the client doesn't exist.
     * @param clientid String
     * @return Client-null if the client doesn't exist.returns the client if it does.
     */
    public Client getClient(String clientid);

    /**
     * Returns a non modifiable list of all the clients that are currently active
     * in this Bayeux session
     * @return List<Client> - a list containing all clients. The List can not be modified.
     */
    public List<Client> getClients();

    /**
     * Returns true if a client with the given id exists.<br/>
     * Same as executing <code>getClient(id)!=null</code>.
     * @param clientId String
     * @return boolean - true if the client exists
     */
    public boolean hasClient(String clientId);

    /**
     * Removes the client all together.
     * This will unsubscribe the client to any channels it may be subscribed to
     * and remove it from the list.
     * @param client Client
     * @return Client - returns the client that was removed, or null if no client was removed.
     */
    public Client remove(Client client);


    /*--Channel---------------------------------------------------------- */
    /**
     * Returns the channel for a given channel id.
     * If the channel doesn't exist, and the <code>create</code> parameter is set to true,
     * the channel will be created and added to the list of active channels.<br/>
     * if <code>create</code> is set to false, and the channel doesn't exist, null will be returned.
     * @param channelId String - the id of the channel to be retrieved or created
     * @param create boolean - true if the Bayeux impl should create the channel
     * @return Channel - null if <code>create</code> is set to false and the channel doesn't exist,
     * otherwise it returns a channel object.
     */
    public Channel getChannel(String channelId, boolean create);

    /**
     * Returns a list of currently active channels in this Bayeux session.
     * @return List<Channel>
     */
    public List<Channel> getChannels();

    /**
     * Removes a channel from the Bayeux object.
     * This will also unsubscribe all the clients currently subscribed to the
     * the channel.
     * @param channel Channel - the channel to be removed
     * @return Channel - returns the channel that was removed, or null if no channel was removed.
     */
    public Channel remove(Channel channel);

    /**
     * returns true if a channel with the given channelId exists.
     * <br/>Same as executing <code>Bayeux.getChannel(channelId,false)!=null</code>
     * @param channelId String
     * @return boolean - true if the channel exists.
     */
    public boolean hasChannel(String channelId);

    /* --Message---------------------------------------------------------- */
    /**
     * Creates a new message to be sent by a server side client.
     * @return Message - returns a new Message object, that has a unique id.
     */
    public Message newMessage(Client from);


    /*--Security policy----------------------------------------------------------- */
    /**
     * Returns the security policy associated with this Bayeux session
     * @return SecurityPolicy
     */
    public SecurityPolicy getSecurityPolicy();

    /**
     * Sets the security policy to be used in this Bayeux session
     * @param securityPolicy SecurityPolicy
     */
    public void setSecurityPolicy(SecurityPolicy securityPolicy);

}