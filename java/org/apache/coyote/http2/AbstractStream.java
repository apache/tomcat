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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for all streams including the connection (referred to as Stream 0)
 * and is used primarily when managing prioritization.
 */
abstract class AbstractStream {

    private static final Log log = LogFactory.getLog(AbstractStream.class);
    private static final StringManager sm = StringManager.getManager(AbstractStream.class);

    private final Integer identifier;
    private final String idAsString;

    private volatile AbstractStream parentStream = null;
    private final Set<AbstractNonZeroStream> childStreams =
            Collections.newSetFromMap(new ConcurrentHashMap<AbstractNonZeroStream,Boolean>());
    private long windowSize = ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;


    AbstractStream(Integer identifier) {
        this.identifier = identifier;
        this.idAsString = identifier.toString();
    }


    final Integer getIdentifier() {
        return identifier;
    }


    final String getIdAsString() {
        return idAsString;
    }


    final int getIdAsInt() {
        return identifier.intValue();
    }


    final void detachFromParent() {
        if (parentStream != null) {
            parentStream.getChildStreams().remove(this);
            parentStream = null;
        }
    }


    final void addChild(AbstractNonZeroStream child) {
        child.setParentStream(this);
        childStreams.add(child);
    }


    final boolean isDescendant(AbstractStream stream) {
        // Is the passed in Stream a descendant of this Stream?
        // Start at the passed in Stream and work up
        AbstractStream parent = stream.getParentStream();
        while (parent != null && parent != this) {
            parent = parent.getParentStream();
        }
        return parent != null;
    }


    final AbstractStream getParentStream() {
        return parentStream;
    }


    final void setParentStream(AbstractStream parentStream) {
        this.parentStream = parentStream;
    }


    final Set<AbstractNonZeroStream> getChildStreams() {
        return childStreams;
    }


    final synchronized void setWindowSize(long windowSize) {
        this.windowSize = windowSize;
    }


    final synchronized long getWindowSize() {
        return windowSize;
    }


    /**
     * Increment window size.
     * @param increment The amount by which the window size should be increased
     * @throws Http2Exception If the window size is now higher than
     *  the maximum allowed
     */
    synchronized void incrementWindowSize(int increment) throws Http2Exception {
        // No need for overflow protection here.
        // Increment can't be more than Integer.MAX_VALUE and once windowSize
        // goes beyond 2^31-1 an error is triggered.
        windowSize += increment;

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("abstractStream.windowSizeInc", getConnectionId(),
                    getIdAsString(), Integer.toString(increment), Long.toString(windowSize)));
        }

        if (windowSize > ConnectionSettingsBase.MAX_WINDOW_SIZE) {
            String msg = sm.getString("abstractStream.windowSizeTooBig", getConnectionId(), identifier,
                    Integer.toString(increment), Long.toString(windowSize));
            if (identifier.intValue() == 0) {
                throw new ConnectionException(msg, Http2Error.FLOW_CONTROL_ERROR);
            } else {
                throw new StreamException(
                        msg, Http2Error.FLOW_CONTROL_ERROR, identifier.intValue());
            }
        }
    }


    final synchronized void decrementWindowSize(int decrement) {
        // No need for overflow protection here. Decrement can never be larger
        // the Integer.MAX_VALUE and once windowSize goes negative no further
        // decrements are permitted
        windowSize -= decrement;
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("abstractStream.windowSizeDec", getConnectionId(),
                    getIdAsString(), Integer.toString(decrement), Long.toString(windowSize)));
        }
    }


    abstract String getConnectionId();

    abstract int getWeight();
}
