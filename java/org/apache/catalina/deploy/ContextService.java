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


package org.apache.catalina.deploy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;

/**
 * Representation of a web service reference for a web application, as
 * represented in a <code>&lt;service-ref&gt;</code> element in the
 * deployment descriptor.
 *
 * @author Fabien Carrion
 * @version $Revision$ $Date$
 */

public class ContextService extends ResourceBase implements Serializable {


    // ------------------------------------------------------------- Properties


    /**
     * The WebService reference name.
     */
    private String displayname = null;

    public String getDisplayname() {
        return (this.displayname);
    }

    public void setDisplayname(String displayname) {
        this.displayname = displayname;
    }

    /**
     * An icon for this WebService.
     */
    private String icon = null;

    public String getIcon() {
        return (this.icon);
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * Contains the location (relative to the root of
     * the module) of the web service WSDL description.
     */
    private String wsdlfile = null;

    public String getWsdlfile() {
        return (this.wsdlfile);
    }

    public void setWsdlfile(String wsdlfile) {
        this.wsdlfile = wsdlfile;
    }

    /**
     * A file specifying the correlation of the WSDL definition
     * to the interfaces (Service Endpoint Interface, Service Interface). 
     */
    private String jaxrpcmappingfile = null;

    public String getJaxrpcmappingfile() {
        return (this.jaxrpcmappingfile);
    }

    public void setJaxrpcmappingfile(String jaxrpcmappingfile) {
        this.jaxrpcmappingfile = jaxrpcmappingfile;
    }

    /**
     * Declares the specific WSDL service element that is being referred to.
     * It is not specified if no wsdl-file is declared or if WSDL contains only
     * 1 service element.
     *
     * A service-qname is composed by a namespaceURI and a localpart.
     * It must be defined if more than 1 service is declared in the WSDL.
     *
     * serviceqname[0] : namespaceURI
     * serviceqname[1] : localpart
     */
    private String[] serviceqname = new String[2];

    public String[] getServiceqname() {
        return (this.serviceqname);
    }

    public String getServiceqname(int i) {
        return this.serviceqname[i];
    }

    public String getServiceqnameNamespaceURI() {
        return this.serviceqname[0];
    }

    public String getServiceqnameLocalpart() {
        return this.serviceqname[1];
    }

    public void setServiceqname(String[] serviceqname) {
        this.serviceqname = serviceqname;
    }

    public void setServiceqname(String serviceqname, int i) {
        this.serviceqname[i] = serviceqname;
    }

    public void setServiceqnameNamespaceURI(String namespaceuri) {
        this.serviceqname[0] = namespaceuri;
    }

    public void setServiceqnameLocalpart(String localpart) {
        this.serviceqname[1] = localpart;
    }

    /**
     * Declares a client dependency on the container to resolving a Service Endpoint Interface
     * to a WSDL port. It optionally associates the Service Endpoint Interface with a
     * particular port-component.
     *
     */
    public Iterator getServiceendpoints() {
        return this.listProperties();
    }

    public String getPortlink(String serviceendpoint) {
        return (String) this.getProperty(serviceendpoint);
    }

    public void addPortcomponent(String serviceendpoint, String portlink) {
        if (portlink == null)
            portlink = "";
        this.setProperty(serviceendpoint, portlink);
    }

    /**
     * A list of Handlers to use for this service-ref.
     *
     * The instanciation of the handler have to be done.
     */
    private HashMap handlers = new HashMap();

    public Iterator getHandlers() {
        return handlers.keySet().iterator();
    }

    public ContextHandler getHandler(String handlername) {
        return (ContextHandler) handlers.get(handlername);
    }

    public void addHandler(ContextHandler handler) {
        handlers.put(handler.getName(), handler);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("ContextService[");
        sb.append("name=");
        sb.append(getName());
        if (getDescription() != null) {
            sb.append(", description=");
            sb.append(getDescription());
        }
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (displayname != null) {
            sb.append(", displayname=");
            sb.append(displayname);
        }
        if (icon != null) {
            sb.append(", icon=");
            sb.append(icon);
        }
        if (wsdlfile != null) {
            sb.append(", wsdl-file=");
            sb.append(wsdlfile);
        }
        if (jaxrpcmappingfile != null) {
            sb.append(", jaxrpc-mapping-file=");
            sb.append(jaxrpcmappingfile);
        }
        if (serviceqname[0] != null) {
            sb.append(", service-qname/namespaceURI=");
            sb.append(serviceqname[0]);
        }
        if (serviceqname[1] != null) {
            sb.append(", service-qname/localpart=");
            sb.append(serviceqname[1]);
        }
        if (this.getServiceendpoints() != null) {
            sb.append(", port-component/service-endpoint-interface=");
            sb.append(this.getServiceendpoints());
        }
        if (handlers != null) {
            sb.append(", handler=");
            sb.append(handlers);
        }
        sb.append("]");
        return (sb.toString());

    }

}
