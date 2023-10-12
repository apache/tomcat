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
package org.apache.catalina.security;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.res.StringManager;

/**
 * A {@link LifecycleListener} that may be used to monitor the expiration dates of TLS certificates and trigger
 * automatic reloading of the TLS configuration a set number of days before the TLS certificate expires.
 * <p>
 * This listener assumes there is some other process (certbot, cloud infrastructure, etc) that renews the certificate on
 * a regular basis and replaces the current certificate with the new one.
 * <p>
 * This listener does <b>NOT</b> re-read the Tomcat configuration from server.xml. If you make changes to server.xml you
 * must restart the Tomcat process to pick up those changes.
 */
public class TLSCertificateReloadListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(TLSCertificateReloadListener.class);
    private static final StringManager sm = StringManager.getManager(TLSCertificateReloadListener.class);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    // Configuration
    private int checkPeriod = 24 * 60 * 60;
    private int daysBefore = 14;

    // State
    private Calendar nextCheck = Calendar.getInstance();


    /**
     * Get the time, in seconds, between reloading checks.
     * <p>
     * The periodic process for {@code LifecycleListener} typically runs much more frequently than this listener
     * requires. This attribute controls the period between checks.
     * <p>
     * If not specified, a default of 86,400 seconds (24 hours) is used.
     *
     * @return The time, in seconds, between reloading checks
     */
    public int getCheckPeriod() {
        return checkPeriod;
    }


    /**
     * Set the time, in seconds, between reloading checks.
     *
     * @param checkPeriod The new time, in seconds, between reloading checks
     */
    public void setCheckPeriod(int checkPeriod) {
        this.checkPeriod = checkPeriod;
    }


    /**
     * Get the number of days before the expiry of a TLS certificate that it is expected that the new certificate will
     * be in place and the reloading can be triggered.
     * <p>
     * If not specified, a default of 14 days is used.
     *
     * @return The number of days before the expiry of a TLS certificate that the reloading will be triggered
     */
    public int getDaysBefore() {
        return daysBefore;
    }


    /**
     * Set the number of days before the expiry of a TLS certificate that it is expected that the new certificate will
     * be in place and the reloading can be triggered.
     *
     * @param daysBefore the number of days before the expiry of the current certificate that reloading will be
     *                       triggered
     */
    public void setDaysBefore(int daysBefore) {
        this.daysBefore = daysBefore;
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (event.getType().equals(Lifecycle.PERIODIC_EVENT)) {
            Server server;
            if (event.getSource() instanceof Server) {
                server = (Server) event.getSource();
            } else {
                return;
            }
            checkCertificatesForRenewal(server);
        } else if (event.getType().equals(Lifecycle.BEFORE_INIT_EVENT)) {
            // This is the earliest event in Lifecycle
            if (!(event.getLifecycle() instanceof Server)) {
                log.warn(sm.getString("listener.notServer", event.getLifecycle().getClass().getSimpleName()));
            }
        }
    }


    private void checkCertificatesForRenewal(Server server) {
        // Only run the check once every checkPeriod (seconds)
        Calendar calendar = Calendar.getInstance();
        if (calendar.compareTo(nextCheck) > 0) {
            nextCheck.add(Calendar.SECOND, getCheckPeriod());
        } else {
            return;
        }

        /*
         * Advance current date by "daysBefore". Any certificates that expire before this time should have been renewed
         * by now so reloading the associated SSLHostConfig should pick up the new certificate.
         */
        calendar.add(Calendar.DAY_OF_MONTH, getDaysBefore());

        // Check all of the certificates
        Service[] services = server.findServices();
        for (Service service : services) {
            Connector[] connectors = service.findConnectors();
            for (Connector connector : connectors) {
                SSLHostConfig[] sslHostConfigs = connector.findSslHostConfigs();
                for (SSLHostConfig sslHostConfig : sslHostConfigs) {
                    if (!sslHostConfig.certificatesExpiringBefore(calendar.getTime()).isEmpty()) {
                        // One or more certificates is due to expire and should have been renewed
                        // Reload the configuration
                        try {
                            connector.getProtocolHandler().addSslHostConfig(sslHostConfig, true);
                            // Now check again
                            Set<X509Certificate> expiringCertificates =
                                    sslHostConfig.certificatesExpiringBefore(calendar.getTime());
                            log.info(sm.getString("tlsCertRenewalListener.reloadSuccess", connector,
                                    sslHostConfig.getHostName()));
                            if (!expiringCertificates.isEmpty()) {
                                for (X509Certificate expiringCertificate : expiringCertificates) {
                                    log.warn(sm.getString("tlsCertRenewalListener.notRenewed", connector,
                                            sslHostConfig.getHostName(),
                                            expiringCertificate.getSubjectX500Principal().getName(),
                                            dateFormat.format(expiringCertificate.getNotAfter())));
                                }
                            }
                        } catch (IllegalArgumentException iae) {
                            log.error(sm.getString("tlsCertRenewalListener.reloadFailed", connector,
                                    sslHostConfig.getHostName()), iae);
                        }
                    }
                }
            }
        }
    }
}
