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
import java.util.Iterator;
import java.util.List;
import javax.servlet.ServletException;

import org.apache.catalina.comet.CometEvent;
import org.apache.tomcat.bayeux.HttpError;
import org.apache.tomcat.bayeux.BayeuxException;
import org.apache.tomcat.bayeux.BayeuxRequest;
import org.apache.tomcat.bayeux.ChannelImpl;
import org.apache.tomcat.bayeux.ClientImpl;
import org.apache.tomcat.bayeux.TomcatBayeux;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.cometd.bayeux.Channel;
import org.apache.cometd.bayeux.Bayeux;
import org.apache.tomcat.bayeux.*;

/******************************************************************************
 * Handshake request Bayeux message.
 *
 * @author Guy A. Molinari
 * @author Filip Hanik
 * @version 1.0
 */
public class MetaSubscribeRequest extends RequestBase implements BayeuxRequest {

    protected static HashMap<String,Object> responseTemplate = new HashMap<String,Object>();

    static {
        responseTemplate.put(Bayeux.CHANNEL_FIELD,Bayeux.META_SUBSCRIBE);
        responseTemplate.put(Bayeux.SUCCESSFUL_FIELD,Boolean.TRUE);
        responseTemplate.put(Bayeux.ADVICE_FIELD, new HashMap<String, Object>());
    }

    public MetaSubscribeRequest(TomcatBayeux tb, CometEvent event, JSONObject jsReq) throws JSONException {
        super(tb, event, jsReq);
    }


    /**
     * Check client request for validity.
     *
     * Per section 4.5.1 of the Bayuex spec a connect request must contain:
     *  1) The "/meta/subscribe" channel identifier.
     *  2) The clientId.
     *  3) The subscription.  This is the name of the channel of interest,
     *     or a pattern.
     *
     * @return HttpError This method returns null if no errors were found
     */
    public HttpError validate() {
        if(clientId==null|| (!this.getTomcatBayeux().hasClient(clientId)))
            return new HttpError(400,"Client Id not valid.", null);
        if (subscription==null||subscription.length()==0)
            return new HttpError(400,"Subscription missing.",null);
        return null;//no error
    }

    /**
     * Register interest for one or more channels.  Per section 2.2.1 of the
     * Bayeux spec, a pattern may be specified.  Assign client to matching
     * channels and inverse client to channel reference.
     */
    public int process(int prevops) throws BayeuxException {
        super.process(prevops);
        response = (HashMap<String, Object>)this.responseTemplate.clone();
        ClientImpl client = (ClientImpl)getTomcatBayeux().getClient(clientId);
        HttpError error = validate();
        if (error == null) {
            boolean wildcard = subscription.indexOf('*')!=-1;
            boolean subscribed = false;
            if (wildcard) {
                List<Channel> channels = getTomcatBayeux().getChannels();
                Iterator<Channel> it = channels.iterator();
                while (it.hasNext()) {
                    ChannelImpl ch = (ChannelImpl)it.next();
                    if (ch.matches(subscription)) {
                        ch.subscribe(client);
                        subscribed = true;
                    }
                }
            }else {
                ChannelImpl ch = (ChannelImpl)getTomcatBayeux().getChannel(subscription,true);
                ch.subscribe(client);
                subscribed = true;
            }
            response.put(Bayeux.SUCCESSFUL_FIELD, Boolean.valueOf(subscribed));
            response.put(Bayeux.SUBSCRIPTION_FIELD,subscription);
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put("reconnect", "retry");
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put("interval", getReconnectInterval());
        }else {
            response.put(Bayeux.SUCCESSFUL_FIELD,Boolean.FALSE);
            response.put(Bayeux.ERROR_FIELD, error.toString());
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put("reconnect", "handshake");
            if (client==null) client = TomcatBayeux.getErrorClient();
        }
        response.put(Bayeux.CLIENT_FIELD, client.getId());
        response.put(Bayeux.TIMESTAMP_FIELD,getTimeStamp());
        try {
            JSONObject obj = new JSONObject(response);
            addToDeliveryQueue(client, obj);
        } catch (ServletException x) {
            throw new BayeuxException(x);
        } catch (IOException x) {
            throw new BayeuxException(x);
        }
        return 0;
    }
}

