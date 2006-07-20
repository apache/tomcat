/*
 * Copyright 2006 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

/**
 * Representation of a web service reference for a web application, as
 * represented in a <code>&lt;service-ref&gt;</code> element in the
 * deployment descriptor.
 *
 * @author Fabien Carrion
 * @version $Revision: 303342 $ $Date: 2005-03-15 23:29:49 -0700 (Web, 15 Mar 2006) $
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
     * An icon for this WebService.
     */
    private String serviceinterface = null;

    public String getServiceinterface() {
        return (this.serviceinterface);
    }

    public void setServiceinterface(String serviceinterface) {
        this.serviceinterface = serviceinterface;
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

    public void setServiceqname(String[] serviceqname) {
        this.serviceqname = serviceqname;
    }

    public void setServiceqname(String serviceqname, int i) {
        this.serviceqname[i] = serviceqname;
    }

    public void setNamespaceURI(String namespaceuri) {
        this.serviceqname[0] = namespaceuri;
    }

    public void setLocalpart(String localpart) {
        this.serviceqname[1] = localpart;
    }

    /**
     * Declares a client dependency on the container to resolving a Service Endpoint Interface
     * to a WSDL port. It optionally associates the Service Endpoint Interface with a
     * particular port-component.
     *
     * portcomponent[0] : service-endpoint-interface
     * portcomponent[1] : port-component-link
     */
    private String[] portcomponent = new String[2];

    public String[] getPortcomponent() {
        return (this.portcomponent);
    }

    public void setPortcomponent(String[] portcomponent) {
        this.portcomponent = portcomponent;
    }

    public void setPortcomponent(String portcomponent, int i) {
        this.portcomponent[i] = portcomponent;
    }

    public void setServiceendpoint(String serviceendpoint) {
        this.portcomponent[0] = serviceendpoint;
    }

    public void setPortlink(String portlink) {
        this.portcomponent[1] = portlink;
    }

    /**
     * A list of Handler to use for this service-ref.
     *
     * The instanciation of the handler have to be done.
     */
    private String handler = null;

    public String getHandler() {
        return (this.handler);
    }

    public void setHandler(String handler) {
        this.handler = handler;
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
        if (serviceqname != null) {
            sb.append(", service-qname=");
            sb.append(serviceqname);
        }
        if (portcomponent != null) {
            sb.append(", port-component=");
            sb.append(portcomponent);
        }
        if (handler != null) {
            sb.append(", handler=");
            sb.append(handler);
        }
        sb.append("]");
        return (sb.toString());

    }

}
