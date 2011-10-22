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


/**
 * <p>Representation of a message destination reference for a web application,
 * as represented in a <code>&lt;message-destination-ref&gt;</code> element
 * in the deployment descriptor.</p>
 *
 * @author Craig R. McClanahan
 * @version $Id$
 * @since Tomcat 5.0
 */

public class MessageDestinationRef extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * The link of this destination ref.
     */
    private String link = null;

    public String getLink() {
        return (this.link);
    }

    public void setLink(String link) {
        this.link = link;
    }


    /**
     * The usage of this destination ref.
     */
    private String usage = null;

    public String getUsage() {
        return (this.usage);
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("MessageDestination[");
        sb.append("name=");
        sb.append(getName());
        if (link != null) {
            sb.append(", link=");
            sb.append(link);
        }
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (usage != null) {
            sb.append(", usage=");
            sb.append(usage);
        }
        if (getDescription() != null) {
            sb.append(", description=");
            sb.append(getDescription());
        }
        sb.append("]");
        return (sb.toString());

    }


}
