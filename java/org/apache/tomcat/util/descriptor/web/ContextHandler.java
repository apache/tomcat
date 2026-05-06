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
package org.apache.tomcat.util.descriptor.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Representation of a handler reference for a web service, as represented in a <code>&lt;handler&gt;</code> element in
 * the deployment descriptor.
 */
public class ContextHandler extends ResourceBase {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor for ContextHandler.
     */
    public ContextHandler() {
    }

    // ------------------------------------------------------------- Properties


    /**
     * The Handler reference class.
     */
    private String handlerclass = null;

    /**
     * Returns the Handler reference class.
     *
     * @return the handler class name
     */
    public String getHandlerclass() {
        return this.handlerclass;
    }

    /**
     * Sets the Handler reference class.
     *
     * @param handlerclass the handler class name
     */
    public void setHandlerclass(String handlerclass) {
        this.handlerclass = handlerclass;
    }

    /**
     * A list of QName specifying the SOAP Headers the handler will work on. -namespace and localpart values must be
     * found inside the WSDL.
     * <p>
     * A service-qname is composed by a namespaceURI and a localpart.
     * <p>
     * soapHeader[0] : namespaceURI soapHeader[1] : localpart
     */
    private final Map<String,String> soapHeaders = new HashMap<>();

    /**
     * Returns the iterator of local parts for SOAP headers.
     *
     * @return iterator of local part names
     */
    public Iterator<String> getLocalparts() {
        return soapHeaders.keySet().iterator();
    }

    /**
     * Returns the namespace URI for the given local part.
     *
     * @param localpart the local part name
     * @return the namespace URI
     */
    public String getNamespaceuri(String localpart) {
        return soapHeaders.get(localpart);
    }

    /**
     * Adds a SOAP header with the given local part and namespace URI.
     *
     * @param localpart the local part name
     * @param namespaceuri the namespace URI
     */
    public void addSoapHeaders(String localpart, String namespaceuri) {
        soapHeaders.put(localpart, namespaceuri);
    }

    /**
     * Set a configured property.
     *
     * @param name  The property name
     * @param value The property value
     */
    public void setProperty(String name, String value) {
        this.setProperty(name, (Object) value);
    }

    /**
     * The soapRole.
     */
    private final List<String> soapRoles = new ArrayList<>();

    /**
     * Returns the SOAP role at the given index.
     *
     * @param i the index
     * @return the SOAP role
     */
    public String getSoapRole(int i) {
        return this.soapRoles.get(i);
    }

    /**
     * Returns the number of SOAP roles.
     *
     * @return the SOAP roles count
     */
    public int getSoapRolesSize() {
        return this.soapRoles.size();
    }

    /**
     * Adds a SOAP role.
     *
     * @param soapRole the SOAP role to add
     */
    public void addSoapRole(String soapRole) {
        this.soapRoles.add(soapRole);
    }

    /**
     * The portName.
     */
    private final List<String> portNames = new ArrayList<>();

    /**
     * Returns the port name at the given index.
     *
     * @param i the index
     * @return the port name
     */
    public String getPortName(int i) {
        return this.portNames.get(i);
    }

    /**
     * Returns the number of port names.
     *
     * @return the port names count
     */
    public int getPortNamesSize() {
        return this.portNames.size();
    }

    /**
     * Adds a port name.
     *
     * @param portName the port name to add
     */
    public void addPortName(String portName) {
        this.portNames.add(portName);
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextHandler[");
        sb.append("name=");
        sb.append(getName());
        if (handlerclass != null) {
            sb.append(", class=");
            sb.append(handlerclass);
        }
        if (!soapHeaders.isEmpty()) {
            sb.append(", soap-headers=");
            sb.append(this.soapHeaders);
        }
        if (this.getSoapRolesSize() > 0) {
            sb.append(", soap-roles=");
            sb.append(soapRoles);
        }
        if (this.getPortNamesSize() > 0) {
            sb.append(", port-name=");
            sb.append(portNames);
        }
        if (this.listProperties() != null) {
            sb.append(", init-param=");
            sb.append(this.listProperties());
        }
        sb.append(']');
        return sb.toString();
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((handlerclass == null) ? 0 : handlerclass.hashCode());
        result = prime * result + portNames.hashCode();
        result = prime * result + soapHeaders.hashCode();
        result = prime * result + soapRoles.hashCode();
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ContextHandler other = (ContextHandler) obj;
        if (handlerclass == null) {
            if (other.handlerclass != null) {
                return false;
            }
        } else if (!handlerclass.equals(other.handlerclass)) {
            return false;
        }
        if (!portNames.equals(other.portNames)) {
            return false;
        }
        if (!soapHeaders.equals(other.soapHeaders)) {
            return false;
        }
        return soapRoles.equals(other.soapRoles);
    }
}
