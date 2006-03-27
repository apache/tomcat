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

package org.apache.naming.resources;

import javax.naming.Name;
import javax.naming.NameNotFoundException;

/**
 * Immutable exception to avoid useless object creation by the proxy context.
 * This should be used only by the proxy context. Actual contexts should return
 * properly populated exceptions.
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @version $Revision: 302726 $
 */
public class ImmutableNameNotFoundException
    extends NameNotFoundException {

    public void appendRemainingComponent(String name) {}
    public void appendRemainingName(Name name) {}
    public void setRemainingName(Name name) {}
    public void setResolverName(Name name) {}
    public void setRootCause(Throwable e) {}

}
