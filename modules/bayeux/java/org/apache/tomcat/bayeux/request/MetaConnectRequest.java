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
import org.apache.tomcat.bayeux.BayeuxRequest;
import org.apache.tomcat.bayeux.ClientImpl;
import org.apache.tomcat.bayeux.TomcatBayeux;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.cometd.bayeux.Bayeux;
import org.apache.tomcat.bayeux.*;

/******************************************************************************
 * Handshake request Bayeux message.
 *
 * @author Guy A. Molinari
 * @author Filip Hanik
 * @version 1.0
 *
 */
public class MetaConnectRequest extends RequestBase implements BayeuxRequest {
    protected static HashMap<String,Object> responseTemplate = new HashMap<String,Object>();

    static {
        responseTemplate.put(Bayeux.CHANNEL_FIELD,Bayeux.META_CONNECT);
        responseTemplate.put(Bayeux.SUCCESSFUL_FIELD,Boolean.TRUE);
        responseTemplate.put(Bayeux.ADVICE_FIELD, new HashMap<String, Object>());
    }

    public MetaConnectRequest(TomcatBayeux tb, CometEvent event, JSONObject jsReq) throws JSONException {
        super(tb, event, jsReq);
        if (clientId!=null && getTomcatBayeux().hasClient(clientId)) {
            event.getHttpServletRequest().setAttribute("client",getTomcatBayeux().getClient(clientId));
        }
    }


    /**
     * Check client request for validity.
     *
     * Per section 4.2.1 of the Bayuex spec a connect request must contain:
     *  1) The "/meta/connect" channel identifier.
     *  2) The clientId returned by the server after handshake.
     *  3) The desired connectionType (must be one of the server's supported
     *     types returned by handshake response.
     *
     * @return HttpError This method returns null if no errors were found
     */
    public HttpError validate() {
        if(clientId==null|| (!getTomcatBayeux().hasClient(clientId)))
            return new HttpError(400,"Client Id not valid.", null);
        if (! (Bayeux.TRANSPORT_LONG_POLL.equals(conType) || Bayeux.TRANSPORT_CALLBACK_POLL.equals(conType)))
            return new HttpError(400,"Unsupported connection type.",null);
        return null;//no error
    }

    /**
     * Transition to connected state, flushing pending messages if
     * available.  If there are pending subscriptions and no messages to
     * flush then the connection is held until there is a pending publish
     * event to be delivered to this client (Section 4.2.2 of spec).
     */
    public int process(int prevops) throws BayeuxException {
        super.process(prevops);
        response = (HashMap<String, Object>)responseTemplate.clone();
        ClientImpl client = (ClientImpl)getTomcatBayeux().getClient(clientId);
        boolean success = false;
        HttpError error = validate();
        if (error == null) {
            client.setDesirectConnType(desiredConnTypeFlag);
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.RECONNECT_FIELD, Bayeux.RETRY_RESPONSE);
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.INTERVAL_FIELD, getReconnectInterval());
            success = true;
        }else {
            response.put(Bayeux.SUCCESSFUL_FIELD,Boolean.FALSE);
            response.put(Bayeux.ERROR_FIELD, error.toString());
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.RECONNECT_FIELD, Bayeux.HANDSHAKE_RESPONSE);
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

        //return immediately if there is no subscriptions
        //so that we can process the next message
        int result = client.isSubscribed()?1:0;

        if (success && client!=null && client.hasMessages()) {
            //send out messages
            flushMessages(client);
            result = 0; //flush out the messages
        }

        return result;
    }
}

