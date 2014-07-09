/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.tomcat.buildutil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Ant task that submits a file to the Symantec code-signing service.
 */
public class SignCode extends Task {

    private final List<FileSet> filesets = new ArrayList<>();

    private static String USERNAME = "AOOAPI";
    private static String PASSWORD = "Demo1234!";
    private static String PARTNERCODE = "4615797APA95264";

    public void addFileset(FileSet fileset) {
        filesets.add(fileset);
    }


    @Override
    public void execute() throws BuildException {

        List<File> filesToSign = new ArrayList<>();

        // Process the filesets and populate the list of files that need to be
        // signed.
        for (FileSet fileset : filesets) {
            DirectoryScanner ds = fileset.getDirectoryScanner(getProject());
            File basedir = ds.getBasedir();
            String[] files = ds.getIncludedFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    File file = new File(basedir, files[i]);
                    filesToSign.add(file);
                }
            }
        }

        try {
            // Construct the signing request
            log("Constructing the code signing request");

            // Create the SOAP message
            MessageFactory factory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
            SOAPMessage message = factory.createMessage();

            // Populate envelope
            SOAPPart soapPart = message.getSOAPPart();
            SOAPEnvelope envelope = soapPart.getEnvelope();
            envelope.addNamespaceDeclaration("soapenv","http://schemas.xmlsoap.org/soap/envelope/");
            envelope.addNamespaceDeclaration("cod","http://api.ws.symantec.com/webtrust/codesigningservice");

            SOAPBody body = envelope.getBody();

            SOAPElement requestSigning =
                    body.addChildElement("requestSigning", "cod");

            SOAPElement requestSigningRequest =
                    requestSigning.addChildElement("requestSigningRequest", "cod");

            SOAPElement authToken = requestSigningRequest.addChildElement("authToken", "cod");
            SOAPElement userName = authToken.addChildElement("userName", "cod");
            userName.addTextNode(USERNAME);
            SOAPElement password = authToken.addChildElement("password", "cod");
            password.addTextNode(PASSWORD);
            SOAPElement partnerCode = authToken.addChildElement("partnerCode", "cod");
            partnerCode.addTextNode(PARTNERCODE);

            SOAPElement applicationName =
                    requestSigningRequest.addChildElement("applicationName", "cod");
            applicationName.addTextNode("Apache Tomcat");

            SOAPElement applicationVersion =
                    requestSigningRequest.addChildElement("applicationVersion", "cod");
            applicationVersion.addTextNode("8.0.x trunk");

            SOAPElement signingServiceName =
                    requestSigningRequest.addChildElement("signingServiceName", "cod");
            signingServiceName.addTextNode("Microsoft Signing");

            SOAPElement commaDelimitedFileNames =
                    requestSigningRequest.addChildElement("commaDelimitedFileNames", "cod");
            commaDelimitedFileNames.addTextNode(getFileNames(filesToSign.size()));

            SOAPElement application =
                    requestSigningRequest.addChildElement("application", "cod");
            application.addTextNode(getApplicationString(filesToSign));

            // Send the message
            SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
            SOAPConnection connection = soapConnectionFactory.createConnection();
            java.net.URL endpoint = new URL("https://test-api.ws.symantec.com:443/webtrust/SigningService");

            log("Sending siging request to server and waiting for reponse");
            SOAPMessage response = connection.call(message, endpoint);

            log("Processing response");
            SOAPElement responseBody = response.getSOAPBody();
            log(responseBody.getTextContent());

            // Should come back signed
            NodeList bodyNodes = responseBody.getChildNodes();
            NodeList requestSigningResponseNodes = bodyNodes.item(0).getChildNodes();
            NodeList returnNodes = requestSigningResponseNodes.item(0).getChildNodes();

            String signingSetID = null;
            String signingSetStatus = null;

            for (int i = 0; i < returnNodes.getLength(); i++) {
                Node returnNode = returnNodes.item(i);
                if (returnNode.getLocalName().equals("signingSetID")) {
                    signingSetID = returnNode.getTextContent();
                } else if (returnNode.getLocalName().equals("signingSetStatus")) {
                    signingSetStatus = returnNode.getTextContent();
                }
            }

            if (!"SIGNED".equals(signingSetStatus)) {
                throw new BuildException("Signing failed. Status was: " + signingSetStatus);
            }

            log("TODO: Download signingSet: " + signingSetID);


        } catch (SOAPException | IOException e) {
            throw new BuildException(e);
        }
    }

    /**
     * Signing service requires unique files names. Since files will be returned
     * in order, use dummy names that we know are unique.
     */
    private String getFileNames(int fileCount) {
        StringBuilder sb = new StringBuilder();

        boolean first = true;

        for (int i = 0; i < fileCount; i++) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(Integer.toString(i));
        }
        return sb.toString();
    }

    /**
     * Zips the files, base 64 encodes the resulting zip and then returns the
     * string. It would be far more efficient to stream this directly to the
     * signing server but the files that need to be signed are relatively small
     * and this simpler to write.
     *
     * @param files Files to be signed
     */
    private String getApplicationString(List<File> files) throws IOException {
        // 10 MB should be more than enough for Tomcat
        ByteArrayOutputStream baos = new ByteArrayOutputStream(10 * 1024 * 1024);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            byte[] buf = new byte[32 * 1024];

            for (int i = 0; i < files.size() ; i++) {
                try (FileInputStream fis = new FileInputStream(files.get(i))) {
                    ZipEntry zipEntry = new ZipEntry(Integer.toString(i));
                    zos.putNextEntry(zipEntry);

                    int numRead;
                    while ( (numRead = fis.read(buf) ) >= 0) {
                        zos.write(buf, 0, numRead);
                    }
                }
            }
        }

        log("" + baos.size());

        return Base64.encodeBase64String(baos.toByteArray());
    }
}
