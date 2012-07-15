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

package org.apache.catalina.manager.util;

import java.util.Comparator;

import org.apache.catalina.Session;

/**
 * Comparator which reverse the sort order
 * @author C&eacute;drik LIME
 */
public class ReverseComparator implements Comparator<Session> {
    protected final Comparator<Session> comparator;

    /**
     *
     */
    public ReverseComparator(Comparator<Session> comparator) {
        super();
        this.comparator = comparator;
    }

    /* (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    @Override
    public int compare(Session o1, Session o2) {
        int returnValue = comparator.compare(o1, o2);
        return (- returnValue);
    }
}
