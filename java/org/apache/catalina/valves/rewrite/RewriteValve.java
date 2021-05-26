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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Pipeline;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.URLEncoder;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.http.RequestUtil;

/**
 * Note: Extra caution should be used when adding a Rewrite Rule. When
 * specifying a regex to match for in a Rewrite Rule, certain regex could allow
 * an attacker to DoS your server, as Java's regex parsing is vulnerable to
 * "catastrophic backtracking" (also known as "Regular expression Denial of
 * Service", or ReDoS). There are some open source tools to help detect
 * vulnerable regex, though in general it is a hard problem. A good defence is
 * to use a regex debugger on your desired regex, and read more on the subject
 * of catastrophic backtracking.
 *
 * @see <a href=
 *      "https://www.owasp.org/index.php/Regular_expression_Denial_of_Service_-_ReDoS">OWASP
 *      ReDoS</a>
 */
public class RewriteValve extends ValveBase {

    /**
     * The rewrite rules that the valve will use.
     */
    protected RewriteRule[] rules = null;


    /**
     * If rewriting occurs, the whole request will be processed again.
     */
    protected ThreadLocal<Boolean> invoked = new ThreadLocal<>();


    /**
     * Relative path to the configuration file.
     * Note: If the valve's container is a context, this will be relative to
     * /WEB-INF/.
     */
    protected String resourcePath = "rewrite.config";


    /**
     * Will be set to true if the valve is associated with a context.
     */
    protected boolean context = false;


    /**
     * enabled this component
     */
    protected boolean enabled = true;

    /**
     * Maps to be used by the rules.
     */
    protected Map<String, RewriteMap> maps = new Hashtable<>();


    /**
     * Maps configuration.
     */
    protected ArrayList<String> mapsConfiguration = new ArrayList<>();


