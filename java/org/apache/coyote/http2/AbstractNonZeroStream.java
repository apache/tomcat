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

import java.util.Iterator;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for all streams other than stream 0, the connection. Primarily
 * provides functionality shared between full Stream and RecycledStream.
 */
abstract class AbstractNonZeroStream extends AbstractStream {

    private static final Log log = LogFactory.getLog(AbstractNonZeroStream.class);
    private static final StringManager sm = StringManager.getManager(AbstractNonZeroStream.class);

    protected final StreamStateMachine state;

    private volatile int weight = Constants.DEFAULT_WEIGHT;


    AbstractNonZeroStream(String connectionId, Integer identifier) {
        super(identifier);
        this.state = new StreamStateMachine(connectionId, getIdAsString());
    }


    AbstractNonZeroStream(Integer identifier, StreamStateMachine state) {
        super(identifier);
        this.state = state;
    }


    @Override
    final int getWeight() {
        return weight;
    }


    /*
     * General method used when reprioritising a stream and care needs to be
     * taken not to create circular references.
     *
     * Changes to the priority tree need to be sychronized at the connection
     * level. This is the caller's responsibility.
     */
    final void rePrioritise(AbstractStream parent, boolean exclusive, int weight) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reprioritisation.debug",
                    getConnectionId(), getIdAsString(), Boolean.toString(exclusive),
                    parent.getIdAsString(), Integer.toString(weight)));
        }

        // Check if new parent is a descendant of this stream
        if (isDescendant(parent)) {
            parent.detachFromParent();
            // Cast is always safe since any descendant of this stream must be
            // an instance of Stream
            getParentStream().addChild((Stream) parent);
        }

        if (exclusive) {
            // Need to move children of the new parent to be children of this
            // stream. Slightly convoluted to avoid concurrent modification.
            Iterator<AbstractNonZeroStream> parentsChildren = parent.getChildStreams().iterator();
            while (parentsChildren.hasNext()) {
                AbstractNonZeroStream parentsChild = parentsChildren.next();
                parentsChildren.remove();
                this.addChild(parentsChild);
            }
        }
        detachFromParent();
        parent.addChild(this);
        this.weight = weight;
    }


    /*
     * Used when removing closed streams from the tree and we know there is no
     * need to check for circular references.
     *
     * Changes to the priority tree need to be sychronized at the connection
     * level. This is the caller's responsibility.
     */
    final void rePrioritise(AbstractStream parent, int weight) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reprioritisation.debug",
                    getConnectionId(), getIdAsString(), Boolean.FALSE,
                    parent.getIdAsString(), Integer.toString(weight)));
        }

        parent.addChild(this);
        this.weight = weight;
    }


    /*
     * Used when "recycling" a stream and replacing a Stream instance with a
     * RecycledStream instance.
     *
     * Replace this stream with the provided stream in the parent/child
     * hierarchy.
     *
     * Changes to the priority tree need to be sychronized at the connection
     * level. This is the caller's responsibility.
     */
    void replaceStream(AbstractNonZeroStream replacement) {
        replacement.setParentStream(getParentStream());
        detachFromParent();
        for (AbstractNonZeroStream child : getChildStreams()) {
            replacement.addChild(child);
        }
        getChildStreams().clear();
        replacement.weight = weight;
    }


    final boolean isClosedFinal() {
        return state.isClosedFinal();
    }


    final void checkState(FrameType frameType) throws Http2Exception {
        state.checkFrameType(frameType);
    }
}
