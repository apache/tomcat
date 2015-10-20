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
package org.apache.coyote;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * This is a light-weight abstract processor implementation that is intended as
 * a basis for all Processor implementations from the light-weight upgrade
 * processors to the HTTP/AJP processors.
 */
public abstract class AbstractProcessorLight implements Processor {

    private Set<DispatchType> dispatches = new CopyOnWriteArraySet<>();


    @Override
    public SocketState process(SocketWrapperBase<?> socketWrapper) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void addDispatch(DispatchType dispatchType) {
        synchronized (dispatches) {
            dispatches.add(dispatchType);
        }
    }


    @Override
    public Iterator<DispatchType> getIteratorAndClearDispatches() {
        // Note: Logic in AbstractProtocol depends on this method only returning
        // a non-null value if the iterator is non-empty. i.e. it should never
        // return an empty iterator.
        Iterator<DispatchType> result;
        synchronized (dispatches) {
            // Synchronized as the generation of the iterator and the clearing
            // of dispatches needs to be an atomic operation.
            result = dispatches.iterator();
            if (result.hasNext()) {
                dispatches.clear();
            } else {
                result = null;
            }
        }
        return result;
    }


    protected void clearDispatches() {
        synchronized (dispatches) {
            dispatches.clear();
        }
    }
}
