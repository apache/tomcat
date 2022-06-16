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
package org.apache.tomcat.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps a list of throwables as a single throwable. This is intended to be used
 * when multiple actions are taken where each may throw an exception but all
 * actions are taken before any errors are reported.
 * <p>
 * This class is <b>NOT</b> threadsafe.
 */
public class MultiThrowable extends Throwable {

    private static final long serialVersionUID = 1L;

    private List<Throwable> throwables = new ArrayList<>();

    /**
     * Add a throwable to the list of wrapped throwables.
     *
     * @param t The throwable to add
     */
    public void add(Throwable t) {
        throwables.add(t);
    }


    /**
     * @return A read-only list of the wrapped throwables.
     */
    public List<Throwable> getThrowables() {
        return Collections.unmodifiableList(throwables);
    }


    /**
     * @return {@code null} if there are no wrapped throwables, the Throwable if
     *         there is a single wrapped throwable or the current instance of
     *         there are multiple wrapped throwables
     */
    public Throwable getThrowable() {
        if (size() == 0) {
            return null;
        } else if (size() == 1) {
            return throwables.get(0);
        } else {
            return this;
        }
    }


    /**
     * @return The number of throwables currently wrapped by this instance.
     */
    public int size() {
        return throwables.size();
    }


    /**
     * Overrides the default implementation to provide a concatenation of the
     * messages associated with each of the wrapped throwables. Note that the
     * format of the returned String is not guaranteed to be fixed and may
     * change in a future release.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(": ");
        sb.append(size());
        sb.append(" wrapped Throwables: ");
        for (Throwable t : throwables) {
            sb.append('[');
            sb.append(t.getMessage());
            sb.append(']');
        }

        return sb.toString();
    }
}
