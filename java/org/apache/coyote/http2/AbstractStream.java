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
package org.apache.coyote.http2;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;

/**
 * Used to managed prioritisation.
 */
abstract class AbstractStream {

    private static final StringManager sm = StringManager.getManager(AbstractStream.class);

    private final Integer identifier;

    private volatile AbstractStream parentStream = null;
    private final Set<AbstractStream> childStreams = new HashSet<>();
    private volatile int weight = Constants.DEFAULT_WEIGHT;

    public Integer getIdentifier() {
        return identifier;
    }


    public AbstractStream(Integer identifier) {
        this.identifier = identifier;
    }


    public void rePrioritise(AbstractStream parent, boolean exclusive, int weight) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractStream.reprioritisation.debug",
                    Long.toString(getConnectionId()), identifier, Boolean.toString(exclusive),
                    parent.getIdentifier(), Integer.toString(weight)));
        }

        // Check if new parent is a descendant of this stream
        if (isDescendant(parent)) {
            parent.detachFromParent();
            parentStream.addChild(parent);
        }

        if (exclusive) {
            // Need to move children of the new parent to be children of this
            // stream. Slightly convoluted to avoid concurrent modification.
            Iterator<AbstractStream> parentsChildren = parent.getChildStreams().iterator();
            while (parentsChildren.hasNext()) {
                AbstractStream parentsChild = parentsChildren.next();
                parentsChildren.remove();
                this.addChild(parentsChild);
            }
        }
        parent.addChild(this);
        this.weight = weight;
    }


    void detachFromParent() {
        if (parentStream != null) {
            parentStream.getChildStreams().remove(this);
            parentStream = null;
        }
    }


    void addChild(AbstractStream child) {
        child.setParent(this);
        childStreams.add(child);
    }


    private void setParent(AbstractStream parent) {
        this.parentStream = parent;
    }


    boolean isDescendant(AbstractStream stream) {
        if (childStreams.contains(stream)) {
            return true;
        }
        for (AbstractStream child : childStreams) {
            if (child.isDescendant(stream)) {
                return true;
            }
        }
        return false;
    }


    AbstractStream getParentStream() {
        return parentStream;
    }


    void setParentStream(AbstractStream parentStream) {
        this.parentStream = parentStream;
    }


    Set<AbstractStream> getChildStreams() {
        return childStreams;
    }

    protected abstract Log getLog();

    protected abstract int getConnectionId();
}
