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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.apache.catalina.util.URLEncoder;

public class Substitution {

    public abstract class SubstitutionElement {
        public abstract String evaluate(Matcher rule, Matcher cond, Resolver resolver);
    }

    public class StaticElement extends SubstitutionElement {
        public String value;

        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return value;
        }

    }

    public class RewriteRuleBackReferenceElement extends SubstitutionElement {
        public int n;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            String result = rule.group(n);
            if (result == null) {
                result = "";
            }
            if (escapeBackReferences) {
                // Note: This should be consistent with the way httpd behaves.
                //       We might want to consider providing a dedicated decoder
                //       with an option to add additional safe characters to
                //       provide users with more flexibility
                return URLEncoder.DEFAULT.encode(result, resolver.getUriCharset());
            } else {
                return result;
            }
        }
    }

    public class RewriteCondBackReferenceElement extends SubstitutionElement {
        public int n;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return (cond.group(n) == null ? "" : cond.group(n));
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
        public SubstitutionElement[] defaultValue = null;
        public SubstitutionElement[] key = null;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            String result = map.lookup(evaluateSubstitution(key, rule, cond, resolver));
            if (result == null && defaultValue != null) {
                result = evaluateSubstitution(defaultValue, rule, cond, resolver);
            }
            return result;
        }
    }

    protected SubstitutionElement[] elements = null;

    protected String sub = null;
    public String getSub() { return sub; }
    public void setSub(String sub) { this.sub = sub; }

    private boolean escapeBackReferences;
    void setEscapeBackReferences(boolean escapeBackReferences) {
        this.escapeBackReferences = escapeBackReferences;
    }

    public void parse(Map<String, RewriteMap> maps) {
        this.elements = parseSubtitution(sub, maps);
    }

    private SubstitutionElement[] parseSubtitution(String sub, Map<String, RewriteMap> maps) {

        List<SubstitutionElement> elements = new ArrayList<>();
        int pos = 0;
        int percentPos = 0;
        int dollarPos = 0;
        int backslashPos = 0;

        while (pos < sub.length()) {
            percentPos = sub.indexOf('%', pos);
            dollarPos = sub.indexOf('$', pos);
            backslashPos = sub.indexOf('\\', pos);
            if (percentPos == -1 && dollarPos == -1 && backslashPos == -1) {
                // Static text
                StaticElement newElement = new StaticElement();
                newElement.value = sub.substring(pos, sub.length());
                pos = sub.length();
                elements.add(newElement);
            } else if (isFirstPos(backslashPos, dollarPos, percentPos)) {
                if (backslashPos + 1 == sub.length()) {
                    throw new IllegalArgumentException(sub);
                }
                StaticElement newElement = new StaticElement();
                newElement.value = sub.substring(pos, backslashPos) + sub.substring(backslashPos + 1, backslashPos + 2);
                pos = backslashPos + 2;
                elements.add(newElement);
            } else if (isFirstPos(dollarPos, percentPos)) {
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
                } else if (sub.charAt(dollarPos + 1) == '{') {
                    // $: map lookup as ${mapname:key|default}
                    MapElement newElement = new MapElement();
                    int open = sub.indexOf('{', dollarPos);
                    int colon = findMatchingColonOrBar(true, sub, open);
                    int def = findMatchingColonOrBar(false, sub, open);
                    int close = findMatchingBrace(sub, open);
                    if (!(-1 < open && open < colon && colon < close)) {
                        throw new IllegalArgumentException(sub);
                    }
                    newElement.map = maps.get(sub.substring(open + 1, colon));
                    if (newElement.map == null) {
                        throw new IllegalArgumentException(sub + ": No map: " + sub.substring(open + 1, colon));
                    }
                    String key = null;
                    String defaultValue = null;
                    if (def > -1) {
                        if (!(colon < def && def < close)) {
                            throw new IllegalArgumentException(sub);
                        }
                        key = sub.substring(colon + 1, def);
                        defaultValue = sub.substring(def + 1, close);
                    } else {
                        key = sub.substring(colon + 1, close);
                    }
                    newElement.key = parseSubtitution(key, maps);
                    if (defaultValue != null) {
                        newElement.defaultValue = parseSubtitution(defaultValue, maps);
                    }
                    pos = close + 1;
                    elements.add(newElement);
                } else {
                    throw new IllegalArgumentException(sub + ": missing digit or curly brace.");
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
                } else if (sub.charAt(percentPos + 1) == '{') {
                    // %: server variable as %{variable}
                    SubstitutionElement newElement = null;
                    int open = sub.indexOf('{', percentPos);
                    int colon = findMatchingColonOrBar(true, sub, open);
                    int close = findMatchingBrace(sub, open);
                    if (!(-1 < open && open < close)) {
                        throw new IllegalArgumentException(sub);
                    }
                    if (colon > -1 && open < colon && colon < close) {
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
                } else {
                    throw new IllegalArgumentException(sub + ": missing digit or curly brace.");
                }
            }
        }

        return elements.toArray(new SubstitutionElement[0]);

    }

    private static int findMatchingBrace(String sub, int start) {
        int nesting = 1;
        for (int i = start + 1; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (c == '{') {
                char previousChar = sub.charAt(i-1);
                if (previousChar == '$' || previousChar == '%') {
                    nesting++;
                }
            } else if (c == '}') {
                nesting--;
                if (nesting == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingColonOrBar(boolean colon, String sub, int start) {
        int nesting = 0;
        for (int i = start + 1; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (c == '{') {
                char previousChar = sub.charAt(i-1);
                if (previousChar == '$' || previousChar == '%') {
                    nesting++;
                }
            } else if (c == '}') {
                nesting--;
            } else if (colon ? c == ':' : c =='|') {
                if (nesting == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Evaluate the substitution based on the context.
     * @param rule corresponding matched rule
     * @param cond last matched condition
     * @param resolver The property resolver
     * @return The substitution result
     */
    public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
        return evaluateSubstitution(elements, rule, cond, resolver);
    }

    private String evaluateSubstitution(SubstitutionElement[] elements, Matcher rule, Matcher cond, Resolver resolver) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < elements.length; i++) {
            buf.append(elements[i].evaluate(rule, cond, resolver));
        }
        return buf.toString();
    }

    /**
     * Checks whether the first int is non negative and smaller than any non negative other int
     * given with {@code others}.
     *
     * @param testPos
     *            integer to test against
     * @param others
     *            list of integers that are paired against {@code testPos}. Any
     *            negative integer will be ignored.
     * @return {@code true} if {@code testPos} is not negative and is less then any given other
     *         integer, {@code false} otherwise
     */
    private boolean isFirstPos(int testPos, int... others) {
        if (testPos < 0) {
            return false;
        }
        for (int other : others) {
            if (other >= 0 && other < testPos) {
                return false;
            }
        }
        return true;
    }
}
