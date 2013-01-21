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
package org.apache.catalina.valves.rewrite;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;

public class Substitution {

    public abstract class SubstitutionElement {
        public abstract String evaluate(Matcher rule, Matcher cond, Resolver resolver);
    }

    public class StaticElement extends SubstitutionElement {
        public String value;

        @Override
        public String evaluate
            (Matcher rule, Matcher cond, Resolver resolver) {
            return value;
        }

    }

    public class RewriteRuleBackReferenceElement extends SubstitutionElement {
        public int n;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return rule.group(n);
        }
    }

    public class RewriteCondBackReferenceElement extends SubstitutionElement {
        public int n;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return cond.group(n);
        }
    }

    public class ServerVariableElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolve(key);
        }
    }

    public class ServerVariableEnvElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolveEnv(key);
        }
    }

    public class ServerVariableSslElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolveSsl(key);
        }
    }

    public class ServerVariableHttpElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolveHttp(key);
        }
    }

    public class MapElement extends SubstitutionElement {
        public RewriteMap map = null;
        public String key;
        public String defaultValue = null;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            String result = map.lookup(key);
            if (result == null) {
                result = defaultValue;
            }
            return result;
        }
    }

    protected SubstitutionElement[] elements = null;

    protected String sub = null;
    public String getSub() { return sub; }
    public void setSub(String sub) { this.sub = sub; }

    public void parse(Map<String, RewriteMap> maps) {

        ArrayList<SubstitutionElement> elements = new ArrayList<>();
        int pos = 0;
        int percentPos = 0;
        int dollarPos = 0;

        while (pos < sub.length()) {
            percentPos = sub.indexOf('%', pos);
            dollarPos = sub.indexOf('$', pos);
            if (percentPos == -1 && dollarPos == -1) {
                // Static text
                StaticElement newElement = new StaticElement();
                newElement.value = sub.substring(pos, sub.length());
                pos = sub.length();
                elements.add(newElement);
            } else if (percentPos == -1 || ((dollarPos != -1) && (dollarPos < percentPos))) {
                // $: back reference to rule or map lookup
                if (dollarPos + 1 == sub.length()) {
                    throw new IllegalArgumentException(sub);
                }
                if (pos < dollarPos) {
                    // Static text
                    StaticElement newElement = new StaticElement();
                    newElement.value = sub.substring(pos, dollarPos);
                    pos = dollarPos;
                    elements.add(newElement);
                }
                if (Character.isDigit(sub.charAt(dollarPos + 1))) {
                    // $: back reference to rule
                    RewriteRuleBackReferenceElement newElement = new RewriteRuleBackReferenceElement();
                    newElement.n = Character.digit(sub.charAt(dollarPos + 1), 10);
                    pos = dollarPos + 2;
                    elements.add(newElement);
                } else {
                    // $: map lookup as ${mapname:key|default}
                    MapElement newElement = new MapElement();
                    int open = sub.indexOf('{', dollarPos);
                    int colon = sub.indexOf(':', dollarPos);
                    int def = sub.indexOf('|', dollarPos);
                    int close = sub.indexOf('}', dollarPos);
                    if (!(-1 < open && open < colon && colon < close)) {
                        throw new IllegalArgumentException(sub);
                    }
                    newElement.map = maps.get(sub.substring(open + 1, colon));
                    if (newElement.map == null) {
                        throw new IllegalArgumentException(sub + ": No map: " + sub.substring(open + 1, colon));
                    }
                    if (def > -1) {
                        if (!(colon < def && def < close)) {
                            throw new IllegalArgumentException(sub);
                        }
                        newElement.key = sub.substring(colon + 1, def);
                        newElement.defaultValue = sub.substring(def + 1, close);
                    } else {
                        newElement.key = sub.substring(colon + 1, close);
                    }
                    pos = close + 1;
                    elements.add(newElement);
                }
            } else {
                // %: back reference to condition or server variable
                if (percentPos + 1 == sub.length()) {
                    throw new IllegalArgumentException(sub);
                }
                if (pos < percentPos) {
                    // Static text
                    StaticElement newElement = new StaticElement();
                    newElement.value = sub.substring(pos, percentPos);
                    pos = percentPos;
                    elements.add(newElement);
                }
                if (Character.isDigit(sub.charAt(percentPos + 1))) {
                    // %: back reference to condition
                    RewriteCondBackReferenceElement newElement = new RewriteCondBackReferenceElement();
                    newElement.n = Character.digit(sub.charAt(percentPos + 1), 10);
                    pos = percentPos + 2;
                    elements.add(newElement);
                } else {
                    // %: server variable as %{variable}
                    SubstitutionElement newElement = null;
                    int open = sub.indexOf('{', percentPos);
                    int colon = sub.indexOf(':', percentPos);
                    int close = sub.indexOf('}', percentPos);
                    if (!(-1 < open && open < close)) {
                        throw new IllegalArgumentException(sub);
                    }
                    if (colon > -1) {
                        if (!(open < colon && colon < close)) {
                            throw new IllegalArgumentException(sub);
                        }
                        String type = sub.substring(open + 1, colon);
                        if (type.equals("ENV")) {
                            newElement = new ServerVariableEnvElement();
                            ((ServerVariableEnvElement) newElement).key = sub.substring(colon + 1, close);
                        } else if (type.equals("SSL")) {
                            newElement = new ServerVariableSslElement();
                            ((ServerVariableSslElement) newElement).key = sub.substring(colon + 1, close);
                        } else if (type.equals("HTTP")) {
                            newElement = new ServerVariableHttpElement();
                            ((ServerVariableHttpElement) newElement).key = sub.substring(colon + 1, close);
                        } else {
                            throw new IllegalArgumentException(sub + ": Bad type: " + type);
                        }
                    } else {
                        newElement = new ServerVariableElement();
                        ((ServerVariableElement) newElement).key = sub.substring(open + 1, close);
                    }
                    pos = close + 1;
                    elements.add(newElement);
                }
            }
        }

        this.elements = elements.toArray(new SubstitutionElement[0]);

    }

    /**
     * Evaluate the substitution based on the context
     *
     * @param rule corresponding matched rule
     * @param cond last matched condition
     */
    public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < elements.length; i++) {
            buf.append(elements[i].evaluate(rule, cond, resolver));
        }
        return buf.toString();
    }
}
