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
public class MetaHandshakeRequest extends RequestBase implements BayeuxRequest {

    protected static HashMap<String,Object> responseTemplate = new HashMap<String,Object>();

    static {
        responseTemplate.put(Bayeux.CHANNEL_FIELD,Bayeux.META_HANDSHAKE);
        responseTemplate.put(Bayeux.VERSION_FIELD,"1.0");
        responseTemplate.put(Bayeux.SUPP_CONNECTION_TYPE_FIELD,new String[] { Bayeux.TRANSPORT_LONG_POLL, Bayeux.TRANSPORT_CALLBACK_POLL });
        responseTemplate.put(Bayeux.SUCCESSFUL_FIELD,Boolean.TRUE);
        responseTemplate.put(Bayeux.ADVICE_FIELD, new HashMap<String, Object>());
    }

    public MetaHandshakeRequest(TomcatBayeux tomcatBayeux, CometEvent event, JSONObject jsReq) throws JSONException {
        super(tomcatBayeux, event, jsReq);
    }


    public String getVersion() { return version; }
    public String getMinimumVersion() { return minVersion; }


    /**
     * Check client request for validity.
     *
     * Per section 4.1.1 of the Bayuex spec a handshake request must contain:
     *  1) The "/meta/handshake" channel identifier.
     *  2) The version of the protocol supported by the client
     *  3) The client's supported connection types.
     *
     * @return HttpError This method returns null if no errors were found
     */
    public HttpError validate() {
        boolean error = (version==null || version.length()==0);
        if (!error) error = suppConnTypesFlag==0;
        if (error) return new HttpError(400,"Invalid handshake request, supportedConnectionType field missing.",null);
        else return null;
    }

    /**
     * Generate and return a client identifier.  Return a list of
     * supported connection types.  Must be a subset of or identical to
     * the list of types supported by the client.  See section 4.1.2 of
     * the Bayuex specification.
     */
    public int process(int prevops) throws BayeuxException {
        super.process(prevops);
        response = (HashMap<String, Object>)responseTemplate.clone();
        ClientImpl client = null;
        HttpError error = validate();
        if (error == null) {
            client = (ClientImpl) getTomcatBayeux().newClient("http-", null, false,getEvent());
            clientId = client.getId();
            client.setSupportedConnTypes(suppConnTypesFlag);
            client.setUseJsonFiltered(getExt().get(Bayeux.JSON_COMMENT_FILTERED_FIELD) != null);
            response.put(Bayeux.CLIENT_FIELD, client.getId());
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.RECONNECT_FIELD, Bayeux.RETRY_RESPONSE);
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.INTERVAL_FIELD, getReconnectInterval());
        }else {
            response.put(Bayeux.SUCCESSFUL_FIELD,Boolean.FALSE);
            response.put(Bayeux.ERROR_FIELD, error.toString());
            client = TomcatBayeux.getErrorClient();
            ((HashMap) response.get(Bayeux.ADVICE_FIELD)).put(Bayeux.RECONNECT_FIELD, Bayeux.NONE_RESPONSE);
        }
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

