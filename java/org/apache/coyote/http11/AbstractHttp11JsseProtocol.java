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
package org.apache.coyote.http11;

import org.apache.tomcat.util.net.AbstractEndpoint;

public abstract class AbstractHttp11JsseProtocol<S>
        extends AbstractHttp11Protocol<S> {

    public AbstractHttp11JsseProtocol(AbstractEndpoint<S> endpoint) {
        super(endpoint);
    }

    public String getAlgorithm() { return getEndpoint().getAlgorithm();}
    public void setAlgorithm(String s ) { getEndpoint().setAlgorithm(s);}

    public String getClientAuth() { return getEndpoint().getClientAuth();}
    public void setClientAuth(String s ) { getEndpoint().setClientAuth(s);}

    public String getKeystorePass() { return getEndpoint().getKeystorePass();}
    public void setKeystorePass(String s ) { getEndpoint().setKeystorePass(s);}

    public String getKeystoreType() { return getEndpoint().getKeystoreType();}
    public void setKeystoreType(String s ) { getEndpoint().setKeystoreType(s);}

    public String getKeystoreProvider() {
        return getEndpoint().getKeystoreProvider();
    }
    public void setKeystoreProvider(String s ) {
        getEndpoint().setKeystoreProvider(s);
    }

    public String getSslProtocol() { return getEndpoint().getSslProtocol();}
    public void setSslProtocol(String s) { getEndpoint().setSslProtocol(s);}

    public String getCiphers() { return getEndpoint().getCiphers();}
    public void setCiphers(String s) { getEndpoint().setCiphers(s);}

    public String getKeyAlias() { return getEndpoint().getKeyAlias();}
    public void setKeyAlias(String s ) { getEndpoint().setKeyAlias(s);}

    public String getKeyPass() { return getEndpoint().getKeyPass();}
    public void setKeyPass(String s ) { getEndpoint().setKeyPass(s);}

    public void setTruststoreFile(String f){ getEndpoint().setTruststoreFile(f);}
    public String getTruststoreFile(){ return getEndpoint().getTruststoreFile();}

    public void setTruststorePass(String p){ getEndpoint().setTruststorePass(p);}
    public String getTruststorePass(){return getEndpoint().getTruststorePass();}

    public void setTruststoreType(String t){ getEndpoint().setTruststoreType(t);}
    public String getTruststoreType(){ return getEndpoint().getTruststoreType();}

    public void setTruststoreProvider(String t){
        getEndpoint().setTruststoreProvider(t);
    }
    public String getTruststoreProvider(){
        return getEndpoint().getTruststoreProvider();
    }

    public void setTruststoreAlgorithm(String a){
        getEndpoint().setTruststoreAlgorithm(a);
    }
    public String getTruststoreAlgorithm(){
        return getEndpoint().getTruststoreAlgorithm();
    }

    public void setTrustMaxCertLength(String s){
        getEndpoint().setTrustMaxCertLength(s);
    }
    public String getTrustMaxCertLength(){
        return getEndpoint().getTrustMaxCertLength();
    }

    public void setCrlFile(String s){getEndpoint().setCrlFile(s);}
    public String getCrlFile(){ return getEndpoint().getCrlFile();}

    public void setSessionCacheSize(String s){getEndpoint().setSessionCacheSize(s);}
    public String getSessionCacheSize(){ return getEndpoint().getSessionCacheSize();}

    public void setSessionTimeout(String s){getEndpoint().setSessionTimeout(s);}
    public String getSessionTimeout(){ return getEndpoint().getSessionTimeout();}

    public String getSslImplementationName() { return getEndpoint().getSslImplementationName(); }
    public void setSslImplementationName(String s) { getEndpoint().setSslImplementationName(s); }
}
