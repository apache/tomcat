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

import java.io.FileReader;
import java.io.IOException;
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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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


    @Override
    public void init(ServletConfig config) throws ServletException {
        String value = config.getInitParameter(INIT_FIXED_RESPONSE);
        if (value != null) {
            fixedResponse = TesterOcspResponder.OcspResponse.valueOf(value);
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


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // The request is base64 encoded and passed as the path (less the leading '/')
        String urlEncoded = req.getRequestURI().substring(1);

        // Handle longer URI used for TestSecurity2017Ocsp
        if (urlEncoded.startsWith("xxxxxxxx")) {
            urlEncoded = urlEncoded.substring(urlEncoded.indexOf("/") + 1);
        }
        String base64 = URLDecoder.decode(urlEncoded, StandardCharsets.US_ASCII.name());
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
                switch (certificateID.getSerialNumber().intValue()) {
                    // TODO read index.db rather than hard-code certificate serial numbers
                    case 4096:
                    case 4098:
                    case 4100:
                    case 4101:
                        responseBuilder.addResponse(certificateID, CertificateStatus.GOOD);
                        break;
                    case 4097:
                    case 4099:
                    case 4102:
                        responseBuilder.addResponse(certificateID, new RevokedStatus(new Date(0)));
                        break;
                    default:
                        responseBuilder.addResponse(certificateID, new UnknownStatus());
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
