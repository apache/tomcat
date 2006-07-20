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


package org.apache.catalina;


import java.util.EventObject;


/**
 * General event for notifying listeners of significant changes on a Container.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 302726 $ $Date: 2004-02-27 15:59:07 +0100 (ven., 27 févr. 2004) $
 */

public final class ContainerEvent
    extends EventObject {


    /**
     * The Container on which this event occurred.
     */
    private Container container = null;


    /**
     * The event data associated with this event.
     */
    private Object data = null;


    /**
     * The event type this instance represents.
     */
    private String type = null;


    /**
     * Construct a new ContainerEvent with the specified parameters.
     *
     * @param container Container on which this event occurred
     * @param type Event type
     * @param data Event data
     */
    public ContainerEvent(Container container, String type, Object data) {

        super(container);
        this.container = container;
        this.type = type;
        this.data = data;

    }


    /**
     * Return the event data of this event.
     */
    public Object getData() {

        return (this.data);

    }


    /**
     * Return the Container on which this event occurred.
     */
    public Container getContainer() {

        return (this.container);

    }


    /**
     * Return the event type of this event.
     */
    public String getType() {

        return (this.type);

    }


    /**
     * Return a string representation of this event.
     */
    public String toString() {

        return ("ContainerEvent['" + getContainer() + "','" +
                getType() + "','" + getData() + "']");

    }


}
