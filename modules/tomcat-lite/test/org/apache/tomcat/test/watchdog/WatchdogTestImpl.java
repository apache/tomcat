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

/**
 * @Author Costin, Ramesh.Mandava
 */

package org.apache.tomcat.test.watchdog;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.tomcat.lite.io.Hex;

// derived from Jsp

public class WatchdogTestImpl {

    int failureCount = 0;

    int passCount = 0;

    Throwable lastError;

    boolean hasFailed = false;

    String prefix = "http";

    String host = "localhost";

    String localHost = null;

    String localIP = null;

    int port = 8080;

    int debug = 0;

    String description = "No description";

    String request;

    HashMap requestHeaders = new HashMap();

    String content;

    // true if task is nested
    private boolean nested = false;

    // Expected response
    boolean magnitude = true;

    boolean exactMatch = false;

    // expect a response body
    boolean expectResponseBody = true;

    // Match the body against a golden file
    String goldenFile;

    // Match the body against a string
    String responseMatch;

    // the response should include the following headers
    HashMap expectHeaders = new HashMap();

    // Headers that should not be found in response
    HashMap unexpectedHeaders = new HashMap();

    // Match request line
    String returnCode = "";

    String returnCodeMsg = "";

    // Actual response
    String responseLine;

    byte[] responseBody;

    HashMap headers;

    // For Report generation
    StringBuffer resultOut = new StringBuffer();

    boolean firstTask = false;

    boolean lastTask = false;

    String expectedString;

    String actualString;

    String testName;

    String assertion;

    String testStrategy;

    // For Session Tracking
    static Hashtable sessionHash;

    static Hashtable cookieHash;

    String testSession;

    Vector cookieVector;

    URL requestURL;

    CookieController cookieController;

    /**
     * Creates a new <code>GTest</code> instance.
     *
     */
    public WatchdogTestImpl() {
    }

    /**
     * <code>setTestSession</code> adds a CookieController for the value of
     * sessionName
     *
     * @param sessionName
     *            a <code>String</code> value
     */
    public void setTestSession(String sessionName) {
        testSession = sessionName;

        if (sessionHash == null) {
            sessionHash = new Hashtable();
        } else if (sessionHash.get(sessionName) == null) {
            sessionHash.put(sessionName, new CookieController());
        }
    }

    /**
     * <code>setTestName</code> sets the current test name.
     *
     * @param tn
     *            current testname.
     */
    public void setTestName(String tn) {
        testName = tn;
    }

    /**
     * <code>setAssertion</code> sets the assertion text for the current test.
     *
     * @param assertion
     *            assertion text
     */
    public void setAssertion(String assertion) {
        this.assertion = assertion;
    }

    /**
     * <code>setTestStrategy</code> sets the test strategy for the current test.
     *
     * @param strategy
     *            test strategy text
     */
    public void setTestStrategy(String strategy) {
        testStrategy = strategy;
    }

    /**
     * <code>getTestName</code> returns the current test name.
     *
     * @return a <code>String</code> value
     */
    public String getTestName() {
        return testName;
    }

    /**
     * <code>getAssertion</code> returns the current assertion text.
     *
     * @return a <code>String</code> value
     */
    public String getAssertion() {
        return assertion;
    }

    /**
     * <code>getTestStrategy</code> returns the current test strategy test.
     *
     * @return a <code>String</code> value
     */
    public String getTestStrategy() {
        return testStrategy;
    }

    /**
     * <code>setFirstTask</code> denotes that current task being executed is the
     * first task within the list.
     *
     * @param a
     *            <code>boolean</code> value
     */
    public void setFirstTask(boolean val) {
        firstTask = val;
    }

    /**
     * <code>setLastTask</code> denotes that the current task being executed is
     * the last task within the list.
     *
     * @param a
     *            <code>boolean</code> value
     */
    public void setLastTask(boolean val) {
        lastTask = val;
    }

    /**
     * <code>setPrefix</code> sets the protocol prefix. Defaults to "http"
     *
     * @param prefix
     *            Either http or https
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * <code>setHost</code> sets hostname where the target server is running.
     * Defaults to "localhost"
     *
     * @param h
     *            a <code>String</code> value
     */
    public void setHost(String h) {
        this.host = h;
    }

