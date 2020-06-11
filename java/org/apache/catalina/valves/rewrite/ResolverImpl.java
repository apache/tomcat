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

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Request;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.jsse.PEMFile;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.net.openssl.ciphers.EncryptionLevel;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;

public class ResolverImpl extends Resolver {

    protected Request request = null;

    public ResolverImpl(Request request) {
        this.request = request;
    }

    /**
     * The following are not implemented:
     * - SERVER_ADMIN
     * - API_VERSION
     * - IS_SUBREQ
     */
    @Override
    public String resolve(String key) {
        if (key.equals("HTTP_USER_AGENT")) {
            return request.getHeader("user-agent");
        } else if (key.equals("HTTP_REFERER")) {
            return request.getHeader("referer");
        } else if (key.equals("HTTP_COOKIE")) {
            return request.getHeader("cookie");
        } else if (key.equals("HTTP_FORWARDED")) {
            return request.getHeader("forwarded");
        } else if (key.equals("HTTP_HOST")) {
            // Don't look directly at the host header to handle:
            // - Host name in HTTP/1.1 request line
            // - HTTP/0.9 & HTTP/1.0 requests
            // - HTTP/2 :authority pseudo header
            return request.getServerName();
        } else if (key.equals("HTTP_PROXY_CONNECTION")) {
            return request.getHeader("proxy-connection");
        } else if (key.equals("HTTP_ACCEPT")) {
            return request.getHeader("accept");
        } else if (key.equals("REMOTE_ADDR")) {
            return request.getRemoteAddr();
        } else if (key.equals("REMOTE_HOST")) {
            return request.getRemoteHost();
        } else if (key.equals("REMOTE_PORT")) {
            return String.valueOf(request.getRemotePort());
        } else if (key.equals("REMOTE_USER")) {
            return request.getRemoteUser();
        } else if (key.equals("REMOTE_IDENT")) {
            return request.getRemoteUser();
        } else if (key.equals("REQUEST_METHOD")) {
            return request.getMethod();
        } else if (key.equals("SCRIPT_FILENAME")) {
            return request.getServletContext().getRealPath(request.getServletPath());
        } else if (key.equals("REQUEST_PATH")) {
            return request.getRequestPathMB().toString();
        } else if (key.equals("CONTEXT_PATH")) {
            return request.getContextPath();
        } else if (key.equals("SERVLET_PATH")) {
            return emptyStringIfNull(request.getServletPath());
        } else if (key.equals("PATH_INFO")) {
            return emptyStringIfNull(request.getPathInfo());
        } else if (key.equals("QUERY_STRING")) {
            return emptyStringIfNull(request.getQueryString());
        } else if (key.equals("AUTH_TYPE")) {
            return request.getAuthType();
        } else if (key.equals("DOCUMENT_ROOT")) {
            return request.getServletContext().getRealPath("/");
        } else if (key.equals("SERVER_NAME")) {
            return request.getLocalName();
        } else if (key.equals("SERVER_ADDR")) {
            return request.getLocalAddr();
        } else if (key.equals("SERVER_PORT")) {
            return String.valueOf(request.getLocalPort());
        } else if (key.equals("SERVER_PROTOCOL")) {
            return request.getProtocol();
        } else if (key.equals("SERVER_SOFTWARE")) {
            return "tomcat";
        } else if (key.equals("THE_REQUEST")) {
            return request.getMethod() + " " + request.getRequestURI()
            + " " + request.getProtocol();
        } else if (key.equals("REQUEST_URI")) {
            return request.getRequestURI();
        } else if (key.equals("REQUEST_FILENAME")) {
            return request.getPathTranslated();
        } else if (key.equals("HTTPS")) {
            return request.isSecure() ? "on" : "off";
        } else if (key.equals("TIME_YEAR")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        } else if (key.equals("TIME_MON")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.MONTH));
        } else if (key.equals("TIME_DAY")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        } else if (key.equals("TIME_HOUR")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        } else if (key.equals("TIME_MIN")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.MINUTE));
        } else if (key.equals("TIME_SEC")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.SECOND));
        } else if (key.equals("TIME_WDAY")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        } else if (key.equals("TIME")) {
            return FastHttpDateFormat.getCurrentDate();
        }
        return null;
    }

    @Override
    public String resolveEnv(String key) {
        Object result = request.getAttribute(key);
        return (result != null) ? result.toString() : System.getProperty(key);
    }

    @Override
    public String resolveSsl(String key) {
        SSLSupport sslSupport = (SSLSupport) request.getAttribute(SSLSupport.SESSION_MGR);
        try {
            // SSL_SRP_USER: no planned support for SRP
            // SSL_SRP_USERINFO: no planned support for SRP
            if (key.equals("HTTPS")) {
                return String.valueOf(sslSupport != null);
            } else if (key.equals("SSL_PROTOCOL")) {
                return sslSupport.getProtocol();
            } else if (key.equals("SSL_SESSION_ID")) {
                return sslSupport.getSessionId();
            } else if (key.equals("SSL_SESSION_RESUMED")) {
                // FIXME session resumption state, not available anywhere
            } else if (key.equals("SSL_SECURE_RENEG")) {
                // FIXME available from SSLHostConfig
            } else if (key.equals("SSL_COMPRESS_METHOD")) {
                // FIXME available from SSLHostConfig
            } else if (key.equals("SSL_TLS_SNI")) {
                // FIXME from handshake SNI processing
            } else if (key.equals("SSL_CIPHER")) {
                return sslSupport.getCipherSuite();
            } else if (key.equals("SSL_CIPHER_EXPORT")) {
                String cipherSuite = sslSupport.getCipherSuite();
                Set<Cipher> cipherList = OpenSSLCipherConfigurationParser.parse(cipherSuite);
                if (cipherList.size() == 1) {
                    Cipher cipher = cipherList.iterator().next();
                    if (cipher.getLevel().equals(EncryptionLevel.EXP40)
                            || cipher.getLevel().equals(EncryptionLevel.EXP56)) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
            } else if (key.equals("SSL_CIPHER_ALGKEYSIZE")) {
                String cipherSuite = sslSupport.getCipherSuite();
                Set<Cipher> cipherList = OpenSSLCipherConfigurationParser.parse(cipherSuite);
                if (cipherList.size() == 1) {
                    Cipher cipher = cipherList.iterator().next();
                    return String.valueOf(cipher.getAlg_bits());
                }
            } else if (key.equals("SSL_CIPHER_USEKEYSIZE")) {
                return sslSupport.getKeySize().toString();
            } else if (key.startsWith("SSL_CLIENT_")) {
                X509Certificate[] certificates = sslSupport.getPeerCertificateChain();
                if (certificates != null && certificates.length > 0) {
                    key = key.substring("SSL_CLIENT_".length());
                    String result = resolveSslCertificates(key, certificates);
                    if (result != null) {
                        return result;
                    } else if (key.startsWith("SAN_OTHER_msUPN_")) {
                        // Type otherName, which is 0
                        key = key.substring("SAN_OTHER_msUPN_".length());
                        // FIXME OID from resolveAlternateName
                    } else if (key.equals("CERT_RFC4523_CEA")) {
                        // FIXME return certificate[0] format CertificateExactAssertion in RFC4523
                    } else if (key.equals("VERIFY")) {
                        // FIXME return verification state, not available anywhere
                    }
                }
            } else if (key.startsWith("SSL_SERVER_")) {
                X509Certificate[] certificates = sslSupport.getLocalCertificateChain();
                if (certificates != null && certificates.length > 0) {
                    key = key.substring("SSL_SERVER_".length());
                    String result = resolveSslCertificates(key, certificates);
                    if (result != null) {
                        return result;
                    } else if (key.startsWith("SAN_OTHER_dnsSRV_")) {
                        // Type otherName, which is 0
                        key = key.substring("SAN_OTHER_dnsSRV_".length());
                        // FIXME OID from resolveAlternateName
                    }
                }
            }
        } catch (IOException e) {
            // TLS access error
        }
        return null;
    }

    private String resolveSslCertificates(String key, X509Certificate[] certificates) {
        if (key.equals("M_VERSION")) {
            return String.valueOf(certificates[0].getVersion());
        } else if (key.equals("M_SERIAL")) {
            return certificates[0].getSerialNumber().toString();
        } else if (key.equals("S_DN")) {
            return certificates[0].getSubjectDN().getName();
        } else if (key.startsWith("S_DN_")) {
            key = key.substring("S_DN_".length());
            return resolveComponent(certificates[0].getSubjectX500Principal().getName(), key);
        } else if (key.startsWith("SAN_Email_")) {
            // Type rfc822Name, which is 1
            key = key.substring("SAN_Email_".length());
            return resolveAlternateName(certificates[0], 1, Integer.parseInt(key));
        } else if (key.startsWith("SAN_DNS_")) {
            // Type dNSName, which is 2
            key = key.substring("SAN_DNS_".length());
            return resolveAlternateName(certificates[0], 2, Integer.parseInt(key));
        } else if (key.equals("I_DN")) {
            return certificates[0].getIssuerDN().getName();
        } else if (key.startsWith("I_DN_")) {
            key = key.substring("I_DN_".length());
            return resolveComponent(certificates[0].getIssuerX500Principal().getName(), key);
        } else if (key.equals("V_START")) {
            return String.valueOf(certificates[0].getNotBefore().getTime());
        } else if (key.equals("V_END")) {
            return String.valueOf(certificates[0].getNotAfter().getTime());
        } else if (key.equals("V_REMAIN")) {
            long remain = certificates[0].getNotAfter().getTime() - System.currentTimeMillis();
            if (remain < 0) {
                remain = 0L;
            }
            // Return remaining days
            return String.valueOf(TimeUnit.MILLISECONDS.toDays(remain));
        } else if (key.equals("A_SIG")) {
            return certificates[0].getSigAlgName();
        } else if (key.equals("A_KEY")) {
            return certificates[0].getPublicKey().getAlgorithm();
        } else if (key.equals("CERT")) {
            try {
                return PEMFile.toPEM(certificates[0]);
            } catch (CertificateEncodingException e) {
            }
        } else if (key.startsWith("CERT_CHAIN_")) {
            key = key.substring("CERT_CHAIN_".length());
            try {
                return PEMFile.toPEM(certificates[Integer.parseInt(key)]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException
                    | CertificateEncodingException e) {
                // Ignore
            }
        }
        return null;
    }

    private String resolveComponent(String fullDN, String component) {
        HashMap<String, String> components = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(fullDN, ",");
        while (tokenizer.hasMoreElements()) {
            String token = tokenizer.nextToken().trim();
            int pos = token.indexOf("=");
            if (pos > 0 && (pos + 1) < token.length()) {
                components.put(token.substring(0, pos), token.substring(pos + 1));
            }
        }
        return components.get(component);
    }

    private String resolveAlternateName(X509Certificate certificate, int type, int n) {
        try {
            Collection<List<?>> alternateNames = certificate.getSubjectAlternativeNames();
            if (alternateNames != null) {
                List<String> elements = new ArrayList<>();
                for (List<?> alternateName : alternateNames) {
                    Integer alternateNameType = (Integer) alternateName.get(0);
                    if (alternateNameType.intValue() == type) {
                        elements.add(String.valueOf(alternateName.get(1)));
                    }
                }
                if (elements.size() > n) {
                    return elements.get(n);
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException
                | CertificateParsingException e) {
            // Ignore
        }
        return null;
    }

    @Override
    public String resolveHttp(String key) {
        String header = request.getHeader(key);
        if (header == null) {
            return "";
        } else {
            return header;
        }
    }

    @Override
    public boolean resolveResource(int type, String name) {
        WebResourceRoot resources = request.getContext().getResources();
        WebResource resource = resources.getResource(name);
        if (!resource.exists()) {
            return false;
        } else {
            switch (type) {
            case 0:
                return resource.isDirectory();
            case 1:
                return resource.isFile();
            case 2:
                return resource.isFile() && resource.getContentLength() > 0;
            default:
                return false;
            }
        }
    }

    private static final String emptyStringIfNull(String value) {
        if (value == null) {
            return "";
        } else {
            return value;
        }
    }

    @Override
    public Charset getUriCharset() {
        return request.getConnector().getURICharset();
    }
}
