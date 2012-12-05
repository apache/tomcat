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
package websocket.echo;

import javax.websocket.DefaultServerConfiguration;
import javax.websocket.EndpointFactory;

public class EchoEndpointConfiguration
        extends DefaultServerConfiguration<EchoEndpoint> {

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        // No origin checks
        return true;
    }


    @Override
    public String getPath() {
        return "/websocket/echoProgrammatic";
    }


    private static final EndpointFactory<EchoEndpoint> factory =
            new EchoEndpointFactory();


    @Override
    public EndpointFactory<EchoEndpoint> getEndpointFactory() {
        return factory;
    }


    private static class EchoEndpointFactory implements
            EndpointFactory<EchoEndpoint> {

        @Override
        public EchoEndpoint createEndpoint() {
            return new EchoEndpoint();
        }
    }
}