    /**
     * <code>setPort</code> sets the port that the target server is listening
     * on. Defaults to "8080"
     *
     * @param portS
     *            a <code>String</code> value
     */
    public void setPort(String portS) {
        this.port = Integer.valueOf(portS).intValue();
    }

    /**
     * <code>setExactMatch</code> determines if a byte-by-byte comparsion is
     * made of the server's response and the test's goldenFile, or if a token
     * comparison is made. By default, only a token comparison is made
     * ("false").
     *
     * @param exact
     *            a <code>String</code> value
     */
    public void setExactMatch(String exact) {
        exactMatch = Boolean.valueOf(exact).booleanValue();
    }

    /**
     * <code>setContent</code> String value upon which the request header
     * Content-Length is based upon.
     *
     * @param s
     *            a <code>String</code> value
     */
    public void setContent(String s) {
        this.content = s;
    }

    /**
     * <code>setDebug</code> enables debug output. By default, this is disabled
     * ( value of "0" ).
     *
     * @param debugS
     *            a <code>String</code> value
     */
    public void setDebug(String debugS) {
        debug = Integer.valueOf(debugS).intValue();
    }

    /**
     * <code>setMagnitude</code> Expected return value of the test execution.
     * Defaults to "true"
     *
     * @param magnitudeS
     *            a <code>String</code> value
     */
    public void setMagnitude(String magnitudeS) {
        magnitude = Boolean.valueOf(magnitudeS).booleanValue();
    }

    /**
     * <code>setGoldenFile</code> Sets the goldenfile that will be used to
     * validate the server's response.
     *
     * @param s
     *            fully qualified path and filename
     */
    public void setGoldenFile(String s) {
        this.goldenFile = s;
    }

    /**
     * <code>setExpectResponseBody</code> sets a flag to indicate if a response
     * body is expected from the server or not
     *
     * @param b
     *            a <code>boolean</code> value
     */
    public void setExpectResponseBody(boolean b) {
        this.expectResponseBody = b;
    }

    /**
     * <code>setExpectHeaders</code> Configures GTest to look for the header
     * passed in the server's response.
     *
     * @param s
     *            a <code>String</code> value in the format of
     *            <header-field>:<header-value>
     */
    public void setExpectHeaders(String s) {
        this.expectHeaders = new HashMap();
        StringTokenizer tok = new StringTokenizer(s, "|");
        while (tok.hasMoreElements()) {
            String header = (String) tok.nextElement();
            setHeaderDetails(header, expectHeaders, false);
        }
    }

    /**
     * <code>setUnexpectedHeaders</code> Configures GTest to look for the header
     * passed to validate that it doesn't exist in the server's response.
     *
     * @param s
     *            a <code>String</code> value in the format of
     *            <header-field>:<header-value>
     */
    public void setUnexpectedHeaders(String s) {
        this.unexpectedHeaders = new HashMap();
        setHeaderDetails(s, unexpectedHeaders, false);
    }

    public void setNested(String s) {
        nested = Boolean.valueOf(s).booleanValue();
    }

    /**
     * <code>setResponseMatch</code> Match the passed value in the server's
     * response.
     *
     * @param s
     *            a <code>String</code> value
     */
    public void setResponseMatch(String s) {
        this.responseMatch = s;
    }

    /**
     * <code>setRequest</code> Sets the HTTP/HTTPS request to be sent to the
     * target server Ex. GET /servlet_path/val HTTP/1.0
     *
     * @param s
     *            a <code>String</code> value in the form of METHOD PATH
     *            HTTP_VERSION
     */
    public void setRequest(String s) {
        this.request = s;
    }

    /**
     * <code>setReturnCode</code> Sets the expected return code from the
     * server's response.
     *
     * @param code
     *            a valid HTTP response status code
     */
    public void setReturnCode(String code) {
        this.returnCode = code;
    }

    /**
     * Describe <code>setReturnCodeMsg</code> Sets the expected return message
     * to be found in the server's response.
     *
     * @param code
     *            a valid HTTP resonse status code
     * @param message
     *            a <code>String</code> value
     */
    public void setReturnCodeMsg(String message) {
        this.returnCodeMsg = message;
    }

    /**
     * <code>setRequestHeaders</code> Configures the request headers GTest
     * should send to the target server.
     *
     * @param s
     *            a <code>String</code> value in for format of
     *            <field-name>:<field-value>
     */
    public void setRequestHeaders(String s) {
        requestHeaders = new HashMap();
        StringTokenizer tok = new StringTokenizer(s, "|");
        while (tok.hasMoreElements()) {
            String header = (String) tok.nextElement();
            setHeaderDetails(header, requestHeaders, true);
        }
    }

