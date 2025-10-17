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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
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
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource;
import org.apache.tomcat.util.http.RequestUtil;

/**
 * Note: Extra caution should be used when adding a Rewrite Rule. When specifying a regex to match for in a Rewrite
 * Rule, certain regex could allow an attacker to DoS your server, as Java's regex parsing is vulnerable to
 * "catastrophic backtracking" (also known as "Regular expression Denial of Service", or ReDoS). There are some open
 * source tools to help detect vulnerable regex, though in general it is a hard problem. A good defence is to use a
 * regex debugger on your desired regex, and read more on the subject of catastrophic backtracking.
 *
 * @see <a href= "https://www.owasp.org/index.php/Regular_expression_Denial_of_Service_-_ReDoS">OWASP ReDoS</a>
 */
public class RewriteValve extends ValveBase {

    private static final URLEncoder REWRITE_DEFAULT_ENCODER;
    private static final URLEncoder REWRITE_QUERY_ENCODER;

    static {
        /*
         * See the detailed explanation of encoding/decoding during URL re-writing in the invoke() method.
         *
         * These encoders perform the second stage of encoding, after re-writing has completed. These rewrite specific
         * encoders treat '%' as a safe character so that URLs and query strings already processed by encodeForRewrite()
         * do not end up with double encoding of '%' characters.
         */
        REWRITE_DEFAULT_ENCODER = (URLEncoder) URLEncoder.DEFAULT.clone();
        REWRITE_DEFAULT_ENCODER.addSafeCharacter('%');

        REWRITE_QUERY_ENCODER = (URLEncoder) URLEncoder.QUERY.clone();
        REWRITE_QUERY_ENCODER.addSafeCharacter('%');
    }

    /**
     * The rewrite rules that the valve will use.
     */
    protected RewriteRule[] rules = null;


    /**
     * If rewriting occurs, the whole request will be processed again.
     */
    protected ThreadLocal<Boolean> invoked = new ThreadLocal<>();


