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


/**
 * Representation of a resource reference for a web application, as represented in a <code>&lt;resource-ref&gt;</code>
 * element in the deployment descriptor.
 */
public class ContextResource extends ResourceBase {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor for ContextResource.
     */
    public ContextResource() {
    }

    // ------------------------------------------------------------- Properties


    /**
     * The authorization requirement for this resource (<code>Application</code> or <code>Container</code>).
     */
    private String auth = null;

    /**
     * Returns the authorization requirement for this resource.
     *
     * @return the authorization type
     */
    public String getAuth() {
        return this.auth;
    }

    /**
     * Sets the authorization requirement for this resource.
     *
     * @param auth the authorization type
     */
    public void setAuth(String auth) {
        this.auth = auth;
    }

    /**
     * The sharing scope of this resource factory (<code>Shareable</code> or <code>Unshareable</code>).
     */
    private String scope = "Shareable";

    /**
     * Returns the sharing scope of this resource factory.
     *
     * @return the sharing scope
     */
    public String getScope() {
        return this.scope;
    }

    /**
     * Sets the sharing scope of this resource factory.
     *
     * @param scope the sharing scope
     */
    public void setScope(String scope) {
        this.scope = scope;
    }


    /**
     * Is this resource known to be a singleton resource. The default value is true since this is what users expect
     * although the Jakarta EE spec implies that the default should be false.
     */
    private boolean singleton = true;

    /**
     * Returns whether this resource is a singleton.
     *
     * @return true if the resource is a singleton
     */
    public boolean getSingleton() {
        return singleton;
    }

    /**
     * Sets whether this resource is a singleton.
     *
     * @param singleton true if the resource is a singleton
     */
    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }


    /**
     * The name of the zero argument method to be called when the resource is no longer required to clean-up resources.
     * This method must only speed up the clean-up of resources that would otherwise happen via garbage collection.
     */
    private String closeMethod = null;
    private boolean closeMethodConfigured = false;

    /**
     * Returns the close method name.
     *
     * @return the close method name
     */
    public String getCloseMethod() {
        return closeMethod;
    }

    /**
     * Sets the close method name.
     *
     * @param closeMethod the close method name
     */
    public void setCloseMethod(String closeMethod) {
        closeMethodConfigured = true;
        this.closeMethod = closeMethod;
    }

    /**
     * Returns whether the close method has been explicitly configured.
     *
     * @return true if the close method was configured
     */
    public boolean getCloseMethodConfigured() {
        return closeMethodConfigured;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextResource[");
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
        if (auth != null) {
            sb.append(", auth=");
            sb.append(auth);
        }
        if (scope != null) {
            sb.append(", scope=");
            sb.append(scope);
        }
        sb.append(']');
        return sb.toString();
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((auth == null) ? 0 : auth.hashCode());
        result = prime * result + ((closeMethod == null) ? 0 : closeMethod.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
        result = prime * result + (singleton ? 1231 : 1237);
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
        ContextResource other = (ContextResource) obj;
        if (auth == null) {
            if (other.auth != null) {
                return false;
            }
        } else if (!auth.equals(other.auth)) {
            return false;
        }
        if (closeMethod == null) {
            if (other.closeMethod != null) {
                return false;
            }
        } else if (!closeMethod.equals(other.closeMethod)) {
            return false;
        }
        if (scope == null) {
            if (other.scope != null) {
                return false;
            }
        } else if (!scope.equals(other.scope)) {
            return false;
        }
        return singleton == other.singleton;
    }
}
