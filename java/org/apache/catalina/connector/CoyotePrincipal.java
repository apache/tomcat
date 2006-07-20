/*
 * Copyright 1999,2004 The Apache Software Foundation.
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


package org.apache.catalina.connector;

import java.security.Principal;

/**
 * Generic implementation of <strong>java.security.Principal</strong> that
 * is used to represent principals authenticated at the protocol handler level.
 *
 * @author Remy Maucherat
 * @version $Revision: 302975 $ $Date: 2004-06-23 10:25:04 +0200 (mer., 23 juin 2004) $
 */

public class CoyotePrincipal 
    implements Principal {


    // ----------------------------------------------------------- Constructors


    public CoyotePrincipal(String name) {

        this.name = name;

    }


    // ------------------------------------------------------------- Properties


    /**
     * The username of the user represented by this Principal.
     */
    protected String name = null;

    public String getName() {
        return (this.name);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object, which exposes only
     * information that should be public.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("CoyotePrincipal[");
        sb.append(this.name);
        sb.append("]");
        return (sb.toString());

    }


}
