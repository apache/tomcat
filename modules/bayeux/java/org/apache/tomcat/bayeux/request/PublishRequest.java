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
package org.apache.tomcat.bayeux.request;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.ServletException;

import org.apache.catalina.comet.CometEvent;
import org.apache.tomcat.bayeux.HttpError;
import org.apache.tomcat.bayeux.BayeuxException;
import org.apache.tomcat.bayeux.ChannelImpl;
import org.apache.tomcat.bayeux.ClientImpl;
import org.apache.tomcat.bayeux.MessageImpl;
import org.apache.tomcat.bayeux.RequestBase;
import org.apache.tomcat.bayeux.TomcatBayeux;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.cometd.bayeux.Bayeux;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/******************************************************************************
 * Handshake request Bayeux message.
 *
 * @author Guy A. Molinari
 * @author Filip Hanik
 * @version 1.0
 *
 */
public class PublishRequest extends RequestBase {

    private static final Log log = LogFactory.getLog(PublishRequest.class);

    protected static HashMap<String,Object> responseTemplate = new HashMap<String,Object>();

    static {
        responseTemplate.put(Bayeux.SUCCESSFUL_FIELD,Boolean.TRUE);
        responseTemplate.put(Bayeux.ADVICE_FIELD, new HashMap<String, Object>());
    }

    JSONObject msgData = null;

    public PublishRequest(TomcatBayeux tb, CometEvent event, JSONObject jsReq) throws JSONException {
        super(tb, event, jsReq);
    }


    /**
     * Check client request for validity.
     *
     * Per section 5.1.1 of the Bayuex spec a connect request must contain:
     *  1) The channel identifier of the channel for publication.
     *  2) The data to send.
     *
     * @return HttpError This method returns null if no errors were found
     */
    @Override
    public HttpError validate() {
        if(channel==null|| (!this.getTomcatBayeux().hasChannel(channel)))
            return new HttpError(400,"Channel Id not valid.", null);
        if(data==null || data.length()==0)
            return new HttpError(400,"Message data missing.", null);
        try {
            this.msgData = new JSONObject(data);
        }catch (JSONException x) {
            return new HttpError(400,"Invalid JSON object in data attribute.",x);
        }
        if(clientId==null|| (!this.getTomcatBayeux().hasClient(clientId)))
            return new HttpError(400,"Client Id not valid.", null);
        return null;//no error
    }

    /**
     *  Send the event message to all registered subscribers.
     */
    @Override
    public int process(int prevops) throws BayeuxException {
        super.process(prevops);
        response = (HashMap<String, Object>)responseTemplate.clone();
        ClientImpl client = clientId!=null?(ClientImpl)getTomcatBayeux().getClient(clientId):
                                           (ClientImpl)event.getHttpServletRequest().getAttribute("client");
        boolean success = false;
        HttpError error = validate();
        if (error == null) {
            ChannelImpl chimpl = (ChannelImpl)getTomcatBayeux().getChannel(channel,false);
            MessageImpl mimpl = (MessageImpl)getTomcatBayeux().newMessage(client);

            try {
                String[] keys = JSONObject.getNames(msgData);
                for (int i = 0; i < keys.length; i++) {
                    mimpl.put(keys[i], msgData.get(keys[i]));
                }
                success = true;
                ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.RECONNECT_FIELD, Bayeux.RETRY_RESPONSE);
                ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.INTERVAL_FIELD, getReconnectInterval());
            }catch (JSONException x) {
                if (log.isErrorEnabled()) log.error("Unable to parse:"+msgData,x);
                throw new BayeuxException(x);
            }
            chimpl.publish(mimpl);
        }
        if(!success) {
            response.put(Bayeux.SUCCESSFUL_FIELD,Boolean.FALSE);
            response.put(Bayeux.ERROR_FIELD, error.toString());
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.RECONNECT_FIELD, Bayeux.HANDSHAKE_RESPONSE);
            if (client==null) client = TomcatBayeux.getErrorClient();
        }
        response.put(Bayeux.CHANNEL_FIELD,channel);
        response.put(Bayeux.CLIENT_FIELD, client.getId());
        try {
            JSONObject obj = new JSONObject(response);
            addToDeliveryQueue(client, obj);
        } catch (ServletException x) {
            throw new BayeuxException(x);
        } catch (IOException x) {
            throw new BayeuxException(x);
        }

        if (success && client!=null && client.hasMessages()) {
            //send out messages
            flushMessages(client);
        }

        return 0;
    }
}