    public RewriteValve() {
        super(true);
    }


    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        containerLog = LogFactory.getLog(getContainer().getLogName() + ".rewrite");
    }


    @Override
    protected synchronized void startInternal() throws LifecycleException {

        super.startInternal();

        InputStream is = null;

        // Process configuration file for this valve
        if (getContainer() instanceof Context) {
            context = true;
            is = ((Context) getContainer()).getServletContext()
                .getResourceAsStream("/WEB-INF/" + resourcePath);
            if (containerLog.isDebugEnabled()) {
                if (is == null) {
                    containerLog.debug("No configuration resource found: /WEB-INF/" + resourcePath);
                } else {
                    containerLog.debug("Read configuration from: /WEB-INF/" + resourcePath);
                }
            }
        } else if (getContainer() instanceof Host) {
            String resourceName = getHostConfigPath(resourcePath);
            File file = new File(getConfigBase(), resourceName);
            try {
                if (!file.exists()) {
                    // Use getResource and getResourceAsStream
                    is = getClass().getClassLoader()
                        .getResourceAsStream(resourceName);
                    if (is != null && containerLog.isDebugEnabled()) {
                        containerLog.debug("Read configuration from CL at " + resourceName);
                    }
                } else {
                    if (containerLog.isDebugEnabled()) {
                        containerLog.debug("Read configuration from " + file.getAbsolutePath());
                    }
                    is = new FileInputStream(file);
                }
                if ((is == null) && (containerLog.isDebugEnabled())) {
                    containerLog.debug("No configuration resource found: " + resourceName +
                            " in " + getConfigBase() + " or in the classloader");
                }
            } catch (Exception e) {
                containerLog.error("Error opening configuration", e);
            }
        }

        if (is == null) {
            // Will use management operations to configure the valve dynamically
            return;
        }

        try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr)) {
            parse(reader);
        } catch (IOException ioe) {
            containerLog.error(sm.getString("rewriteValve.closeError"), ioe);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                containerLog.error(sm.getString("rewriteValve.closeError"), e);
            }
        }

    }

    public void setConfiguration(String configuration)
        throws Exception {
        if (containerLog == null) {
            containerLog = LogFactory.getLog(getContainer().getLogName() + ".rewrite");
        }
        maps.clear();
        parse(new BufferedReader(new StringReader(configuration)));
    }

    public String getConfiguration() {
        StringBuilder buffer = new StringBuilder();
        for (String mapConfiguration : mapsConfiguration) {
            buffer.append(mapConfiguration).append("\r\n");
        }
        if (mapsConfiguration.size() > 0) {
            buffer.append("\r\n");
        }
        for (RewriteRule rule : rules) {
            for (int j = 0; j < rule.getConditions().length; j++) {
                buffer.append(rule.getConditions()[j].toString()).append("\r\n");
            }
            buffer.append(rule.toString()).append("\r\n").append("\r\n");
        }
        return buffer.toString();
    }

    protected void parse(BufferedReader reader) throws LifecycleException {
        List<RewriteRule> rules = new ArrayList<>();
        List<RewriteCond> conditions = new ArrayList<>();
        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                Object result = parse(line);
                if (result instanceof RewriteRule) {
                    RewriteRule rule = (RewriteRule) result;
                    if (containerLog.isDebugEnabled()) {
                        containerLog.debug("Add rule with pattern " + rule.getPatternString()
                                + " and substitution " + rule.getSubstitutionString());
                    }
                    for (int i = (conditions.size() - 1); i > 0; i--) {
                        if (conditions.get(i - 1).isOrnext()) {
                            conditions.get(i).setOrnext(true);
                        }
                    }
                    for (RewriteCond condition : conditions) {
                        if (containerLog.isDebugEnabled()) {
                            RewriteCond cond = condition;
                            containerLog.debug("Add condition " + cond.getCondPattern()
                                    + " test " + cond.getTestString() + " to rule with pattern "
                                    + rule.getPatternString() + " and substitution "
                                    + rule.getSubstitutionString() + (cond.isOrnext() ? " [OR]" : "")
                                    + (cond.isNocase() ? " [NC]" : ""));
                        }
                        rule.addCondition(condition);
                    }
                    conditions.clear();
                    rules.add(rule);
                } else if (result instanceof RewriteCond) {
                    conditions.add((RewriteCond) result);
                } else if (result instanceof Object[]) {
                    String mapName = (String) ((Object[]) result)[0];
                    RewriteMap map = (RewriteMap) ((Object[]) result)[1];
                    maps.put(mapName, map);
                    // Keep the original configuration line as it is not possible to get
                    // the parameters back without an API change
                    mapsConfiguration.add(line);
                    if (map instanceof Lifecycle) {
                        ((Lifecycle) map).start();
                    }
                }
            } catch (IOException e) {
                containerLog.error(sm.getString("rewriteValve.readError"), e);
            }
        }
        this.rules = rules.toArray(new RewriteRule[0]);

        // Finish parsing the rules
        for (RewriteRule rule : this.rules) {
            rule.parse(maps);
        }
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        for (RewriteMap map : maps.values()) {
            if (map instanceof Lifecycle) {
                ((Lifecycle) map).stop();
            }
        }
        maps.clear();
        rules = null;
    }


    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        if (!getEnabled() || rules == null || rules.length == 0) {
            getNext().invoke(request, response);
            return;
        }

        if (Boolean.TRUE.equals(invoked.get())) {
            try {
                getNext().invoke(request, response);
            } finally {
                invoked.set(null);
            }
            return;
        }

        try {

            Resolver resolver = new ResolverImpl(request);

            invoked.set(Boolean.TRUE);

            // As long as MB isn't a char sequence or affiliated, this has to be
            // converted to a string
            Charset uriCharset = request.getConnector().getURICharset();
            String originalQueryStringEncoded = request.getQueryString();
            MessageBytes urlMB =
                    context ? request.getRequestPathMB() : request.getDecodedRequestURIMB();
            urlMB.toChars();
            CharSequence urlDecoded = urlMB.getCharChunk();
            CharSequence host = request.getServerName();
            boolean rewritten = false;
            boolean done = false;
            boolean qsa = false;
            boolean qsd = false;
            for (int i = 0; i < rules.length; i++) {
                RewriteRule rule = rules[i];
                CharSequence test = (rule.isHost()) ? host : urlDecoded;
                CharSequence newtest = rule.evaluate(test, resolver);
                if (newtest != null && !test.equals(newtest.toString())) {
                    if (containerLog.isDebugEnabled()) {
                        containerLog.debug("Rewrote " + test + " as " + newtest
                                + " with rule pattern " + rule.getPatternString());
                    }
                    if (rule.isHost()) {
                        host = newtest;
                    } else {
                        urlDecoded = newtest;
                    }
                    rewritten = true;
                }

                // Check QSA before the final reply
                if (!qsa && newtest != null && rule.isQsappend()) {
                    qsa = true;
                }

                if (!qsa && newtest != null && rule.isQsdiscard()) {
                    qsd = true;
                }

                // Final reply

                // - forbidden
                if (rule.isForbidden() && newtest != null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    done = true;
                    break;
                }
                // - gone
                if (rule.isGone() && newtest != null) {
                    response.sendError(HttpServletResponse.SC_GONE);
                    done = true;
                    break;
                }

                // - redirect (code)
                if (rule.isRedirect() && newtest != null) {
                    // Append the query string to the url if there is one and it
                    // hasn't been rewritten
                    String urlStringDecoded = urlDecoded.toString();
                    int index = urlStringDecoded.indexOf('?');
                    String rewrittenQueryStringDecoded;
                    if (index == -1) {
                        rewrittenQueryStringDecoded = null;
                    } else {
                        rewrittenQueryStringDecoded = urlStringDecoded.substring(index + 1);
                        urlStringDecoded = urlStringDecoded.substring(0, index);
                    }

                    StringBuilder urlStringEncoded =
                            new StringBuilder(URLEncoder.DEFAULT.encode(urlStringDecoded, uriCharset));
                    if (!qsd && originalQueryStringEncoded != null
                            && originalQueryStringEncoded.length() > 0) {
                        if (rewrittenQueryStringDecoded == null) {
                            urlStringEncoded.append('?');
                            urlStringEncoded.append(originalQueryStringEncoded);
                        } else {
                            if (qsa) {
                                // if qsa is specified append the query
                                urlStringEncoded.append('?');
                                urlStringEncoded.append(URLEncoder.QUERY.encode(
                                        rewrittenQueryStringDecoded, uriCharset));
                                urlStringEncoded.append('&');
                                urlStringEncoded.append(originalQueryStringEncoded);
                            } else if (index == urlStringEncoded.length() - 1) {
                                // if the ? is the last character delete it, its only purpose was to
                                // prevent the rewrite module from appending the query string
                                urlStringEncoded.deleteCharAt(index);
                            } else {
                                urlStringEncoded.append('?');
                                urlStringEncoded.append(URLEncoder.QUERY.encode(
                                        rewrittenQueryStringDecoded, uriCharset));
                            }
                        }
                    } else if (rewrittenQueryStringDecoded != null) {
                        urlStringEncoded.append('?');
                        urlStringEncoded.append(
                                URLEncoder.QUERY.encode(rewrittenQueryStringDecoded, uriCharset));
                    }

                    // Insert the context if
                    // 1. this valve is associated with a context
                    // 2. the url starts with a leading slash
                    // 3. the url isn't absolute
                    if (context && urlStringEncoded.charAt(0) == '/' &&
                            !UriUtil.hasScheme(urlStringEncoded)) {
                        urlStringEncoded.insert(0, request.getContext().getEncodedPath());
                    }
                    if (rule.isNoescape()) {
                        response.sendRedirect(
                                UDecoder.URLDecode(urlStringEncoded.toString(), uriCharset));
                    } else {
                        response.sendRedirect(urlStringEncoded.toString());
                    }
                    response.setStatus(rule.getRedirectCode());
                    done = true;
                    break;
                }

                // Reply modification

                // - cookie
                if (rule.isCookie() && newtest != null) {
                    Cookie cookie = new Cookie(rule.getCookieName(),
                            rule.getCookieResult());
                    cookie.setDomain(rule.getCookieDomain());
                    cookie.setMaxAge(rule.getCookieLifetime());
                    cookie.setPath(rule.getCookiePath());
                    cookie.setSecure(rule.isCookieSecure());
                    cookie.setHttpOnly(rule.isCookieHttpOnly());
                    response.addCookie(cookie);
                }
                // - env (note: this sets a request attribute)
                if (rule.isEnv() && newtest != null) {
                    for (int j = 0; j < rule.getEnvSize(); j++) {
                        request.setAttribute(rule.getEnvName(j), rule.getEnvResult(j));
                    }
                }
                // - content type (note: this will not force the content type, use a filter
                //   to do that)
                if (rule.isType() && newtest != null) {
                    request.setContentType(rule.getTypeValue());
                }

                // Control flow processing

                // - chain (skip remaining chained rules if this one does not match)
                if (rule.isChain() && newtest == null) {
                    for (int j = i; j < rules.length; j++) {
                        if (!rules[j].isChain()) {
                            i = j;
                            break;
                        }
                    }
                    continue;
                }
                // - last (stop rewriting here)
                if (rule.isLast() && newtest != null) {
                    break;
                }
                // - next (redo again)
                if (rule.isNext() && newtest != null) {
                    i = 0;
                    continue;
                }
                // - skip (n rules)
                if (newtest != null) {
                    i += rule.getSkip();
                }

            }

            if (rewritten) {
                if (!done) {
                    // See if we need to replace the query string
                    String urlStringDecoded = urlDecoded.toString();
                    String queryStringDecoded = null;
                    int queryIndex = urlStringDecoded.indexOf('?');
                    if (queryIndex != -1) {
                        queryStringDecoded = urlStringDecoded.substring(queryIndex+1);
                        urlStringDecoded = urlStringDecoded.substring(0, queryIndex);
                    }
                    // Save the current context path before re-writing starts
                    String contextPath = null;
                    if (context) {
                        contextPath = request.getContextPath();
                    }
                    // Populated the encoded (i.e. undecoded) requestURI
                    request.getCoyoteRequest().requestURI().setString(null);
                    CharChunk chunk = request.getCoyoteRequest().requestURI().getCharChunk();
                    chunk.recycle();
                    if (context) {
                        // This is neither decoded nor normalized
                        chunk.append(contextPath);
                    }
                    chunk.append(URLEncoder.DEFAULT.encode(urlStringDecoded, uriCharset));
                    request.getCoyoteRequest().requestURI().toChars();
                    // Decoded and normalized URI
                    // Rewriting may have denormalized the URL
                    urlStringDecoded = RequestUtil.normalize(urlStringDecoded);
                    request.getCoyoteRequest().decodedURI().setString(null);
                    chunk = request.getCoyoteRequest().decodedURI().getCharChunk();
                    chunk.recycle();
                    if (context) {
                        // This is decoded and normalized
                        chunk.append(request.getServletContext().getContextPath());
                    }
                    chunk.append(urlStringDecoded);
                    request.getCoyoteRequest().decodedURI().toChars();
                    // Set the new Query if there is one
                    if (queryStringDecoded != null) {
                        request.getCoyoteRequest().queryString().setString(null);
                        chunk = request.getCoyoteRequest().queryString().getCharChunk();
                        chunk.recycle();
                        chunk.append(URLEncoder.QUERY.encode(queryStringDecoded, uriCharset));
                        if (qsa && originalQueryStringEncoded != null &&
                                originalQueryStringEncoded.length() > 0) {
                            chunk.append('&');
                            chunk.append(originalQueryStringEncoded);
                        }
                        if (!chunk.isNull()) {
                            request.getCoyoteRequest().queryString().toChars();
                        }
                    }
                    // Set the new host if it changed
                    if (!host.equals(request.getServerName())) {
                        request.getCoyoteRequest().serverName().setString(null);
                        chunk = request.getCoyoteRequest().serverName().getCharChunk();
                        chunk.recycle();
                        chunk.append(host.toString());
                        request.getCoyoteRequest().serverName().toChars();
                    }
                    request.getMappingData().recycle();
                    // Reinvoke the whole request recursively
                    Connector connector = request.getConnector();
                    try {
                        if (!connector.getProtocolHandler().getAdapter().prepare(
                                request.getCoyoteRequest(), response.getCoyoteResponse())) {
                            return;
                        }
                    } catch (Exception e) {
                        // This doesn't actually happen in the Catalina adapter implementation
                    }
                    Pipeline pipeline = connector.getService().getContainer().getPipeline();
                    request.setAsyncSupported(pipeline.isAsyncSupported());
                    pipeline.getFirst().invoke(request, response);
                }
            } else {
                getNext().invoke(request, response);
            }

        } finally {
            invoked.set(null);
        }

    }


    /**
     * @return config base.
     */
    protected File getConfigBase() {
        File configBase =
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        } else {
            return configBase;
        }
    }


    /**
     * Find the configuration path where the rewrite configuration file
     * will be stored.
     * @param resourceName The rewrite configuration file name
     * @return the full rewrite configuration path
     */
    protected String getHostConfigPath(String resourceName) {
        StringBuffer result = new StringBuffer();
        Container container = getContainer();
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host) {
                host = container;
            }
            if (container instanceof Engine) {
                engine = container;
            }
            container = container.getParent();
        }
        if (engine != null) {
            result.append(engine.getName()).append('/');
        }
        if (host != null) {
            result.append(host.getName()).append('/');
        }
        result.append(resourceName);
        return result.toString();
    }


    /**
     * This factory method will parse a line formed like:
     *
     * Example:
     *  RewriteCond %{REMOTE_HOST}  ^host1.*  [OR]
     *
     * @param line A line from the rewrite configuration
     * @return The condition, rule or map resulting from parsing the line
     */
    public static Object parse(String line) {
        QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(line);
        if (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.equals("RewriteCond")) {
                // RewriteCond TestString CondPattern [Flags]
                RewriteCond condition = new RewriteCond();
                if (tokenizer.countTokens() < 2) {
                    throw new IllegalArgumentException(sm.getString("rewriteValve.invalidLine", line));
                }
                condition.setTestString(tokenizer.nextToken());
                condition.setCondPattern(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    String flags = tokenizer.nextToken();
                    condition.setFlagsString(flags);
                    if (flags.startsWith("[") && flags.endsWith("]")) {
                        flags = flags.substring(1, flags.length() - 1);
                    }
                    StringTokenizer flagsTokenizer = new StringTokenizer(flags, ",");
                    while (flagsTokenizer.hasMoreElements()) {
                        parseCondFlag(line, condition, flagsTokenizer.nextToken());
                    }
                }
                return condition;
            } else if (token.equals("RewriteRule")) {
                // RewriteRule Pattern Substitution [Flags]
                RewriteRule rule = new RewriteRule();
                if (tokenizer.countTokens() < 2) {
                    throw new IllegalArgumentException(sm.getString("rewriteValve.invalidLine", line));
                }
                rule.setPatternString(tokenizer.nextToken());
                rule.setSubstitutionString(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    String flags = tokenizer.nextToken();
                    rule.setFlagsString(flags);
                    if (flags.startsWith("[") && flags.endsWith("]")) {
                        flags = flags.substring(1, flags.length() - 1);
                    }
                    StringTokenizer flagsTokenizer = new StringTokenizer(flags, ",");
                    while (flagsTokenizer.hasMoreElements()) {
                        parseRuleFlag(line, rule, flagsTokenizer.nextToken());
                    }
                }
                return rule;
            } else if (token.equals("RewriteMap")) {
                // RewriteMap name rewriteMapClassName whateverOptionalParameterInWhateverFormat
                // FIXME: Possibly implement more special maps from https://httpd.apache.org/docs/2.4/rewrite/rewritemap.html
                if (tokenizer.countTokens() < 2) {
                    throw new IllegalArgumentException(sm.getString("rewriteValve.invalidLine", line));
                }
                String name = tokenizer.nextToken();
                String rewriteMapClassName = tokenizer.nextToken();
                RewriteMap map = null;
                if (rewriteMapClassName.startsWith("int:")) {
                    map = InternalRewriteMap.toMap(rewriteMapClassName.substring("int:".length()));
                } else if (rewriteMapClassName.startsWith("prg:")) {
                    rewriteMapClassName = rewriteMapClassName.substring("prg:".length());
                }
                if (map == null) {
                    try {
                        map = (RewriteMap) (Class.forName(
                                rewriteMapClassName).getConstructor().newInstance());
                    } catch (Exception e) {
                        throw new IllegalArgumentException(sm.getString("rewriteValve.invalidMapClassName", line));
                    }
                }
                if (tokenizer.hasMoreTokens()) {
                    map.setParameters(tokenizer.nextToken());
                }
                Object[] result = new Object[2];
                result[0] = name;
                result[1] = map;
                return result;
            } else if (token.startsWith("#")) {
                // it's a comment, ignore it
            } else {
                throw new IllegalArgumentException(sm.getString("rewriteValve.invalidLine", line));
            }
        }
        return null;
    }


    /**
     * Parser for RewriteCond flags.
     * @param line The configuration line being parsed
     * @param condition The current condition
     * @param flag The flag
     */
    protected static void parseCondFlag(String line, RewriteCond condition, String flag) {
        if (flag.equals("NC") || flag.equals("nocase")) {
            condition.setNocase(true);
        } else if (flag.equals("OR") || flag.equals("ornext")) {
            condition.setOrnext(true);
        } else {
            throw new IllegalArgumentException(sm.getString("rewriteValve.invalidFlags", line, flag));
        }
    }


    /**
     * Parser for RewriteRule flags.
     * @param line The configuration line being parsed
     * @param rule The current rule
     * @param flag The flag
     */
    protected static void parseRuleFlag(String line, RewriteRule rule, String flag) {
        if (flag.equals("B")) {
            rule.setEscapeBackReferences(true);
        } else if (flag.equals("chain") || flag.equals("C")) {
            rule.setChain(true);
        } else if (flag.startsWith("cookie=") || flag.startsWith("CO=")) {
            rule.setCookie(true);
            if (flag.startsWith("cookie")) {
                flag = flag.substring("cookie=".length());
            } else if (flag.startsWith("CO=")) {
                flag = flag.substring("CO=".length());
            }
            StringTokenizer tokenizer = new StringTokenizer(flag, ":");
            if (tokenizer.countTokens() < 2) {
                throw new IllegalArgumentException(sm.getString("rewriteValve.invalidFlags", line, flag));
            }
            rule.setCookieName(tokenizer.nextToken());
            rule.setCookieValue(tokenizer.nextToken());
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieDomain(tokenizer.nextToken());
            }
            if (tokenizer.hasMoreTokens()) {
                try {
                    rule.setCookieLifetime(Integer.parseInt(tokenizer.nextToken()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(sm.getString("rewriteValve.invalidFlags", line, flag), e);
                }
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookiePath(tokenizer.nextToken());
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieSecure(Boolean.parseBoolean(tokenizer.nextToken()));
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieHttpOnly(Boolean.parseBoolean(tokenizer.nextToken()));
            }
        } else if (flag.startsWith("env=") || flag.startsWith("E=")) {
            rule.setEnv(true);
            if (flag.startsWith("env=")) {
                flag = flag.substring("env=".length());
            } else if (flag.startsWith("E=")) {
                flag = flag.substring("E=".length());
            }
            int pos = flag.indexOf(':');
            if (pos == -1 || (pos + 1) == flag.length()) {
                throw new IllegalArgumentException(sm.getString("rewriteValve.invalidFlags", line, flag));
            }
            rule.addEnvName(flag.substring(0, pos));
            rule.addEnvValue(flag.substring(pos + 1));
        } else if (flag.startsWith("forbidden") || flag.startsWith("F")) {
            rule.setForbidden(true);
        } else if (flag.startsWith("gone") || flag.startsWith("G")) {
            rule.setGone(true);
        } else if (flag.startsWith("host") || flag.startsWith("H")) {
            rule.setHost(true);
        } else if (flag.startsWith("last") || flag.startsWith("L")) {
            rule.setLast(true);
        } else if (flag.startsWith("nocase") || flag.startsWith("NC")) {
            rule.setNocase(true);
        } else if (flag.startsWith("noescape") || flag.startsWith("NE")) {
            rule.setNoescape(true);
        } else if (flag.startsWith("next") || flag.startsWith("N")) {
            rule.setNext(true);
        // Note: Proxy is not supported as Tomcat does not have proxy
        //       capabilities
        } else if (flag.startsWith("qsappend") || flag.startsWith("QSA")) {
            rule.setQsappend(true);
        } else if (flag.startsWith("qsdiscard") || flag.startsWith("QSD")) {
            rule.setQsdiscard(true);
        } else if (flag.startsWith("redirect") || flag.startsWith("R")) {
            rule.setRedirect(true);
            int redirectCode = HttpServletResponse.SC_FOUND;
            if (flag.startsWith("redirect=") || flag.startsWith("R=")) {
                if (flag.startsWith("redirect=")) {
                    flag = flag.substring("redirect=".length());
                } else if (flag.startsWith("R=")) {
                    flag = flag.substring("R=".length());
                }
                switch(flag) {
                    case "temp":
                        redirectCode = HttpServletResponse.SC_FOUND;
                        break;
                    case "permanent":
                        redirectCode = HttpServletResponse.SC_MOVED_PERMANENTLY;
                        break;
                    case "seeother":
                        redirectCode = HttpServletResponse.SC_SEE_OTHER;
                        break;
                    default:
                        redirectCode = Integer.parseInt(flag);
                        break;
                }
            }
            rule.setRedirectCode(redirectCode);
        } else if (flag.startsWith("skip") || flag.startsWith("S")) {
            if (flag.startsWith("skip=")) {
                flag = flag.substring("skip=".length());
            } else if (flag.startsWith("S=")) {
                flag = flag.substring("S=".length());
            }
            rule.setSkip(Integer.parseInt(flag));
        } else if (flag.startsWith("type") || flag.startsWith("T")) {
            if (flag.startsWith("type=")) {
                flag = flag.substring("type=".length());
            } else if (flag.startsWith("T=")) {
                flag = flag.substring("T=".length());
            }
            rule.setType(true);
            rule.setTypeValue(flag);
        } else {
            throw new IllegalArgumentException(sm.getString("rewriteValve.invalidFlags", line, flag));
        }
    }
}
