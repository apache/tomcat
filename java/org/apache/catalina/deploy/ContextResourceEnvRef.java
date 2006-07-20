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


package org.apache.catalina.deploy;

import java.io.Serializable;


/**
 * Representation of an application resource reference, as represented in
 * an <code>&lt;res-env-refy&gt;</code> element in the deployment descriptor.
 *
 * @author Craig R. McClanahan
 * @author Peter Rossbach (pero@apache.org)
 * @version $Revision: 303342 $ $Date: 2004-10-05 09:56:49 +0200 (mar., 05 oct. 2004) $
 */

public class ContextResourceEnvRef extends ResourceBase implements Serializable {


    // ------------------------------------------------------------- Properties

    /**
     * Does this environment entry allow overrides by the application
     * deployment descriptor?
     */
    private boolean override = true;

    public boolean getOverride() {
        return (this.override);
    }

    public void setOverride(boolean override) {
        this.override = override;
    }
    
    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("ContextResourceEnvRef[");
        sb.append("name=");
        sb.append(getName());
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        sb.append(", override=");
        sb.append(override);
        sb.append("]");
        return (sb.toString());

    }



}