    /**
     * Relative path to the configuration file. Note: If the valve's container is a context, this will be relative to
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
    protected Map<String,RewriteMap> maps = new ConcurrentHashMap<>();


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
    protected void startInternal() throws LifecycleException {

        super.startInternal();

        InputStream is = null;

        // Process configuration file for this valve
        if (getContainer() instanceof Context) {
            context = true;
            String webInfResourcePath = "/WEB-INF/" + resourcePath;
            is = ((Context) getContainer()).getServletContext().getResourceAsStream(webInfResourcePath);
            if (containerLog.isDebugEnabled()) {
                if (is == null) {
                    containerLog.debug(sm.getString("rewriteValve.noConfiguration", webInfResourcePath));
                } else {
                    containerLog.debug(sm.getString("rewriteValve.readConfiguration", webInfResourcePath));
                }
            }
        } else {
            String resourceName = Container.getConfigPath(getContainer(), resourcePath);
            try {
                ConfigurationSource.Resource resource = ConfigFileLoader.getSource().getResource(resourceName);
                is = resource.getInputStream();
            } catch (IOException ioe) {
                if (containerLog.isDebugEnabled()) {
                    containerLog.debug(sm.getString("rewriteValve.noConfiguration", resourceName), ioe);
                }
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
            } catch (IOException ioe) {
                containerLog.error(sm.getString("rewriteValve.closeError"), ioe);
            }
        }

    }

    public void setConfiguration(String configuration) throws Exception {
        if (containerLog == null) {
            containerLog = LogFactory.getLog(getContainer().getLogName() + ".rewrite");
        }
        for (RewriteMap map : maps.values()) {
            if (map instanceof Lifecycle) {
                ((Lifecycle) map).stop();
            }
        }
        maps.clear();
        parse(new BufferedReader(new StringReader(configuration)));
    }

    public String getConfiguration() {
        StringBuilder buffer = new StringBuilder();
        for (String mapConfiguration : mapsConfiguration) {
            buffer.append(mapConfiguration).append("\r\n");
        }
        if (!mapsConfiguration.isEmpty()) {
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
        ArrayList<String> mapsConfiguration = new ArrayList<>();
        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                Object result = parse(line);
                if (result instanceof RewriteRule rule) {
                    if (containerLog.isTraceEnabled()) {
                        containerLog.trace("Add rule with pattern " + rule.getPatternString() + " and substitution " +
                                rule.getSubstitutionString());
                    }
                    for (int i = (conditions.size() - 1); i > 0; i--) {
                        if (conditions.get(i - 1).isOrnext()) {
                            conditions.get(i).setOrnext(true);
                        }
                    }
                    for (RewriteCond condition : conditions) {
                        if (containerLog.isTraceEnabled()) {
                            containerLog.trace("Add condition " + condition.getCondPattern() + " test " +
                                    condition.getTestString() + " to rule with pattern " + rule.getPatternString() +
                                    " and substitution " + rule.getSubstitutionString() +
                                    (condition.isOrnext() ? " [OR]" : "") + (condition.isNocase() ? " [NC]" : ""));
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
            } catch (IOException ioe) {
                containerLog.error(sm.getString("rewriteValve.readError"), ioe);
            }
        }
        this.mapsConfiguration = mapsConfiguration;

        // Finish parsing the rules
        for (RewriteRule rule : rules) {
            rule.parse(maps);
        }

        this.rules = rules.toArray(new RewriteRule[0]);
    }

    @Override
    protected void stopInternal() throws LifecycleException {
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
    public void invoke(Request request, Response response) throws IOException, ServletException {

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

            Resolver resolver = new ResolverImpl(request, containerLog);

            invoked.set(Boolean.TRUE);

            // As long as MB isn't a char sequence or affiliated, this has to be converted to a string
            Charset uriCharset = request.getConnector().getURICharset();
            String queryStringOriginalEncoded = request.getQueryString();
            MessageBytes urlMB = context ? request.getRequestPathMB() : request.getDecodedRequestURIMB();
            urlMB.toChars();
            CharSequence urlDecoded = urlMB.getCharChunk();

            /*
             * The URL presented to the rewrite valve is the URL that is used for request mapping. That URL has been
             * processed to: remove path parameters; remove the query string; decode; and normalize the URL. It may
             * contain literal '%', '?' and/or ';' characters at this point.
             *
             * The re-write rules need to be able to process URLs with literal '?' characters and add query strings
             * without the two becoming confused. The re-write rules also need to be able to insert literal '%'
             * characters without them being confused with %nn encoding.
             *
             * To meet these requirement, the URL is processed as follows.
             *
             * Step 1. The URL is partially re-encoded by encodeForRewrite(). This method encodes any literal '%', ';'
             * and/or '?' characters in the URL using the standard %nn form.
             *
             * Step 2. The re-write processing runs with the provided re-write rules against the partially encoded URL.
             * If a re-write rule needs to insert a literal '%', ';' or '?', it must do so in %nn encoded form.
             *
             * Step 3. The URL (and query string if present) is re-encoded using the re-write specific encoders
             * (REWRITE_DEFAULT_ENCODER and REWRITE_QUERY_ENCODER) that behave the same was as the standard encoders
             * apart from '%' being treated as a safe character. This prevents double encoding of any '%' characters
             * present in the URL from steps 1 or 2.
             */

            // Step 1. Encode URL for processing by the re-write rules.
            CharSequence urlRewriteEncoded = encodeForRewrite(urlDecoded);
            CharSequence host = request.getServerName();
            boolean rewritten = false;
            boolean done = false;
            boolean qsa = false;
            boolean qsd = false;
            boolean valveSkip = false;