    // Inner tests are not used currently, can be reworked

    // /**
    // * Add a Task to this container
    // *
    // * @param Task to add
    // */
    // public void addTask(Task task) {
    // children.add(task);
    // }

    /**
     * <code>execute</code> Executes the test.
     *
     * @exception BuildException
     *                if an error occurs
     */
    public void execute() {

        try {

            if (resultOut != null && !nested) {
                resultOut.append("\ntestName: " + testName);
                resultOut.append("\nreq: " + request);
                resultOut.append("\nassertion: " + assertion);
                resultOut.append("\ntestStrategy: " + testStrategy);
            }

            WatchdogHttpClient.dispatch(this);

            hasFailed = !checkResponse(magnitude);

            // if ( !children.isEmpty() ) {
            // Iterator iter = children.iterator();
            // while (iter.hasNext()) {
            // Task task = (Task) iter.next();
            // task.perform();
            // }
            // }

            if (!hasFailed && !nested) {
                passCount++;
                if (resultOut != null) {
                    resultOut.append("<result>PASS</result>\n");
                }
//                System.out.println(" PASSED " + testName + "        ("
//                        + request + ")");
            } else if (hasFailed && !nested) {
                failureCount++;
                if (resultOut != null) {
                    resultOut.append("<result>FAIL</result>\n");
                }
                System.out.println(" FAILED " + testName + "\n        ("
                        + request + ")\n" + resultOut.toString());
            }

        } catch (Exception ex) {
            failureCount++;
            System.out.println(" FAIL " + description + " (" + request + ")");
            lastError = ex;
            ex.printStackTrace();
        } finally {
            if (!nested) {
                hasFailed = false;
            }
        }
    }

