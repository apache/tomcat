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
package org.apache.tomcat.util.net.ocsp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.apache.tomcat.util.net.TesterSupport;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/*
 * Based on https://github.com/wdawson/revoker - ALv2 licensed
 */
public class TesterOcspResponderServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // Config
    public static final String INIT_FIXED_RESPONSE = "fixedResponse";
    private TesterOcspResponder.OcspResponse fixedResponse;

    // Cached OCSP processing components
    private DigestCalculatorProvider digestCalculatorProvider;
    private X509CertificateHolder[] responderCertificateChain;
    private RespID responderID;
    private ContentSigner contentSigner;
    private Map<BigInteger,CertificateState> certificateStatuses;

    enum CertificateState {
        GOOD,
        REVOKED
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        String value = config.getInitParameter(INIT_FIXED_RESPONSE);
        if (value != null) {
            fixedResponse = TesterOcspResponder.OcspResponse.valueOf(value);
        }

        try (FileReader reader = new FileReader(TesterSupport.DB_INDEX)) {
            certificateStatuses = loadCertificateStatuses(reader);
        } catch (IOException e) {
            throw new ServletException(e);
        }

        // Enable the Bouncy Castle Provider
        Provider provider = new BouncyCastleProvider();
        Security.addProvider(provider);

        // Create the digest provider
        try {
            this.digestCalculatorProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        } catch (OperatorCreationException e) {
            throw new ServletException(e);
        }

        // Parse the OCSP responder cert
        X509Certificate responderCert;
        try (PEMParser pemParser = new PEMParser(new FileReader(TesterSupport.OCSP_RESPONDER_RSA_CERT))) {
            JcaX509CertificateConverter x509Converter = new JcaX509CertificateConverter().setProvider(provider);
            responderCert = x509Converter.getCertificate((X509CertificateHolder) pemParser.readObject());
        } catch (IOException | CertificateException e) {
            throw new ServletException(e);
        }

        // Parse the OCSP responder issuer certificate
        X509Certificate issuerCert;
        try (PEMParser pemParser = new PEMParser(new FileReader(TesterSupport.CA_CERT_PEM))) {
            JcaX509CertificateConverter x509Converter = new JcaX509CertificateConverter().setProvider(provider);
            issuerCert = x509Converter.getCertificate((X509CertificateHolder) pemParser.readObject());
        } catch (IOException | CertificateException e) {
            throw new ServletException(e);
        }

        // Create the responder certificate chain
        try {
            responderCertificateChain = new X509CertificateHolder[] { new JcaX509CertificateHolder(responderCert),
                    new JcaX509CertificateHolder(issuerCert) };
        } catch (CertificateEncodingException e) {
            throw new ServletException(e);
        }

        // Create the responder ID
        SubjectPublicKeyInfo publicKeyInfo =
                SubjectPublicKeyInfo.getInstance(responderCert.getPublicKey().getEncoded());

        try {
            // Only SHA-1 supported
            responderID = new RespID(publicKeyInfo,
                    digestCalculatorProvider.get(new DefaultDigestAlgorithmIdentifierFinder().find("SHA-1")));
        } catch (OperatorCreationException | OCSPException e) {
            throw new ServletException(e);
        }

        // Parse the private key
        PrivateKey responderKey;
        try (PEMParser pemParser = new PEMParser(new FileReader(TesterSupport.OCSP_RESPONDER_RSA_KEY))) {
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pemParser.readObject());
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            responderKey = converter.getPrivateKey(privateKeyInfo);
        } catch (IOException e) {
            throw new ServletException(e);
        }

        // Create the content signer
        try {
            contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(responderKey);
        } catch (OperatorCreationException e) {
            throw new ServletException(e);
        }
    }


    static Map<BigInteger,CertificateState> loadCertificateStatuses(Reader input) throws IOException {
        Map<BigInteger,CertificateState> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(input);
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String[] fields = line.split("\\t", -1);
            if (fields.length < 4) {
                throw new IOException("Invalid certificate database entry at line " + lineNumber);
            }

            CertificateState state;
            if ("V".equals(fields[0])) {
                state = CertificateState.GOOD;
            } else if ("R".equals(fields[0])) {
                state = CertificateState.REVOKED;
            } else {
                continue;
            }

            try {
                result.put(new BigInteger(fields[3], 16), state);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid certificate serial at line " + lineNumber, e);
            }
        }
        return Map.copyOf(result);
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // The request is base64 encoded and passed as the path (less the leading '/')
        String urlEncoded = req.getRequestURI().substring(1);

        // Handle longer URI used for TestSecurity2017Ocsp
        if (urlEncoded.startsWith("xxxxxxxx")) {
            urlEncoded = urlEncoded.substring(urlEncoded.indexOf("/") + 1);
        }
        String base64 = URLDecoder.decode(urlEncoded, StandardCharsets.US_ASCII);
        byte[] derEncodeOCSPRequest = Base64.getDecoder().decode(base64);

        // Process the OCSP request
        OCSPResp ocspResponse = processOscpRequest(derEncodeOCSPRequest);

        // Write the OCSP response
        ServletOutputStream sos = resp.getOutputStream();
        sos.write(ocspResponse.getEncoded());
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // The request is passed in the request body

        // Determine request content length (or start with a reasonable default)
        int contentLength = req.getContentLength();
        if (contentLength == -1) {
            // OCSP requests are small. 1k should be plenty and it can expand if necessary.
            contentLength = 1024;
        }

        // Read the body into a byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength);
        IOTools.flow(req.getInputStream(), baos);

        // Process the OCSP request
        OCSPResp ocspResponse = processOscpRequest(baos.toByteArray());

        // Write the OCSP response
        ServletOutputStream sos = resp.getOutputStream();
        sos.write(ocspResponse.getEncoded());
    }


    private OCSPResp processOscpRequest(byte[] derEncodeOCSPRequest) throws ServletException, IOException {

        OCSPReq ocspReq = new OCSPReq(derEncodeOCSPRequest);

        // For the tests as currently written it is safe to assume the request is valid

        // Set the responses for each certificate
        BasicOCSPRespBuilder responseBuilder = new BasicOCSPRespBuilder(responderID);
        Req[] requests = ocspReq.getRequestList();
        for (Req request : requests) {
            CertificateID certificateID = request.getCertID();
            if (fixedResponse == null) {
                CertificateState state = certificateStatuses.get(certificateID.getSerialNumber());
                if (state == null) {
                    responseBuilder.addResponse(certificateID, new UnknownStatus());
                } else {
                    switch (state) {
                        case GOOD:
                            responseBuilder.addResponse(certificateID, CertificateStatus.GOOD);
                            break;
                        case REVOKED:
                            responseBuilder.addResponse(certificateID, new RevokedStatus(new Date(0)));
                            break;
                    }
                }
            } else {
                switch (fixedResponse) {
                    case OK:
                        responseBuilder.addResponse(certificateID, CertificateStatus.GOOD);
                        break;
                    case REVOKED:
                        responseBuilder.addResponse(certificateID, new RevokedStatus(new Date(0)));
                        break;
                    case TRY_LATER:
                        // NO-OP
                        break;
                    case UNKNOWN:
                        responseBuilder.addResponse(certificateID, new UnknownStatus());
                        break;
                    case INTERNAL_ERROR:
                        throw new ServletException("Internal error");
                }
            }
        }

        // Build and sign the response
        OCSPResp ocspResponse;
        try {
            BasicOCSPResp basicResponse = responseBuilder.build(contentSigner, responderCertificateChain, new Date());
            if (fixedResponse == TesterOcspResponder.OcspResponse.TRY_LATER) {
                ocspResponse = new OCSPRespBuilder().build(OCSPRespBuilder.TRY_LATER, null);
            } else {
                ocspResponse = new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basicResponse);
            }
        } catch (OCSPException e) {
            throw new ServletException(e);
        }

        return ocspResponse;
    }
}
