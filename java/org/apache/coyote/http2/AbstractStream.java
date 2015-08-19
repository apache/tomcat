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
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Used to managed prioritisation.
 */
abstract class AbstractStream {

    private static final Log log = LogFactory.getLog(AbstractStream.class);
    private static final StringManager sm = StringManager.getManager(AbstractStream.class);

    private final Integer identifier;

    private volatile AbstractStream parentStream = null;
    private final Set<AbstractStream> childStreams = new HashSet<>();
    private long windowSize = ConnectionSettingsRemote.DEFAULT_INITIAL_WINDOW_SIZE;

    public Integer getIdentifier() {
        return identifier;
    }


    public AbstractStream(Integer identifier) {
        this.identifier = identifier;
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


    protected synchronized void setWindowSize(long windowSize) {
        this.windowSize = windowSize;
    }


    protected synchronized long getWindowSize() {
        return windowSize;
    }


    /**
     * @param increment
     * @throws Http2Exception
     */
    protected synchronized void incrementWindowSize(int increment) throws Http2Exception {
        // No need for overflow protection here.
        // Increment can't be more than Integer.MAX_VALUE and once windowSize
        // goes beyond 2^31-1 an error is triggered.
        windowSize += increment;

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("abstractStream.windowSizeInc", getConnectionId(),
                    getIdentifier(), Integer.toString(increment), Long.toString(windowSize)));
        }

        if (windowSize > ConnectionSettingsRemote.MAX_WINDOW_SIZE) {
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


    protected synchronized void decrementWindowSize(int decrement) {
        // No need for overflow protection here. Decrement can never be larger
        // the Integer.MAX_VALUE and once windowSize goes negative no further
        // decrements are permitted
        windowSize -= decrement;
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("abstractStream.windowSizeDec", getConnectionId(),
                    getIdentifier(), Integer.toString(decrement), Long.toString(windowSize)));
        }
    }


    protected abstract String getConnectionId();

    protected abstract int getWeight();
}
