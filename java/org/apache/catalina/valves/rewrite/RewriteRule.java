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
import java.util.regex.Pattern;

public class RewriteRule {

    protected RewriteCond[] conditions = new RewriteCond[0];

    protected ThreadLocal<Pattern> pattern = new ThreadLocal<>();
    protected Substitution substitution = null;

    protected String patternString = null;
    protected String substitutionString = null;

    public void parse(Map<String, RewriteMap> maps) {
        // Parse the substitution
        if (!"-".equals(substitutionString)) {
            substitution = new Substitution();
            substitution.setSub(substitutionString);
            substitution.parse(maps);
            substitution.setEscapeBackReferences(isEscapeBackReferences());
        }
        // Parse the pattern
        int flags = 0;
        if (isNocase()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        Pattern.compile(patternString, flags);
        // Parse conditions
        for (int i = 0; i < conditions.length; i++) {
            conditions[i].parse(maps);
        }
        // Parse flag which have substitution values
        if (isEnv()) {
            for (int i = 0; i < envValue.size(); i++) {
                Substitution newEnvSubstitution = new Substitution();
                newEnvSubstitution.setSub(envValue.get(i));
                newEnvSubstitution.parse(maps);
                envSubstitution.add(newEnvSubstitution);
                envResult.add(new ThreadLocal<String>());
            }
        }
        if (isCookie()) {
            cookieSubstitution = new Substitution();
            cookieSubstitution.setSub(cookieValue);
            cookieSubstitution.parse(maps);
        }
    }

    public void addCondition(RewriteCond condition) {
        RewriteCond[] conditions = new RewriteCond[this.conditions.length + 1];
        for (int i = 0; i < this.conditions.length; i++) {
            conditions[i] = this.conditions[i];
        }
        conditions[this.conditions.length] = condition;
        this.conditions = conditions;
    }

    /**
     * Evaluate the rule based on the context
     * @param url The char sequence
     * @param resolver Property resolver
     * @return <code>null</code> if no rewrite took place
     */
    public CharSequence evaluate(CharSequence url, Resolver resolver) {
        Pattern pattern = this.pattern.get();
        if (pattern == null) {
            // Parse the pattern
            int flags = 0;
            if (isNocase()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            pattern = Pattern.compile(patternString, flags);
            this.pattern.set(pattern);
        }
        Matcher matcher = pattern.matcher(url);
        if (!matcher.matches()) {
            // Evaluation done
            return null;
        }
        // Evaluate conditions
        boolean done = false;
        boolean rewrite = true;
        Matcher lastMatcher = null;
        int pos = 0;
        while (!done) {
            if (pos < conditions.length) {
                rewrite = conditions[pos].evaluate(matcher, lastMatcher, resolver);
                if (rewrite) {
                    Matcher lastMatcher2 = conditions[pos].getMatcher();
                    if (lastMatcher2 != null) {
                        lastMatcher = lastMatcher2;
                    }
                    while (pos < conditions.length && conditions[pos].isOrnext()) {
                        pos++;
                    }
                } else if (!conditions[pos].isOrnext()) {
                    done = true;
                }
                pos++;
            } else {
                done = true;
            }
        }
        // Use the substitution to rewrite the url
        if (rewrite) {
            if (isEnv()) {
                for (int i = 0; i < envSubstitution.size(); i++) {
                    envResult.get(i).set(envSubstitution.get(i).evaluate(matcher, lastMatcher, resolver));
                }
            }
            if (isCookie()) {
                cookieResult.set(cookieSubstitution.evaluate(matcher, lastMatcher, resolver));
            }
            if (substitution != null) {
                return substitution.evaluate(matcher, lastMatcher, resolver);
            } else {
                return url;
            }
        } else {
            return null;
        }
    }


    /**
     * String representation.
     */
    @Override
    public String toString() {
        // FIXME: Add flags if possible
        return "RewriteRule " + patternString + " " + substitutionString;
    }


    private boolean escapeBackReferences = false;

    /**
     *  This flag chains the current rule with the next rule (which itself
     *  can be chained with the following rule, etc.). This has the following
     *  effect: if a rule matches, then processing continues as usual, i.e.,
     *  the flag has no effect. If the rule does not match, then all following
     *  chained rules are skipped. For instance, use it to remove the ``.www''
     *  part inside a per-directory rule set when you let an external redirect
     *  happen (where the ``.www'' part should not to occur!).
     */
    protected boolean chain = false;

    /**
     *  This sets a cookie on the client's browser. The cookie's name is
     *  specified by NAME and the value is VAL. The domain field is the domain
     *  of the cookie, such as '.apache.org',the optional lifetime
     *  is the lifetime of the cookie in minutes, and the optional path is the
     *  path of the cookie
     */
    protected boolean cookie = false;
    protected String cookieName = null;
    protected String cookieValue = null;
    protected String cookieDomain = null;
    protected int cookieLifetime = -1;
    protected String cookiePath = null;
    protected boolean cookieSecure = false;
    protected boolean cookieHttpOnly = false;
    protected Substitution cookieSubstitution = null;
    protected ThreadLocal<String> cookieResult = new ThreadLocal<>();

    /**
     *  This forces a request attribute named VAR to be set to the value VAL,
     *  where VAL can contain regexp back references $N and %N which will be
     *  expanded. Multiple env flags are allowed.
     */
    protected boolean env = false;
    protected ArrayList<String> envName = new ArrayList<>();
    protected ArrayList<String> envValue = new ArrayList<>();
    protected ArrayList<Substitution> envSubstitution = new ArrayList<>();
    protected ArrayList<ThreadLocal<String>> envResult = new ArrayList<>();

    /**
     *  This forces the current URL to be forbidden, i.e., it immediately sends
     *  back a HTTP response of 403 (FORBIDDEN). Use this flag in conjunction
     *  with appropriate RewriteConds to conditionally block some URLs.
     */
    protected boolean forbidden = false;

    /**
     *  This forces the current URL to be gone, i.e., it immediately sends
     *  back a HTTP response of 410 (GONE). Use this flag to mark pages which
     *  no longer exist as gone.
     */
    protected boolean gone = false;

    /**
     * Host. This means this rule and its associated conditions will apply to
     * host, allowing host rewriting (ex: redirecting internally *.foo.com to
     * bar.foo.com).
     */
    protected boolean host = false;

    /**
     *  Stop the rewriting process here and don't apply any more rewriting
     *  rules. This corresponds to the Perl last command or the break command
     *  from the C language. Use this flag to prevent the currently rewritten
     *  URL from being rewritten further by following rules. For example, use
     *  it to rewrite the root-path URL ('/') to a real one, e.g., '/e/www/'.
     */
    protected boolean last = false;

    /**
     *  Re-run the rewriting process (starting again with the first rewriting
     *  rule). Here the URL to match is again not the original URL but the URL
     *  from the last rewriting rule. This corresponds to the Perl next
     *  command or the continue command from the C language. Use this flag to
     *  restart the rewriting process, i.e., to immediately go to the top of
     *  the loop. But be careful not to create an infinite loop!
     */
    protected boolean next = false;

    /**
     *  This makes the Pattern case-insensitive, i.e., there is no difference
     *  between 'A-Z' and 'a-z' when Pattern is matched against the current
     *  URL.
     */
    protected boolean nocase = false;

    /**
     *  This flag keeps mod_rewrite from applying the usual URI escaping rules
     *  to the result of a rewrite. Ordinarily, special characters (such as
     *  '%', '$', ';', and so on) will be escaped into their hexcode
     *  equivalents ('%25', '%24', and '%3B', respectively); this flag
     *  prevents this from being done. This allows percent symbols to appear
     *  in the output, as in
     *    RewriteRule /foo/(.*) /bar?arg=P1\%3d$1 [R,NE]
     *    which would turn '/foo/zed' into a safe request for '/bar?arg=P1=zed'.
     */
    protected boolean noescape = false;

    /**
     *  This flag forces the rewriting engine to skip a rewriting rule if the
     *  current request is an internal sub-request. For instance, sub-requests
     *  occur internally in Apache when mod_include tries to find out
     *  information about possible directory default files (index.xxx). On
     *  sub-requests it is not always useful and even sometimes causes a
     *  failure to if the complete set of rules are applied. Use this flag to
     *  exclude some rules. Use the following rule for your decision: whenever
     *  you prefix some URLs with CGI-scripts to force them to be processed by
     *  the CGI-script, the chance is high that you will run into problems (or
     *  even overhead) on sub-requests. In these cases, use this flag.
     */
    protected boolean nosubreq = false;

    /**
     *  This flag forces the substitution part to be internally forced as a proxy
     *  request and immediately (i.e., rewriting rule processing stops here) put
     *  through the proxy module. You have to make sure that the substitution string
     *  is a valid URI (e.g., typically starting with http://hostname) which can be
     *  handled by the Apache proxy module. If not you get an error from the proxy
     *  module. Use this flag to achieve a more powerful implementation of the
     *  ProxyPass directive, to map some remote stuff into the namespace of
     *  the local server.
     *  Note: No proxy
     */

    /**
     * Note: No passthrough
     */

    /**
     *  This flag forces the rewriting engine to append a query string part in
     *  the substitution string to the existing one instead of replacing it.
     *  Use this when you want to add more data to the query string via
     *  a rewrite rule.
     */
    protected boolean qsappend = false;

    /**
     *  Prefix Substitution with http://thishost[:thisport]/ (which makes the
     *  new URL a URI) to force a external redirection. If no code is given
     *  a HTTP response of 302 (MOVED TEMPORARILY) is used. If you want to
     *  use other response codes in the range 300-400 just specify them as
     *  a number or use one of the following symbolic names: temp (default),
     *  permanent, seeother. Use it for rules which should canonicalize the
     *  URL and give it back to the client, e.g., translate ``/~'' into ``/u/''
     *  or always append a slash to /u/user, etc. Note: When you use this flag,
     *  make sure that the substitution field is a valid URL! If not, you are
     *  redirecting to an invalid location! And remember that this flag itself
     *  only prefixes the URL with http://thishost[:thisport]/, rewriting
     *  continues. Usually you also want to stop and do the redirection
     *  immediately. To stop the rewriting you also have to provide the
     *  'L' flag.
     */
    protected boolean redirect = false;
    protected int redirectCode = 0;

    /**
     *  This flag forces the rewriting engine to skip the next num rules in
     *  sequence when the current rule matches. Use this to make pseudo
     *  if-then-else constructs: The last rule of the then-clause becomes
     *  skip=N where N is the number of rules in the else-clause.
     *  (This is not the same as the 'chain|C' flag!)
     */
    protected int skip = 0;

    /**
     *  Force the MIME-type of the target file to be MIME-type. For instance,
     *  this can be used to setup the content-type based on some conditions.
     *  For example, the following snippet allows .php files to be displayed
     *  by mod_php if they are called with the .phps extension:
     *  RewriteRule ^(.+\.php)s$ $1 [T=application/x-httpd-php-source]
     */
    protected boolean type = false;
    protected String typeValue = null;

    public boolean isEscapeBackReferences() {
        return escapeBackReferences;
    }
    public void setEscapeBackReferences(boolean escapeBackReferences) {
        this.escapeBackReferences = escapeBackReferences;
    }
    public boolean isChain() {
        return chain;
    }
    public void setChain(boolean chain) {
        this.chain = chain;
    }
    public RewriteCond[] getConditions() {
        return conditions;
    }
    public void setConditions(RewriteCond[] conditions) {
        this.conditions = conditions;
    }
    public boolean isCookie() {
        return cookie;
    }
    public void setCookie(boolean cookie) {
        this.cookie = cookie;
    }
    public String getCookieName() {
        return cookieName;
    }
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }
    public String getCookieValue() {
        return cookieValue;
    }
    public void setCookieValue(String cookieValue) {
        this.cookieValue = cookieValue;
    }
    public String getCookieResult() {
        return cookieResult.get();
    }
    public boolean isEnv() {
        return env;
    }
    public int getEnvSize() {
        return envName.size();
    }
    public void setEnv(boolean env) {
        this.env = env;
    }
    public String getEnvName(int i) {
        return envName.get(i);
    }
    public void addEnvName(String envName) {
        this.envName.add(envName);
    }
    public String getEnvValue(int i) {
        return envValue.get(i);
    }
    public void addEnvValue(String envValue) {
        this.envValue.add(envValue);
    }
    public String getEnvResult(int i) {
        return envResult.get(i).get();
    }
    public boolean isForbidden() {
        return forbidden;
    }
    public void setForbidden(boolean forbidden) {
        this.forbidden = forbidden;
    }
    public boolean isGone() {
        return gone;
    }
    public void setGone(boolean gone) {
        this.gone = gone;
    }
    public boolean isLast() {
        return last;
    }
    public void setLast(boolean last) {
        this.last = last;
    }
    public boolean isNext() {
        return next;
    }
    public void setNext(boolean next) {
        this.next = next;
    }
    public boolean isNocase() {
        return nocase;
    }
    public void setNocase(boolean nocase) {
        this.nocase = nocase;
    }
    public boolean isNoescape() {
        return noescape;
    }
    public void setNoescape(boolean noescape) {
        this.noescape = noescape;
    }
    public boolean isNosubreq() {
        return nosubreq;
    }
    public void setNosubreq(boolean nosubreq) {
        this.nosubreq = nosubreq;
    }
    public boolean isQsappend() {
        return qsappend;
    }
    public void setQsappend(boolean qsappend) {
        this.qsappend = qsappend;
    }
    public boolean isRedirect() {
        return redirect;
    }
    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }
    public int getRedirectCode() {
        return redirectCode;
    }
    public void setRedirectCode(int redirectCode) {
        this.redirectCode = redirectCode;
    }
    public int getSkip() {
        return skip;
    }
    public void setSkip(int skip) {
        this.skip = skip;
    }
    public Substitution getSubstitution() {
        return substitution;
    }
    public void setSubstitution(Substitution substitution) {
        this.substitution = substitution;
    }
    public boolean isType() {
        return type;
    }
    public void setType(boolean type) {
        this.type = type;
    }
    public String getTypeValue() {
        return typeValue;
    }
    public void setTypeValue(String typeValue) {
        this.typeValue = typeValue;
    }

    public String getPatternString() {
        return patternString;
    }

    public void setPatternString(String patternString) {
        this.patternString = patternString;
    }

    public String getSubstitutionString() {
        return substitutionString;
    }

    public void setSubstitutionString(String substitutionString) {
        this.substitutionString = substitutionString;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    public int getCookieLifetime() {
        return cookieLifetime;
    }

    public void setCookieLifetime(int cookieLifetime) {
        this.cookieLifetime = cookieLifetime;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }
}