    /**
     * <code>checkResponse</code> Executes various response checking mechanisms
     * against the server's response. Checks include:
     * <ul>
     * <li>expected headers
     * <li>unexpected headers
     * <li>return codes and messages in the Status-Line
     * <li>response body comparison againt a goldenfile
     * </ul>
     *
     * @param testCondition
     *            a <code>boolean</code> value
     * @return a <code>boolean</code> value
     * @exception Exception
     *                if an error occurs
     */
    private boolean checkResponse(boolean testCondition) throws Exception {
        boolean match = false;

        if (responseLine != null && !"".equals(responseLine)) {
            // If returnCode doesn't match
            if (responseLine.indexOf("HTTP/1.") > -1) {

                if (!returnCode.equals("")) {
                    boolean resCode = (responseLine.indexOf(returnCode) > -1);
                    boolean resMsg = (responseLine.indexOf(returnCodeMsg) > -1);

                    if (returnCodeMsg.equals("")) {
                        match = resCode;
                    } else {
                        match = (resCode && resMsg);
                    }

                    if (match != testCondition) {

                        if (resultOut != null) {
                            String expectedStatusCode = "<expectedStatusCode>"
                                    + returnCode + "</expectedReturnCode>\n";
                            String expectedReasonPhrase = "<expectedReasonPhrase>"
                                    + returnCodeMsg + "</expectedReasonPhrase>";
                            actualString = "<actualStatusLine>" + responseLine
                                    + "</actualStatusLine>\n";
                            resultOut.append(expectedStatusCode);
                            resultOut.append(expectedReasonPhrase);
                            resultOut.append(actualString);
                        }

                        return false;
                    }
                }
            } else {
                resultOut.append("\n<failure>No response or invalid response: "
                        + responseLine + "</failure>");
                return false;
            }
        } else {
            resultOut.append("\n<failure>No response from server</failure>");
            return false;
        }

        /*
         * Check for headers the test expects to be in the server's response
         */

        // Duplicate set of response headers
        HashMap copiedHeaders = cloneHeaders(headers);

        // used for error reporting
        String currentHeaderField = null;
        String currentHeaderValue = null;

        if (!expectHeaders.isEmpty()) {
            boolean found = false;
            String expHeader = null;

            if (!headers.isEmpty()) {
                Iterator expectIterator = expectHeaders.keySet().iterator();
                while (expectIterator.hasNext()) {
                    found = false;
                    String expFieldName = (String) expectIterator.next();
                    currentHeaderField = expFieldName;
                    ArrayList expectValues = (ArrayList) expectHeaders
                            .get(expFieldName);
                    Iterator headersIterator = copiedHeaders.keySet()
                            .iterator();

                    while (headersIterator.hasNext()) {
                        String headerFieldName = (String) headersIterator
                                .next();
                        ArrayList headerValues = (ArrayList) copiedHeaders
                                .get(headerFieldName);

                        // compare field names and values in an HTTP 1.x
                        // compliant fashion
                        if ((headerFieldName.equalsIgnoreCase(expFieldName))) {
                            int hSize = headerValues.size();
                            int eSize = expectValues.size();

                            // number of expected headers found in server
                            // response
                            int numberFound = 0;

                            for (int i = 0; i < eSize; i++) {
                                currentHeaderValue = (String) expectValues
                                        .get(i);

                                /*
                                 * Handle the Content-Type header appropriately
                                 * based on the the test is configured to look
                                 * for.
                                 */
                                if (currentHeaderField
                                        .equalsIgnoreCase("content-type")) {
                                    String resVal = (String) headerValues
                                            .get(0);
                                    if (currentHeaderValue.indexOf(';') > -1) {
                                        if (currentHeaderValue.equals(resVal)) {
                                            numberFound++;
                                            headerValues.remove(0);
                                        }
                                    } else if (resVal
                                            .indexOf(currentHeaderValue) > -1) {
                                        numberFound++;
                                        headerValues.remove(0);
                                    }
                                } else if (currentHeaderField
                                        .equalsIgnoreCase("location")) {
                                    String resVal = (String) headerValues
                                            .get(0);
                                    int idx = currentHeaderValue
                                            .indexOf(":80/");
                                    if (idx > -1) {
                                        String tempValue = currentHeaderValue
                                                .substring(0, idx)
                                                + currentHeaderValue
                                                        .substring(idx + 3);
                                        if (currentHeaderValue.equals(resVal)
                                                || tempValue.equals(resVal)) {
                                            numberFound++;
                                            headerValues.remove(0);
                                        }
                                    } else {
                                        if (currentHeaderValue.equals(resVal)) {
                                            numberFound++;
                                            headerValues.remove(0);
                                        }
                                    }
                                } else if (headerValues
                                        .contains(currentHeaderValue)) {
                                    numberFound++;
                                    headerValues.remove(headerValues
                                            .indexOf(currentHeaderValue));
                                }
                            }
                            if (numberFound == eSize) {
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        /*
                         * Expected headers not found in server response. Break
                         * the processing loop.
                         */
                        break;
                    }
                }
            }

            if (!found) {
                StringBuffer actualBuffer = new StringBuffer(128);
                if (resultOut != null) {
                    expectedString = "<expectedHeaderNotFound>"
                            + currentHeaderField + ": " + currentHeaderValue
                            + "</expectedHeader>\n";
                }
                if (!headers.isEmpty()) {
                    Iterator iter = headers.keySet().iterator();
                    while (iter.hasNext()) {
                        String headerName = (String) iter.next();
                        ArrayList vals = (ArrayList) headers.get(headerName);
                        String[] val = (String[]) vals.toArray(new String[vals
                                .size()]);
                        for (int i = 0; i < val.length; i++) {
                            if (resultOut != null) {
                                actualBuffer.append("<actualHeader>"
                                        + headerName + ": " + val[i]
                                        + "</actualHeader>\n");
                            }
                        }
                    }
                    if (resultOut != null) {
                        resultOut.append(expectedString);
                        resultOut.append(actualBuffer.toString());
                    }
                }
                return false;
            }
        }

        /*
         * Check to see if we're looking for unexpected headers. If we are,
         * compare the values in the unexectedHeaders ArrayList against the
         * headers from the server response. if the unexpected header is found,
         * then return false.
         */

        if (!unexpectedHeaders.isEmpty()) {
            boolean found = false;
            String unExpHeader = null;
            // Check if we got any unexpected headers

            if (!copiedHeaders.isEmpty()) {
                Iterator unexpectedIterator = unexpectedHeaders.keySet()
                        .iterator();
                while (unexpectedIterator.hasNext()) {
                    found = false;
                    String unexpectedFieldName = (String) unexpectedIterator
                            .next();
                    ArrayList unexpectedValues = (ArrayList) unexpectedHeaders
                            .get(unexpectedFieldName);
                    Iterator headersIterator = copiedHeaders.keySet()
                            .iterator();

                    while (headersIterator.hasNext()) {
                        String headerFieldName = (String) headersIterator
                                .next();
                        ArrayList headerValues = (ArrayList) copiedHeaders
                                .get(headerFieldName);

                        // compare field names and values in an HTTP 1.x
                        // compliant fashion
                        if ((headerFieldName
                                .equalsIgnoreCase(unexpectedFieldName))) {
                            int hSize = headerValues.size();
                            int eSize = unexpectedValues.size();
                            int numberFound = 0;
                            for (int i = 0; i < eSize; i++) {
                                if (headerValues.contains(unexpectedValues
                                        .get(i))) {
                                    numberFound++;
                                    if (headerValues.indexOf(headerFieldName) >= 0) {
                                        headerValues.remove(headerValues
                                                .indexOf(headerFieldName));
                                    }
                                }
                            }
                            if (numberFound == eSize) {
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        /*
                         * Expected headers not found in server response. Break
                         * the processing loop.
                         */
                        break;
                    }
                }
            }

            if (found) {
                resultOut.append("\n Unexpected header received from server: "
                        + unExpHeader);
                return false;
            }
        }

        if (responseMatch != null) {
            // check if we got the string we wanted
            if (expectResponseBody && responseBody == null) {
                resultOut.append("\n ERROR: got no response, expecting "
                        + responseMatch);
                return false;
            }
            String responseBodyString = new String(responseBody);
            if (responseBodyString.indexOf(responseMatch) < 0) {
                resultOut.append("\n ERROR: expecting match on "
                        + responseMatch);
                resultOut.append("\n Received: \n" + responseBodyString);
            }
        }

        if (!expectResponseBody && responseBody != null) {
            resultOut
                    .append("Received a response body from the server where none was expected");
            return false;
        }

        // compare the body
        if (goldenFile == null)
            return true;

        // Get the expected result from the "golden" file.
        byte[] expResult = getExpectedResult();
        String expResultS = (expResult == null) ? "" : new String(expResult);
        // Compare the results and set the status
        boolean cmp = true;

        if (exactMatch) {
            cmp = compare(responseBody, expResult);
        } else {
            cmp = compareWeak(responseBody, expResult);
        }

        if (cmp != testCondition) {

            if (resultOut != null) {
                expectedString = "<expectedBody>" + new String(expResult)
                        + "</expectedBody>\n";
                actualString = "<actualBody>"
                        + (responseBody != null ? new String(responseBody)
                                : "null") + "</actualBody>\n";
                resultOut.append(expectedString);
                resultOut.append(actualString);
            }

            return false;
        }

        return true;
    }

    /**
     * Replaces any |client.ip| and |client.host| parameter marks with the host
     * and IP values of the host upon which Watchdog is running.
     *
     * @param request
     *            An HTTP request.
     */
    String replaceMarkers(String req, Socket socket) {

        final String CLIENT_IP = "client.ip";
        final String CLIENT_HOME = "client.host";

        if (localIP == null || localHost == null) {
            InetAddress addr = socket.getLocalAddress();
            localHost = addr.getHostName();
            localIP = addr.getHostAddress();
        }

        if (req.indexOf('|') > -1) {
            StringTokenizer tok = new StringTokenizer(request, "|");
            StringBuffer sb = new StringBuffer(50);

            while (tok.hasMoreElements()) {
                String token = tok.nextToken();
                if (token.equals(CLIENT_IP)) {
                    sb.append(localIP);
                } else if (token.equals(CLIENT_HOME)) {
                    sb.append(localHost);
                } else {
                    sb.append(token);
                }
            }
            return sb.toString();
        } else {
            return req;
        }
    }

    /**
     * <code>getExpectedResult</code> returns a byte array containing the
     * content of the configured goldenfile
     *
     * @return goldenfile as a byte[]
     * @exception IOException
     *                if an error occurs
     */
    private byte[] getExpectedResult() throws IOException {
        byte[] expResult = { 'N', 'O', ' ', 'G', 'O', 'L', 'D', 'E', 'N', 'F',
                'I', 'L', 'E', ' ', 'F', 'O', 'U', 'N', 'D' };

        try {
            InputStream in = new BufferedInputStream(new FileInputStream(
                    goldenFile));
            return readBody(in);
        } catch (Exception ex) {
            System.out.println("Golden file not found: " + goldenFile);
            return expResult;
        }
    }

    /**
     * <code>compare</code> compares the two byte arrays passed in to verify
     * that the lengths of the arrays are equal, and that the content of the two
     * arrays, byte for byte are equal.
     *
     * @param fromServer
     *            a <code>byte[]</code> value
     * @param fromGoldenFile
     *            a <code>byte[]</code> value
     * @return <code>boolean</code> true if equal, otherwise false
     */
    private boolean compare(byte[] fromServer, byte[] fromGoldenFile) {
        if (fromServer == null || fromGoldenFile == null) {
            return false;
        }

        /*
         * Check to see that the respose and golden file lengths are equal. If
         * they are not, dump the hex and don't bother comparing the bytes. If
         * they are equal, iterate through the byte arrays and compare each
         * byte. If the bytes don't match, dump the hex representation of the
         * server response and the goldenfile and return false.
         */
        if (fromServer.length != fromGoldenFile.length) {
            StringBuffer sb = new StringBuffer(50);
            sb.append(" Response and golden files lengths do not match!\n");
            sb.append(" Server response length: ");
            sb.append(fromServer.length);
            sb.append("\n Goldenfile length: ");
            sb.append(fromGoldenFile.length);
            resultOut.append(sb.toString());
            sb = null;
            // dump the hex representation of the byte arrays
            dumpHex(fromServer, fromGoldenFile);

            return false;
        } else {

            int i = 0;
            int j = 0;

            while ((i < fromServer.length) && (j < fromGoldenFile.length)) {
                if (fromServer[i] != fromGoldenFile[j]) {
                    resultOut.append("\n Error at position " + (i + 1));
                    // dump the hex representation of the byte arrays
                    dumpHex(fromServer, fromGoldenFile);

                    return false;
                }

                i++;
                j++;
            }
        }

        return true;
    }

    /**
     * <code>compareWeak</code> creates new Strings from the passed arrays and
     * then uses a StringTokenizer to compare non-whitespace tokens.
     *
     * @param fromServer
     *            a <code>byte[]</code> value
     * @param fromGoldenFile
     *            a <code>byte[]</code> value
     * @return a <code>boolean</code> value
     */
    private boolean compareWeak(byte[] fromServer, byte[] fromGoldenFile) {
        if (fromServer == null || fromGoldenFile == null) {
            return false;
        }

        boolean status = true;

        String server = new String(fromServer);
        String golden = new String(fromGoldenFile);

        StringTokenizer st1 = new StringTokenizer(server);

        StringTokenizer st2 = new StringTokenizer(golden);

        while (st1.hasMoreTokens() && st2.hasMoreTokens()) {
            String tok1 = st1.nextToken();
            String tok2 = st2.nextToken();

            if (!tok1.equals(tok2)) {
                resultOut.append("\t FAIL*** : Rtok1 = " + tok1 + ", Etok2 = "
                        + tok2);
                status = false;
            }
        }

        if (st1.hasMoreTokens() || st2.hasMoreTokens()) {
            status = false;
        }

        if (!status) {
            StringBuffer sb = new StringBuffer(255);
            sb
                    .append("ERROR: Server's response and configured goldenfile do not match!\n");
            sb.append("Response received from server:\n");
            sb
                    .append("---------------------------------------------------------\n");
            sb.append(server);
            sb.append("\nContent of Goldenfile:\n");
            sb
                    .append("---------------------------------------------------------\n");
            sb.append(golden);
            sb.append("\n");
            resultOut.append(sb.toString());
        }
        return status;
    }

    /**
     * <code>readBody</code> reads the body of the response from the
     * InputStream.
     *
     * @param input
     *            an <code>InputStream</code>
     * @return a <code>byte[]</code> representation of the response
     */
    private byte[] readBody(InputStream input) {
        StringBuffer sb = new StringBuffer(255);
        while (true) {
            try {
                int ch = input.read();

                if (ch < 0) {
                    if (sb.length() == 0) {
                        return (null);
                    } else {
                        break;
                    }
                }
                sb.append((char) ch);

            } catch (IOException ex) {
                return null;
            }
        }
        return sb.toString().getBytes();
    }

    /**
     * <code>setHeaderDetails</code> Wrapper method for parseHeader. Allows easy
     * addition of headers to the specified HashMap
     *
     * @param line
     *            a <code>String</code> value
     * @param headerMap
     *            a <code>HashMap</code> value
     * @param isRequest
     *            a <code>boolean</code> indicating if the passed Header HashMap
     *            is for request headers
     */
    void setHeaderDetails(String line, HashMap headerHash, boolean isRequest) {
        StringTokenizer stk = new StringTokenizer(line, "##");

        while (stk.hasMoreElements()) {
            String presentHeader = stk.nextToken();
            parseHeader(presentHeader, headerHash, isRequest);
        }
    }

    /**
     * <code>parseHeader</code> parses input headers in format of "key:value"
     * The parsed header field-name will be used as a key in the passed HashMap
     * object, and the values found will be stored in an ArrayList associated
     * with the field-name key.
     *
     * @param line
     *            String representation of an HTTP header line.
     * @param headers
     *            a<code>HashMap</code> to store key/value header objects.
     * @param isRequest
     *            set to true if the headers being processed are requestHeaders.
     */
    void parseHeader(String line, HashMap headerMap, boolean isRequest) {
        // Parse the header name and value
        int colon = line.indexOf(":");

        if (colon < 0) {
            resultOut
                    .append("\n ERROR: Header is in incorrect format: " + line);
            return;
        }

        String name = line.substring(0, colon).trim();
        String value = line.substring(colon + 1).trim();

        if ((cookieVector != null) && (name.equalsIgnoreCase("Set-Cookie"))) {
            cookieVector.addElement(value);
            /*
             * if ( ( value.indexOf("JSESSIONID") > -1 ) ||
             * (value.indexOf("jsessionid") > -1 ) ) { String sessionId=
             * value.substring( value.indexOf("=")+1); if ( testSession != null
             * ) { sessionHash.put( testSession, sessionId ); }
             * System.out.println("Got Session-ID : " + sessionId ); }
             */
        }

        // System.out.println("HEADER: " +name + " " + value);

        ArrayList values = (ArrayList) headerMap.get(name);
        if (values == null) {
            values = new ArrayList();
        }
        // HACK
        if (value.indexOf(',') > -1 && !isRequest
                && !name.equalsIgnoreCase("Date")) {
            StringTokenizer st = new StringTokenizer(value, ",");
            while (st.hasMoreElements()) {
                values.add(st.nextToken());
            }
        } else {
            values.add(value);
        }

        headerMap.put(name, values);
    }

    /**
     * <code>dumpHex</code> helper method to dump formatted hex output of the
     * server response and the goldenfile.
     *
     * @param serverResponse
     *            a <code>byte[]</code> value
     * @param goldenFile
     *            a <code>byte[]</code> value
     */
    private void dumpHex(byte[] serverResponse, byte[] goldenFile) {
        StringBuffer outBuf = new StringBuffer(
                (serverResponse.length + goldenFile.length) * 2);

        String fromServerString = Hex.getHexDump(serverResponse, 0,
                serverResponse.length, true);
        String fromGoldenFileString = Hex.getHexDump(goldenFile, 0,
                goldenFile.length, true);

        outBuf
                .append(" Hex dump of server response and goldenfile below.\n\n### RESPONSE FROM SERVER ###\n");
        outBuf.append("----------------------------\n");
        outBuf.append(fromServerString);
        outBuf.append("\n\n### GOLDEN FILE ###\n");
        outBuf.append("-------------------\n");
        outBuf.append(fromGoldenFileString);
        outBuf.append("\n\n### END OF DUMP ###\n");

        resultOut.append(outBuf.toString());

    }

    /**
     * <code>cloneHeaders</code> returns a "cloned" HashMap of the map passed
     * in.
     *
     * @param map
     *            a <code>HashMap</code> value
     * @return a <code>HashMap</code> value
     */
    private HashMap cloneHeaders(HashMap map) {
        HashMap dupMap = new HashMap();
        Iterator iter = map.keySet().iterator();

        while (iter.hasNext()) {
            String key = new String((String) iter.next());
            ArrayList origValues = (ArrayList) map.get(key);
            ArrayList dupValues = new ArrayList();

            String[] dupVal = (String[]) origValues
                    .toArray(new String[origValues.size()]);
            for (int i = 0; i < dupVal.length; i++) {
                dupValues.add(new String(dupVal[i]));
            }

            dupMap.put(key, dupValues);
        }
        return dupMap;
    }

}
