/*
 * Copyright 2002,2004 The Apache Software Foundation.
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


package org.apache.catalina.ant;


import org.apache.tools.ant.BuildException;


/**
 * Ant task that implements the JMX Query command 
 * (<code>/jmxproxy/?qry</code>) supported by the Tomcat manager application.
 *
 * @author Vivek Chopra
 * @version $Revision: 303236 $
 */
public class JMXQueryTask extends AbstractCatalinaTask {

    // Properties

    /**
     * The JMX query string 
     * @see #setQuery(String)
     */
    protected String query      = null;

    // Public Methods
    
    /**
     * Get method for the JMX query string
     * @return Query string
     */
    public String getQuery () {
        return this.query;
    }

    /**
     * Set method for the JMX query string.
    * <P>Examples of query format:
     * <UL>
     * <LI>*:*</LI>
     * <LI>*:type=RequestProcessor,*</LI>
     * <LI>*:j2eeType=Servlet,*</LI>
     * <LI>Catalina:type=Environment,resourcetype=Global,name=simpleValue</LI>
     * </UL>
     * </P> 
     * @param query JMX Query string
     */
    public void setQuery (String query) {
        this.query = query;
    }

    /**
     * Execute the requested operation.
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {
        super.execute();
        String queryString = (query == null) ? "":("?qry="+query);
        log("Query string is " + queryString); 
        execute ("/jmxproxy/" + queryString);
    }
}