            // Step 2. Process the URL using the re-write rules.
            for (int i = 0; i < rules.length; i++) {
                RewriteRule rule = rules[i];
                CharSequence test = (rule.isHost()) ? host : urlRewriteEncoded;
                CharSequence newtest = rule.evaluate(test, resolver);
                if (newtest != null && !Objects.equals(test.toString(), newtest.toString())) {
                    if (containerLog.isTraceEnabled()) {
                        containerLog.trace(
                                "Rewrote " + test + " as " + newtest + " with rule pattern " + rule.getPatternString());
                    }
                    if (rule.isHost()) {
                        host = newtest;
                    } else {
                        urlRewriteEncoded = newtest;
                    }
                    rewritten = true;
                }

                // Check QSA before the final reply
                if (!qsa && newtest != null && rule.isQsappend()) {
                    qsa = true;
                }

                if (!qsd && newtest != null && rule.isQsdiscard()) {
                    qsd = true;
                }

                if (!valveSkip && newtest != null && rule.isValveSkip()) {
                    valveSkip = true;
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
                    String urlStringRewriteEncoded = urlRewriteEncoded.toString();
                    int index = urlStringRewriteEncoded.indexOf('?');
                    String rewrittenQueryStringRewriteEncoded;
                    if (index == -1) {
                        rewrittenQueryStringRewriteEncoded = null;
                    } else {
                        rewrittenQueryStringRewriteEncoded = urlStringRewriteEncoded.substring(index + 1);
                        urlStringRewriteEncoded = urlStringRewriteEncoded.substring(0, index);
                    }

                    // Step 3. Complete the 2nd stage to encoding.
                    StringBuilder urlStringEncoded =
                            new StringBuilder(REWRITE_DEFAULT_ENCODER.encode(urlStringRewriteEncoded, uriCharset));

                    if (!qsd && queryStringOriginalEncoded != null && !queryStringOriginalEncoded.isEmpty()) {
                        if (rewrittenQueryStringRewriteEncoded == null) {
                            urlStringEncoded.append('?');
                            urlStringEncoded.append(queryStringOriginalEncoded);
                        } else {
                            if (qsa) {
                                // if qsa is specified append the query
                                urlStringEncoded.append('?');
                                urlStringEncoded.append(
                                        REWRITE_QUERY_ENCODER.encode(rewrittenQueryStringRewriteEncoded, uriCharset));
                                urlStringEncoded.append('&');
                                urlStringEncoded.append(queryStringOriginalEncoded);
                            } else if (index == urlStringEncoded.length() - 1) {
                                // if the ? is the last character delete it, its only purpose was to
                                // prevent the rewrite module from appending the query string
                                urlStringEncoded.deleteCharAt(index);
                            } else {
                                urlStringEncoded.append('?');
                                urlStringEncoded.append(
                                        REWRITE_QUERY_ENCODER.encode(rewrittenQueryStringRewriteEncoded, uriCharset));
                            }
                        }
                    } else if (rewrittenQueryStringRewriteEncoded != null) {
                        urlStringEncoded.append('?');
                        urlStringEncoded
                                .append(REWRITE_QUERY_ENCODER.encode(rewrittenQueryStringRewriteEncoded, uriCharset));
                    }

                    // Insert the context if
                    // 1. this valve is associated with a context
                    // 2. the url starts with a leading slash
                    // 3. the url isn't absolute
                    if (context && urlStringEncoded.charAt(0) == '/' && !UriUtil.hasScheme(urlStringEncoded)) {
                        urlStringEncoded.insert(0, request.getContext().getEncodedPath());
                    }
                    String redirectPath;
                    if (rule.isNoescape()) {
                        redirectPath = UDecoder.URLDecode(urlStringEncoded.toString(), uriCharset);
                    } else {
                        redirectPath = urlStringEncoded.toString();
                    }
                    response.sendRedirect(response.encodeRedirectURL(redirectPath));
                    response.setStatus(rule.getRedirectCode());
                    done = true;
                    break;
                }

                // Reply modification

                // - cookie
                if (rule.isCookie() && newtest != null) {
                    Cookie cookie = new Cookie(rule.getCookieName(), rule.getCookieResult());
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
                // to do that)
                if (rule.isType() && newtest != null) {
                    response.setContentType(rule.getTypeValue());
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
                    String urlStringRewriteEncoded = urlRewriteEncoded.toString();
                    String queryStringRewriteEncoded = null;
                    int queryIndex = urlStringRewriteEncoded.indexOf('?');
                    if (queryIndex != -1) {
                        queryStringRewriteEncoded = urlStringRewriteEncoded.substring(queryIndex + 1);
                        urlStringRewriteEncoded = urlStringRewriteEncoded.substring(0, queryIndex);
                    }
                    // Parse path parameters from rewrite production and populate request path parameters
                    urlStringRewriteEncoded =
                            org.apache.catalina.util.RequestUtil.stripPathParams(urlStringRewriteEncoded, request);
                    // Save the current context path before re-writing starts
                    String contextPath = null;
                    if (context) {
                        contextPath = request.getContextPath();
                    }
                    // Populated the encoded (i.e. undecoded) requestURI
                    request.getCoyoteRequest().requestURI().setChars(MessageBytes.EMPTY_CHAR_ARRAY, 0, 0);
                    CharChunk chunk = request.getCoyoteRequest().requestURI().getCharChunk();
                    if (context) {
                        // This is neither decoded nor normalized
                        chunk.append(contextPath);
                    }

                    // Step 3. Complete the 2nd stage to encoding.
                    chunk.append(REWRITE_DEFAULT_ENCODER.encode(urlStringRewriteEncoded, uriCharset));
                    // Rewriting may have denormalized the URL and added encoded characters
                    // Decode then normalize
                    String urlStringRewriteDecoded = URLDecoder.decode(urlStringRewriteEncoded, uriCharset);
                    urlStringRewriteDecoded = RequestUtil.normalize(urlStringRewriteDecoded);
                    request.getCoyoteRequest().decodedURI().setChars(MessageBytes.EMPTY_CHAR_ARRAY, 0, 0);
                    chunk = request.getCoyoteRequest().decodedURI().getCharChunk();
                    if (context) {
                        // This is decoded and normalized
                        chunk.append(request.getServletContext().getContextPath());
                    }
                    chunk.append(urlStringRewriteDecoded);
                    // Set the new Query String
                    if (queryStringRewriteEncoded == null) {
                         // No new query string. Therefore the original is retained unless QSD is defined.
                        if (qsd) {
                            request.getCoyoteRequest().queryString().setChars(MessageBytes.EMPTY_CHAR_ARRAY, 0, 0);
                        }
                    } else {
                        // New query string. Therefore the original is dropped unless QSA is defined (and QSD is not).
                        request.getCoyoteRequest().queryString().setChars(MessageBytes.EMPTY_CHAR_ARRAY, 0, 0);
                        chunk = request.getCoyoteRequest().queryString().getCharChunk();
                        chunk.append(REWRITE_QUERY_ENCODER.encode(queryStringRewriteEncoded, uriCharset));
                        if (qsa && queryStringOriginalEncoded != null && !queryStringOriginalEncoded.isEmpty()) {
                            chunk.append('&');
                            chunk.append(queryStringOriginalEncoded);
                        }
                    }
                    // Set the new host if it changed
                    if (!host.equals(request.getServerName())) {
                        request.getCoyoteRequest().serverName().setChars(MessageBytes.EMPTY_CHAR_ARRAY, 0, 0);
                        chunk = request.getCoyoteRequest().serverName().getCharChunk();
                        chunk.append(host.toString());
                    }
                    request.getMappingData().recycle();
                    request.recycleSessionInfo();
                    // Reinvoke the whole request recursively
                    Connector connector = request.getConnector();
                    try {
                        if (!connector.getProtocolHandler().getAdapter().prepare(request.getCoyoteRequest(),
                                response.getCoyoteResponse())) {
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
                Valve next = getNext();
                if (valveSkip) {
                    next = next.getNext();
                    if (next == null) {
                        // Ignore and invoke the next valve normally
                        next = getNext();
                    }
                }
                next.invoke(request, response);
            }

        } finally {
            invoked.set(null);
        }

    }


    /**
     * This factory method will parse a line formed like:
     * <p>
     * Example: {@code RewriteCond %{REMOTE_HOST} ^host1.* [OR]}
     *
     * @param line A line from the rewrite configuration
     *
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
                    // If QSD and QSA are present, QSD always takes precedence
                    if (rule.isQsdiscard()) {
                        rule.setQsappend(false);
                    }
                }
                return rule;
            } else if (token.equals("RewriteMap")) {
                // RewriteMap name rewriteMapClassName whateverOptionalParameterInWhateverFormat
                if (tokenizer.countTokens() < 2) {
                    throw new IllegalArgumentException(sm.getString("rewriteValve.invalidLine", line));
                }
                String name = tokenizer.nextToken();
                String rewriteMapClassName = tokenizer.nextToken();
                RewriteMap map = null;
                if (rewriteMapClassName.startsWith("int:")) {
                    map = InternalRewriteMap.toMap(rewriteMapClassName.substring("int:".length()));
                } else if (rewriteMapClassName.startsWith("txt:")) {
                    map = new RandomizedTextRewriteMap(rewriteMapClassName.substring("txt:".length()), false);
                } else if (rewriteMapClassName.startsWith("rnd:")) {
                    map = new RandomizedTextRewriteMap(rewriteMapClassName.substring("rnd:".length()), true);
                } else if (rewriteMapClassName.startsWith("prg:")) {
                    // https://httpd.apache.org/docs/2.4/rewrite/rewritemap.html#prg
                    // Not worth implementing further since this is a simpler CGI
                    // piping stdin/stdout from an external native process
                    // Instead assume a class and use the RewriteMap interface
                    rewriteMapClassName = rewriteMapClassName.substring("prg:".length());
                } else if (rewriteMapClassName.startsWith("dbm:")) {
                    // FIXME: https://httpd.apache.org/docs/2.4/rewrite/rewritemap.html#dbm
                    // Probably too specific to HTTP Server to implement
                } else if (rewriteMapClassName.startsWith("dbd:") || rewriteMapClassName.startsWith("fastdbd:")) {
                    // FIXME: https://httpd.apache.org/docs/2.4/rewrite/rewritemap.html#dbd
                }
                if (map == null) {
                    try {
                        map = (RewriteMap) (Class.forName(rewriteMapClassName).getConstructor().newInstance());
                    } catch (Exception e) {
                        throw new IllegalArgumentException(sm.getString("rewriteValve.invalidMapClassName", line));
                    }
                }
                if (tokenizer.hasMoreTokens()) {
                    if (tokenizer.countTokens() == 1) {
                        map.setParameters(tokenizer.nextToken());
                    } else {
                        List<String> params = new ArrayList<>();
                        while (tokenizer.hasMoreTokens()) {
                            params.add(tokenizer.nextToken());
                        }
                        map.setParameters(params.toArray(new String[0]));
                    }
                }
                return new Object[] { name, map };
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
     *
     * @param line      The configuration line being parsed
     * @param condition The current condition
     * @param flag      The flag
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
     *
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
            // capabilities
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
                redirectCode = switch (flag) {
                    case "temp" -> HttpServletResponse.SC_FOUND;
                    case "permanent" -> HttpServletResponse.SC_MOVED_PERMANENTLY;
                    case "seeother" -> HttpServletResponse.SC_SEE_OTHER;
                    default -> Integer.parseInt(flag);
                };
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
        } else if (flag.startsWith("valveSkip") || flag.startsWith("VS")) {
            rule.setValveSkip(true);
        } else {
            throw new IllegalArgumentException(sm.getString("rewriteValve.invalidFlags", line, flag));
        }
    }


    private CharSequence encodeForRewrite(CharSequence input) {
        StringBuilder result = null;
        int pos = 0;
        int mark = 0;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '%' || c == ';' || c == '?') {
                if (result == null) {
                    result = new StringBuilder((int) (input.length() * 1.1));
                }
                result.append(input.subSequence(mark, pos));
                result.append('%');
                result.append(Character.forDigit((c >> 4) & 0xF, 16));
                result.append(Character.forDigit(c & 0xF, 16));
                mark = pos + 1;
            }
            pos++;
        }
        if (result != null) {
            result.append(input.subSequence(mark, input.length()));
            return result;
        } else {
            return input;
        }
    }


}
