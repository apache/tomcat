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
package org.apache.catalina.ha;

import org.apache.catalina.Valve;

/**
 * Cluster valves are a simple extension to the Tomcat valve architecture
 * with a small addition of being able to reference the cluster component in the container it sits in.
 * @author Filip Hanik
 * @author Peter Rossbach
 * @version $Revision: 303842 $, $Date: 2005-04-10 11:20:46 -0500 (Sun, 10 Apr 2005) $
 */
public interface ClusterValve extends Valve{
    /**
     * Returns the cluster the cluster deployer is associated with
     * @return CatalinaCluster
     */
    public CatalinaCluster getCluster();

    /**
     * Associates the cluster deployer with a cluster
     * @param cluster CatalinaCluster
     */
    public void setCluster(CatalinaCluster cluster);
}
