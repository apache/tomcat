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
package org.apache.catalina.realm;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.xml.sax.Attributes;

/**
 * <p>
 * <strong>RuleSet</strong> for recognizing the users defined in the XML file processed by <code>MemoryRealm</code>.
 * </p>
 *
 * @author Craig R. McClanahan
 */
public class MemoryRuleSet implements RuleSet {


    // ----------------------------------------------------- Instance Variables

    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor

    /**
     * Construct an instance of this <code>RuleSet</code> with the default matching pattern prefix.
     */
    public MemoryRuleSet() {
        this("tomcat-users/");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the trailing slash character)
     */
    public MemoryRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public void addRuleInstances(Digester digester) {

        digester.addRule(prefix + "user", new MemoryUserRule());

    }


}


/**
 * Private class used when parsing the XML database file.
 */
final class MemoryUserRule extends Rule {


    /**
     * Construct a new instance of this <code>Rule</code>.
     */
    MemoryUserRule() {
        // No initialisation required
    }


    /**
     * Process a <code>&lt;user&gt;</code> element from the XML database file.
     *
     * @param attributes The attribute list for this element
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {

        String username = attributes.getValue("username");
        if (username == null) {
            username = attributes.getValue("name");
        }
        String password = attributes.getValue("password");
        String roles = attributes.getValue("roles");

        MemoryRealm realm = (MemoryRealm) digester.peek(digester.getCount() - 1);
        realm.addUser(username, password, roles);

    }


}
