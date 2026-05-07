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
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a rewrite rule for URL rewriting.
 */
public class RewriteRule {

    /**
     * Default constructor.
     */
    public RewriteRule() {
    }

    /**
     * The conditions associated with this rule.
     */
    protected RewriteCond[] conditions = new RewriteCond[0];

    /**
     * The compiled pattern for matching.
     */
    protected ThreadLocal<Pattern> pattern = new ThreadLocal<>();

    /**
     * The substitution to apply.
     */
    protected Substitution substitution = null;

    /**
     * The pattern string.
     */
    protected String patternString = null;

    /**
     * The substitution string.
     */
    protected String substitutionString = null;

    /**
     * The flags string.
     */
    protected String flagsString = null;

    /**
     * Whether the pattern is positive (true) or negated (false).
     */
    protected boolean positive = true;

    /**
     * Parse the rule using the provided rewrite maps.
     * @param maps the rewrite maps
     */
    public void parse(Map<String,RewriteMap> maps) {
        // Parse the substitution
        if (!"-".equals(substitutionString)) {
            substitution = new Substitution();
            substitution.setSub(substitutionString);
            substitution.parse(maps);
            substitution.setEscapeBackReferences(isEscapeBackReferences());
        }
        // Parse the pattern
        if (patternString.startsWith("!")) {
            positive = false;
            patternString = patternString.substring(1);
        }
        int flags = Pattern.DOTALL;
        if (isNocase()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        Pattern.compile(patternString, flags);
        // Parse conditions
        for (RewriteCond condition : conditions) {
            condition.parse(maps);
        }
        // Parse flag which have substitution values
        if (isEnv()) {
            for (String s : envValue) {
                Substitution newEnvSubstitution = new Substitution();
                newEnvSubstitution.setSub(s);
                newEnvSubstitution.parse(maps);
                envSubstitution.add(newEnvSubstitution);
                envResult.add(new ThreadLocal<>());
            }
        }
        if (isCookie()) {
            cookieSubstitution = new Substitution();
            cookieSubstitution.setSub(cookieValue);
            cookieSubstitution.parse(maps);
        }
    }

    /**
     * Add a condition to this rule.
     * @param condition the condition to add
     */
    public void addCondition(RewriteCond condition) {
        RewriteCond[] conditions = Arrays.copyOf(this.conditions, this.conditions.length + 1);
        conditions[this.conditions.length] = condition;
        this.conditions = conditions;
    }

    /**
     * Evaluate the rule based on the context
     *
     * @param url      The char sequence
     * @param resolver Property resolver
     *
     * @return <code>null</code> if no rewrite took place
     */
    public CharSequence evaluate(CharSequence url, Resolver resolver) {
        Pattern pattern = this.pattern.get();
        if (pattern == null) {
            // Parse the pattern
            int flags = Pattern.DOTALL;
            if (isNocase()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            pattern = Pattern.compile(patternString, flags);
            this.pattern.set(pattern);
        }
        Matcher matcher = pattern.matcher(url);
        // Use XOR
        if (positive ^ matcher.matches()) {
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
        return "RewriteRule " + patternString + " " + substitutionString +
                ((flagsString != null) ? (" " + flagsString) : "");
    }


    /**
     * Whether to escape back references.
     */
    private boolean escapeBackReferences = false;

    /**
     * This flag chains the current rule with the next rule (which itself can be chained with the following rule, etc.).
     * This has the following effect: if a rule matches, then processing continues as usual, i.e., the flag has no
     * effect. If the rule does not match, then all following chained rules are skipped. For instance, use it to remove
     * the ".www" part inside a per-directory rule set when you let an external redirect happen (where the ".www" part
     * should not to occur!).
     */
    protected boolean chain = false;

    /**
     * This sets a cookie on the client's browser. The cookie's name is specified by NAME and the value is VAL. The
     * domain field is the domain of the cookie, such as '.apache.org',the optional lifetime is the lifetime of the
     * cookie in minutes, and the optional path is the path of the cookie
     */
    protected boolean cookie = false;

    /**
     * The cookie name.
     */
    protected String cookieName = null;

    /**
     * The cookie value.
     */
    protected String cookieValue = null;

    /**
     * The cookie domain.
     */
    protected String cookieDomain = null;

    /**
     * The cookie lifetime.
     */
    protected int cookieLifetime = -1;

    /**
     * The cookie path.
     */
    protected String cookiePath = null;

    /**
     * Whether the cookie is secure.
     */
    protected boolean cookieSecure = false;

    /**
     * Whether the cookie is HTTP only.
     */
    protected boolean cookieHttpOnly = false;

    /**
     * The cookie substitution.
     */
    protected Substitution cookieSubstitution = null;

    /**
     * The cookie result.
     */
    protected ThreadLocal<String> cookieResult = new ThreadLocal<>();

    /**
     * This forces a request attribute named VAR to be set to the value VAL, where VAL can contain regexp back
     * references $N and %N which will be expanded. Multiple env flags are allowed.
     */
    protected boolean env = false;

    /**
     * The environment variable names.
     */
    protected ArrayList<String> envName = new ArrayList<>();

    /**
     * The environment variable values.
     */
    protected ArrayList<String> envValue = new ArrayList<>();

    /**
     * The environment substitutions.
     */
    protected ArrayList<Substitution> envSubstitution = new ArrayList<>();

    /**
     * The environment results.
     */
    protected ArrayList<ThreadLocal<String>> envResult = new ArrayList<>();

    /**
     * This forces the current URL to be forbidden, i.e., it immediately sends back an HTTP response of 403 (FORBIDDEN).
     * Use this flag in conjunction with appropriate RewriteConds to conditionally block some URLs.
     */
    protected boolean forbidden = false;

    /**
     * This forces the current URL to be gone, i.e., it immediately sends back an HTTP response of 410 (GONE). Use this
     * flag to mark pages which no longer exist as gone.
     */
    protected boolean gone = false;

    /**
     * Host. This means this rule and its associated conditions will apply to host, allowing host rewriting (ex:
     * redirecting internally *.foo.com to bar.foo.com).
     */
    protected boolean host = false;

    /**
     * Stop the rewriting process here and don't apply any additional rewriting rules. This corresponds to the Perl last
     * command or the break command from the C language. Use this flag to prevent the currently rewritten URL from being
     * rewritten further by following rules. For example, use it to rewrite the root-path URL ('/') to a real one, e.g.,
     * '/e/www/'.
     */
    protected boolean last = false;

    /**
     * Re-run the rewriting process (starting again with the first rewriting rule). Here the URL to match is again not
     * the original URL but the URL from the last rewriting rule. This corresponds to the Perl next command or the
     * continue command from the C language. Use this flag to restart the rewriting process, i.e., to immediately go to
     * the top of the loop. But be careful not to create an infinite loop!
     */
    protected boolean next = false;

    /**
     * This makes the Pattern case-insensitive, i.e., there is no difference between 'A-Z' and 'a-z' when Pattern is
     * matched against the current URL.
     */
    protected boolean nocase = false;

    /**
     * This flag keeps mod_rewrite from applying the usual URI escaping rules to the result of a rewrite. Ordinarily,
     * special characters (such as '%', '$', ';', and so on) will be escaped into their hexcode equivalents ('%25',
     * '%24', and '%3B', respectively); this flag prevents this from being done. This allows percent symbols to appear
     * in the output, as in {@code RewriteRule /foo/(.*) /bar?arg=P1\%3d$1 [R,NE]} which would turn '/foo/zed' into a
     * safe request for '/bar?arg=P1=zed'.
     */
    protected boolean noescape = false;

    /**
     * This flag forces the rewriting engine to skip a rewriting rule if the current request is an internal sub-request.
     * For instance, sub-requests occur internally in Apache when mod_include tries to find out information about
     * possible directory default files (index.xxx). On sub-requests it is not always useful and even sometimes causes a
     * failure to if the complete set of rules are applied. Use this flag to exclude some rules. Use the following rule
     * for your decision: whenever you prefix some URLs with CGI-scripts to force them to be processed by the
     * CGI-script, the chance is high that you will run into problems (or even overhead) on sub-requests. In these
     * cases, use this flag.
     */
    protected boolean nosubreq = false;

    /*
     * Note: No proxy
     */

    /*
     * Note: No passthrough
     */

    /**
     * This flag forces the rewriting engine to append a query string part in the substitution string to the existing
     * one instead of replacing it. Use this when you want to add more data to the query string via a rewrite rule.
     */
    protected boolean qsappend = false;

    /**
     * When the requested URI contains a query string, and the target URI does not, the default behavior of RewriteRule
     * is to copy that query string to the target URI. Using the [QSD] flag causes the query string to be discarded.
     * Using [QSD] and [QSA] together will result in [QSD] taking precedence.
     */
    protected boolean qsdiscard = false;

    /**
     * Prefix Substitution with http://thishost[:thisport]/ (which makes the new URL a URI) to force an external
     * redirection. If no code is given an HTTP response of 302 (FOUND, previously MOVED TEMPORARILY) is used. If you
     * want to use other response codes in the range 300-399 just specify them as a number or use one of the following
     * symbolic names: temp (default), permanent, seeother. Use it for rules which should canonicalize the URL and give
     * it back to the client, e.g., translate "/~" into "/u/" or always append a slash to /u/user, etc. Note: When you
     * use this flag, make sure that the substitution field is a valid URL! If not, you are redirecting to an invalid
     * location! And remember that this flag itself only prefixes the URL with http://thishost[:thisport]/, rewriting
     * continues. Usually you also want to stop and do the redirection immediately. To stop the rewriting you also have
     * to provide the 'L' flag.
     */
    protected boolean redirect = false;

    /**
     * The redirect HTTP status code.
     */
    protected int redirectCode = 0;

    /**
     * This flag forces the rewriting engine to skip the next num rules in sequence when the current rule matches. Use
     * this to make pseudo if-then-else constructs: The last rule of the then-clause becomes skip=N where N is the
     * number of rules in the else-clause. (This is not the same as the 'chain|C' flag!)
     */
    protected int skip = 0;

    /**
     * Force the MIME-type of the target file to be MIME-type. For instance, this can be used to set up the content-type
     * based on some conditions. For example, the following snippet allows .php files to be displayed by mod_php if they
     * are called with the .phps extension: RewriteRule ^(.+\.php)s$ $1 [T=application/x-httpd-php-source]
     */
    protected boolean type = false;

    /**
     * The MIME type value.
     */
    protected String typeValue = null;

    /**
     * Allows skipping the next valve in the Catalina pipeline.
     */
    protected boolean valveSkip = false;

   /**
     * Get whether back references are escaped.
     * @return true if back references are escaped
     */
    public boolean isEscapeBackReferences() {
        return escapeBackReferences;
    }

    /**
     * Set whether back references are escaped.
     * @param escapeBackReferences true to escape back references
     */
    public void setEscapeBackReferences(boolean escapeBackReferences) {
        this.escapeBackReferences = escapeBackReferences;
    }

    /**
     * Get whether this rule is chained.
     * @return true if chained
     */
    public boolean isChain() {
        return chain;
    }

    /**
     * Set whether this rule is chained.
     * @param chain true to chain with the next rule
     */
    public void setChain(boolean chain) {
        this.chain = chain;
    }

    /**
     * Get the conditions for this rule.
     * @return the conditions array
     */
    public RewriteCond[] getConditions() {
        return conditions;
    }

    /**
     * Set the conditions for this rule.
     * @param conditions the conditions array
     */
    public void setConditions(RewriteCond[] conditions) {
        this.conditions = conditions;
    }

    /**
     * Get whether the cookie flag is set.
     * @return true if cookie flag is set
     */
    public boolean isCookie() {
        return cookie;
    }

    /**
     * Set the cookie flag.
     * @param cookie true to set the cookie flag
     */
    public void setCookie(boolean cookie) {
        this.cookie = cookie;
    }

    /**
     * Get the cookie name.
     * @return the cookie name
     */
    public String getCookieName() {
        return cookieName;
    }

    /**
     * Set the cookie name.
     * @param cookieName the cookie name
     */
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    /**
     * Get the cookie value.
     * @return the cookie value
     */
    public String getCookieValue() {
        return cookieValue;
    }

    /**
     * Set the cookie value.
     * @param cookieValue the cookie value
     */
    public void setCookieValue(String cookieValue) {
        this.cookieValue = cookieValue;
    }

    /**
     * Get the cookie result.
     * @return the cookie result
     */
    public String getCookieResult() {
        return cookieResult.get();
    }

    /**
     * Get whether the env flag is set.
     * @return true if env flag is set
     */
    public boolean isEnv() {
        return env;
    }

    /**
     * Get the number of environment variables.
     * @return the number of environment variables
     */
    public int getEnvSize() {
        return envName.size();
    }

    /**
     * Set the env flag.
     * @param env true to set the env flag
     */
    public void setEnv(boolean env) {
        this.env = env;
    }

    /**
     * Get the name of an environment variable.
     * @param i the index
     * @return the environment variable name
     */
    public String getEnvName(int i) {
        return envName.get(i);
    }

    /**
     * Add an environment variable name.
     * @param envName the name to add
     */
    public void addEnvName(String envName) {
        this.envName.add(envName);
    }

    /**
     * Get the value of an environment variable.
     * @param i the index
     * @return the environment variable value
     */
    public String getEnvValue(int i) {
        return envValue.get(i);
    }

    /**
     * Add an environment variable value.
     * @param envValue the value to add
     */
    public void addEnvValue(String envValue) {
        this.envValue.add(envValue);
    }

    /**
     * Get the result of an environment variable substitution.
     * @param i the index
     * @return the environment variable result
     */
    public String getEnvResult(int i) {
        return envResult.get(i).get();
    }

    /**
     * Get whether the forbidden flag is set.
     * @return true if forbidden flag is set
     */
    public boolean isForbidden() {
        return forbidden;
    }

    /**
     * Set the forbidden flag.
     * @param forbidden true to set the forbidden flag
     */
    public void setForbidden(boolean forbidden) {
        this.forbidden = forbidden;
    }

    /**
     * Get whether the gone flag is set.
     * @return true if gone flag is set
     */
    public boolean isGone() {
        return gone;
    }

    /**
     * Set the gone flag.
     * @param gone true to set the gone flag
     */
    public void setGone(boolean gone) {
        this.gone = gone;
    }

    /**
     * Get whether the last flag is set.
     * @return true if last flag is set
     */
    public boolean isLast() {
        return last;
    }

    /**
     * Set the last flag.
     * @param last true to set the last flag
     */
    public void setLast(boolean last) {
        this.last = last;
    }

    /**
     * Get whether the next flag is set.
     * @return true if next flag is set
     */
    public boolean isNext() {
        return next;
    }

    /**
     * Set the next flag.
     * @param next true to set the next flag
     */
    public void setNext(boolean next) {
        this.next = next;
    }

    /**
     * Get whether the nocase flag is set.
     * @return true if nocase flag is set
     */
    public boolean isNocase() {
        return nocase;
    }

    /**
     * Set the nocase flag.
     * @param nocase true to set the nocase flag
     */
    public void setNocase(boolean nocase) {
        this.nocase = nocase;
    }

    /**
     * Get whether the noescape flag is set.
     * @return true if noescape flag is set
     */
    public boolean isNoescape() {
        return noescape;
    }

    /**
     * Set the noescape flag.
     * @param noescape true to set the noescape flag
     */
    public void setNoescape(boolean noescape) {
        this.noescape = noescape;
    }

    /**
     * Get whether the nosubreq flag is set.
     * @return true if nosubreq flag is set
     */
    public boolean isNosubreq() {
        return nosubreq;
    }

    /**
     * Set the nosubreq flag.
     * @param nosubreq true to set the nosubreq flag
     */
    public void setNosubreq(boolean nosubreq) {
        this.nosubreq = nosubreq;
    }

    /**
     * Get whether the qsappend flag is set.
     * @return true if qsappend flag is set
     */
    public boolean isQsappend() {
        return qsappend;
    }

    /**
     * Set the qsappend flag.
     * @param qsappend true to set the qsappend flag
     */
    public void setQsappend(boolean qsappend) {
        this.qsappend = qsappend;
    }

    /**
     * Get whether the qsdiscard flag is set.
     * @return true if qsdiscard flag is set
     */
    public final boolean isQsdiscard() {
        return qsdiscard;
    }

    /**
     * Set the qsdiscard flag.
     * @param qsdiscard true to set the qsdiscard flag
     */
    public final void setQsdiscard(boolean qsdiscard) {
        this.qsdiscard = qsdiscard;
    }

    /**
     * Get whether the redirect flag is set.
     * @return true if redirect flag is set
     */
    public boolean isRedirect() {
        return redirect;
    }

    /**
     * Set the redirect flag.
     * @param redirect true to set the redirect flag
     */
    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }

    /**
     * Get the redirect HTTP status code.
     * @return the redirect code
     */
    public int getRedirectCode() {
        return redirectCode;
    }

    /**
     * Set the redirect HTTP status code.
     * @param redirectCode the redirect code
     */
    public void setRedirectCode(int redirectCode) {
        this.redirectCode = redirectCode;
    }

    /**
     * Get the number of rules to skip.
     * @return the skip count
     */
    public int getSkip() {
        return skip;
    }

    /**
     * Set the number of rules to skip.
     * @param skip the number of rules to skip
     */
    public void setSkip(int skip) {
        this.skip = skip;
    }

    /**
     * Get the substitution.
     * @return the substitution
     */
    public Substitution getSubstitution() {
        return substitution;
    }

    /**
     * Set the substitution.
     * @param substitution the substitution
     */
    public void setSubstitution(Substitution substitution) {
        this.substitution = substitution;
    }

    /**
     * Get whether the type flag is set.
     * @return true if type flag is set
     */
    public boolean isType() {
        return type;
    }

    /**
     * Set the type flag.
     * @param type true to set the type flag
     */
    public void setType(boolean type) {
        this.type = type;
    }

    /**
     * Get the MIME type value.
     * @return the MIME type value
     */
    public String getTypeValue() {
        return typeValue;
    }

    /**
     * Set the MIME type value.
     * @param typeValue the MIME type value
     */
    public void setTypeValue(String typeValue) {
        this.typeValue = typeValue;
    }

    /**
     * Get the pattern string.
     * @return the pattern string
     */
    public String getPatternString() {
        return patternString;
    }

    /**
     * Set the pattern string.
     * @param patternString the pattern string
     */
    public void setPatternString(String patternString) {
        this.patternString = patternString;
    }

    /**
     * Get the substitution string.
     * @return the substitution string
     */
    public String getSubstitutionString() {
        return substitutionString;
    }

    /**
     * Set the substitution string.
     * @param substitutionString the substitution string
     */
    public void setSubstitutionString(String substitutionString) {
        this.substitutionString = substitutionString;
    }

    /**
     * Get the flags string.
     * @return the flags string
     */
    public final String getFlagsString() {
        return flagsString;
    }

    /**
     * Set the flags string.
     * @param flagsString the flags string
     */
    public final void setFlagsString(String flagsString) {
        this.flagsString = flagsString;
    }

    /**
     * Get whether the host flag is set.
     * @return true if host flag is set
     */
    public boolean isHost() {
        return host;
    }

    /**
     * Set the host flag.
     * @param host true to set the host flag
     */
    public void setHost(boolean host) {
        this.host = host;
    }

    /**
     * Get the cookie domain.
     * @return the cookie domain
     */
    public String getCookieDomain() {
        return cookieDomain;
    }

    /**
     * Set the cookie domain.
     * @param cookieDomain the cookie domain
     */
    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    /**
     * Get the cookie lifetime.
     * @return the cookie lifetime in minutes
     */
    public int getCookieLifetime() {
        return cookieLifetime;
    }

    /**
     * Set the cookie lifetime.
     * @param cookieLifetime the cookie lifetime in minutes
     */
    public void setCookieLifetime(int cookieLifetime) {
        this.cookieLifetime = cookieLifetime;
    }

    /**
     * Get the cookie path.
     * @return the cookie path
     */
    public String getCookiePath() {
        return cookiePath;
    }

    /**
     * Set the cookie path.
     * @param cookiePath the cookie path
     */
    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    /**
     * Get whether the cookie secure flag is set.
     * @return true if cookie secure flag is set
     */
    public boolean isCookieSecure() {
        return cookieSecure;
    }

    /**
     * Set the cookie secure flag.
     * @param cookieSecure true to set the cookie secure flag
     */
    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * Get whether the cookie HTTP only flag is set.
     * @return true if cookie HTTP only flag is set
     */
    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    /**
     * Set the cookie HTTP only flag.
     * @param cookieHttpOnly true to set the cookie HTTP only flag
     */
    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    /**
     * Get whether the valve skip flag is set.
     * @return true if valve skip flag is set
     */
    public boolean isValveSkip() {
        return this.valveSkip;
    }

    /**
     * Set the valve skip flag.
     * @param valveSkip true to set the valve skip flag
     */
    public void setValveSkip(boolean valveSkip) {
        this.valveSkip = valveSkip;
    }

}
