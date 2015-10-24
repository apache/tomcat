/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.net.openssl.ciphers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.res.StringManager;

/**
 * Class in charge with parsing openSSL expressions to define a list of ciphers.
 */
public class OpenSSLCipherConfigurationParser {

    private static final Log log = LogFactory.getLog(OpenSSLCipherConfigurationParser.class);
    private static final StringManager sm =
            StringManager.getManager("org.apache.tomcat.util.net.jsse.res");

    private static boolean initialized = false;

    private static final String SEPARATOR = ":|,| ";
    /**
     * If ! is used then the ciphers are permanently deleted from the list. The ciphers deleted can never reappear in the list
     * even if they are explicitly stated.
     */
    private static final String EXCLUDE = "!";
    /**
     * If - is used then the ciphers are deleted from the list, but some or all of the ciphers can be added again by later
     * options.
     */
    private static final String DELETE = "-";
    /**
     * If + is used then the ciphers are moved to the end of the list. This option doesn't add any new ciphers it just moves
     * matching existing ones.
     */
    private static final String TO_END = "+";
     /**
     * Lists of cipher suites can be combined in a single cipher string using the + character.
     * This is used as a logical and operation.
     * For example SHA1+DES represents all cipher suites containing the SHA1 and the DES algorithms.
     */
    private static final String AND = "+";
    /**
     * All ciphers by their openssl alias name.
     */
    private static final Map<String, List<Cipher>> aliases = new LinkedHashMap<>();

    /**
     * the 'NULL' ciphers that is those offering no encryption. Because these offer no encryption at all and are a security risk
     * they are disabled unless explicitly included.
     */
    private static final String eNULL = "eNULL";
    /**
     * The cipher suites offering no authentication. This is currently the anonymous DH algorithms. T These cipher suites are
     * vulnerable to a 'man in the middle' attack and so their use is normally discouraged.
     */
    private static final String aNULL = "aNULL";

    /**
     * 'high' encryption cipher suites. This currently means those with key lengths larger than 128 bits, and some cipher suites
     * with 128-bit keys.
     */
    private static final String HIGH = "HIGH";
    /**
     * 'medium' encryption cipher suites, currently some of those using 128 bit encryption.
     */
    private static final String MEDIUM = "MEDIUM";
    /**
     * 'low' encryption cipher suites, currently those using 64 or 56 bit encryption algorithms but excluding export cipher
     * suites.
     */
    private static final String LOW = "LOW";
    /**
     * Export encryption algorithms. Including 40 and 56 bits algorithms.
     */
    private static final String EXPORT = "EXPORT";
    /**
     * 40 bit export encryption algorithms.
     */
    private static final String EXPORT40 = "EXPORT40";
    /**
     * 56 bit export encryption algorithms.
     */
    private static final String EXPORT56 = "EXPORT56";
    /**
     * Cipher suites using RSA key exchange.
     */
    private static final String kRSA = "kRSA";
    /**
     * Cipher suites using RSA authentication.
     */
    private static final String aRSA = "aRSA";
    /**
     * Cipher suites using RSA for key exchange
     * Despite what the docs say, RSA is equivalent to kRSA.
     */
    private static final String RSA = "RSA";
    /**
     * Cipher suites using ephemeral DH key agreement.
     */
    private static final String kEDH = "kEDH";
    /**
     * Cipher suites using ephemeral DH key agreement.
     */
    private static final String kDHE = "kDHE";
    /**
     * Cipher suites using ephemeral DH key agreement. equivalent to kEDH:-ADH
     */
    private static final String EDH = "EDH";
    /**
     * Cipher suites using ephemeral DH key agreement. equivalent to kEDH:-ADH
     */
    private static final String DHE = "DHE";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with RSA keys.
     */
    private static final String kDHr = "kDHr";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with DSS keys.
     */
    private static final String kDHd = "kDHd";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with RSA or DSS keys.
     */
    private static final String kDH = "kDH";
    /**
     * Cipher suites using fixed ECDH key agreement signed by CAs with RSA keys.
     */
    private static final String kECDHr = "kECDHr";
    /**
     * Cipher suites using fixed ECDH key agreement signed by CAs with ECDSA keys.
     */
    private static final String kECDHe = "kECDHe";
    /**
     * Cipher suites using fixed ECDH key agreement signed by CAs with RSA and ECDSA keys or either respectively.
     */
    private static final String kECDH = "kECDH";
    /**
     * Cipher suites using ephemeral ECDH key agreement, including anonymous cipher suites.
     */
    private static final String kEECDH = "kEECDH";
    /**
     * Cipher suites using ephemeral ECDH key agreement, excluding anonymous cipher suites.
     * Same as "kEECDH:-AECDH"
     */
    private static final String EECDH = "EECDH";
    /**
     * Cipher suitesusing ECDH key exchange, including anonymous, ephemeral and fixed ECDH.
     */
    private static final String ECDH = "ECDH";
    /**
     * Cipher suites using ephemeral ECDH key agreement, including anonymous cipher suites.
     */
    private static final String kECDHE = "kECDHE";
    /**
     * Cipher suites using authenticated ephemeral ECDH key agreement
     */
    private static final String ECDHE = "ECDHE";
    /**
     * Cipher suites using authenticated ephemeral ECDH key agreement
     */
    private static final String EECDHE = "EECDHE";
    /**
     * Anonymous Elliptic Curve Diffie Hellman cipher suites.
     */
    private static final String AECDH = "AECDH";
    /**
     * Cipher suites using DSS authentication, i.e. the certificates carry DSS keys.
     */
    private static final String aDSS = "aDSS";
    /**
     * Cipher suites effectively using DH authentication, i.e. the certificates carry DH keys.
     */
    private static final String aDH = "aDH";
    /**
     * Cipher suites effectively using ECDH authentication, i.e. the certificates carry ECDH keys.
     */
    private static final String aECDH = "aECDH";
    /**
     * Cipher suites effectively using ECDSA authentication, i.e. the certificates carry ECDSA keys.
     */
    private static final String aECDSA = "aECDSA";
    /**
     * Cipher suites effectively using ECDSA authentication, i.e. the certificates carry ECDSA keys.
     */
    private static final String ECDSA = "ECDSA";
    /**
     * Ciphers suites using FORTEZZA key exchange algorithms.
     */
    private static final String kFZA = "kFZA";
    /**
     * Ciphers suites using FORTEZZA authentication algorithms.
     */
    private static final String aFZA = "aFZA";
    /**
     * Ciphers suites using FORTEZZA encryption algorithms.
     */
    private static final String eFZA = "eFZA";
    /**
     * Ciphers suites using all FORTEZZA algorithms.
     */
    private static final String FZA = "FZA";
    /**
     * Cipher suites using DH, including anonymous DH, ephemeral DH and fixed DH.
     */
    private static final String DH = "DH";
    /**
     * Anonymous DH cipher suites.
     */
    private static final String ADH = "ADH";
    /**
     * Cipher suites using 128 bit AES.
     */
    private static final String AES128 = "AES128";
    /**
     * Cipher suites using 256 bit AE.
     */
    private static final String AES256 = "AES256";
    /**
     * Cipher suites using either 128 or 256 bit AES.
     */
    private static final String AES = "AES";
    /**
     * AES in Galois Counter Mode (GCM): these cipher suites are only supported in TLS v1.2.
     */
    private static final String AESGCM = "AESGCM";
    /**
     * Cipher suites using 128 bit CAMELLIA.
     */
    private static final String CAMELLIA128 = "CAMELLIA128";
    /**
     * Cipher suites using 256 bit CAMELLIA.
     */
    private static final String CAMELLIA256 = "CAMELLIA256";
    /**
     * Cipher suites using either 128 or 256 bit CAMELLIA.
     */
    private static final String CAMELLIA = "CAMELLIA";
    /**
     * Cipher suites using triple DES.
     */
    private static final String TRIPLE_DES = "3DES";
    /**
     * Cipher suites using DES (not triple DES).
     */
    private static final String DES = "DES";
    /**
     * Cipher suites using RC4.
     */
    private static final String RC4 = "RC4";
    /**
     * Cipher suites using RC2.
     */
    private static final String RC2 = "RC2";
    /**
     * Cipher suites using IDEA.
     */
    private static final String IDEA = "IDEA";
    /**
     * Cipher suites using SEED.
     */
    private static final String SEED = "SEED";
    /**
     * Cipher suites using MD5.
     */
    private static final String MD5 = "MD5";
    /**
     * Cipher suites using SHA1.
     */
    private static final String SHA1 = "SHA1";
    /**
     * Cipher suites using SHA1.
     */
    private static final String SHA = "SHA";
    /**
     * Cipher suites using SHA256.
     */
    private static final String SHA256 = "SHA256";
    /**
     * Cipher suites using SHA384.
     */
    private static final String SHA384 = "SHA384";
    /**
     * Cipher suites using KRB5.
     */
    private static final String KRB5 = "KRB5";
    /**
     * Cipher suites using GOST R 34.10 (either 2001 or 94) for authentication.
     */
    private static final String aGOST = "aGOST";
    /**
     * Cipher suites using GOST R 34.10-2001 for authentication.
     */
    private static final String aGOST01 = "aGOST01";
    /**
     * Cipher suites using GOST R 34.10-94 authentication (note that R 34.10-94 standard has been expired so use GOST R
     * 34.10-2001)
     */
    private static final String aGOST94 = "aGOST94";
    /**
     * Cipher suites using using VKO 34.10 key exchange, specified in the RFC 4357.
     */
    private static final String kGOST = "kGOST";
    /**
     * Cipher suites, using HMAC based on GOST R 34.11-94.
     */
    private static final String GOST94 = "GOST94";
    /**
     * Cipher suites using GOST 28147-89 MAC instead of HMAC.
     */
    private static final String GOST89MAC = "GOST89MAC";
    /**
     * Cipher suites using SRP authentication, specified in the RFC 5054.
     */
    private static final String aSRP = "aSRP";
    /**
     * Cipher suites using SRP key exchange, specified in the RFC 5054.
     */
    private static final String kSRP = "kSRP";
    /**
     * Same as kSRP
     */
    private static final String SRP = "SRP";
    /**
     * Cipher suites using pre-shared keys (PSK).
     */
    private static final String PSK = "PSK";

    private static final String DEFAULT = "DEFAULT";
    private static final String COMPLEMENTOFDEFAULT = "COMPLEMENTOFDEFAULT";

    private static final String ALL = "ALL";
    private static final String COMPLEMENTOFALL = "COMPLEMENTOFALL";

    private static final Map<String,String> jsseToOpenSSL = new HashMap<>();

    private static final void init() {

        for (Cipher cipher : Cipher.values()) {
            String alias = cipher.getOpenSSLAlias();
            if (aliases.containsKey(alias)) {
                aliases.get(alias).add(cipher);
            } else {
                List<Cipher> list = new ArrayList<>();
                list.add(cipher);
                aliases.put(alias, list);
            }
            aliases.put(cipher.name(), Collections.singletonList(cipher));

            for (String openSSlAltName : cipher.getOpenSSLAltNames()) {
                if (aliases.containsKey(openSSlAltName)) {
                    aliases.get(openSSlAltName).add(cipher);
                } else {
                    List<Cipher> list = new ArrayList<>();
                    list.add(cipher);
                    aliases.put(openSSlAltName, list);
                }

            }

            jsseToOpenSSL.put(cipher.name(), cipher.getOpenSSLAlias());
            Set<String> jsseNames = cipher.getJsseNames();
            for (String jsseName : jsseNames) {
                jsseToOpenSSL.put(jsseName, cipher.getOpenSSLAlias());
            }
        }
        List<Cipher> allCiphersList = Arrays.asList(Cipher.values());
        Collections.reverse(allCiphersList);
        LinkedHashSet<Cipher> allCiphers = defaultSort(new LinkedHashSet<>(allCiphersList));
        addListAlias(eNULL, filterByEncryption(allCiphers, Collections.singleton(Encryption.eNULL)));
        LinkedHashSet<Cipher> all = new LinkedHashSet<>(allCiphers);
        remove(all, eNULL);
        addListAlias(ALL, all);
        addListAlias(HIGH, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.HIGH)));
        addListAlias(MEDIUM, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.MEDIUM)));
        addListAlias(LOW, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.LOW)));
        addListAlias(EXPORT, filterByEncryptionLevel(allCiphers, new HashSet<>(Arrays.asList(EncryptionLevel.EXP40, EncryptionLevel.EXP56))));
        aliases.put("EXP", aliases.get(EXPORT));
        addListAlias(EXPORT40, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.EXP40)));
        addListAlias(EXPORT56, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.EXP56)));
        aliases.put("NULL", aliases.get(eNULL));
        aliases.put(COMPLEMENTOFALL, aliases.get(eNULL));
        addListAlias(aNULL, filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(kRSA, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.RSA)));
        addListAlias(aRSA, filterByAuthentication(allCiphers, Collections.singleton(Authentication.RSA)));
        // Despite what the docs say, RSA is equivalent to kRSA
        aliases.put(RSA, aliases.get(kRSA));
        addListAlias(kEDH, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH)));
        addListAlias(kDHE, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH)));
        Set<Cipher> edh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH));
        edh.removeAll(filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(EDH, edh);
        addListAlias(DHE, edh);
        addListAlias(kDHr, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.DHr)));
        addListAlias(kDHd, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.DHd)));
        addListAlias(kDH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.DHr, KeyExchange.DHd))));

        addListAlias(kECDHr, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.ECDHr)));
        addListAlias(kECDHe, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.ECDHe)));
        addListAlias(kECDH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.ECDHe, KeyExchange.ECDHr))));
        addListAlias(ECDH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.ECDHe, KeyExchange.ECDHr, KeyExchange.EECDH))));
        addListAlias(kECDHE, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.ECDHe)));
        aliases.put(ECDHE, aliases.get(kECDHE));
        addListAlias(kEECDH, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH)));
        aliases.put(EECDHE, aliases.get(kEECDH));
        Set<Cipher> eecdh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH));
        eecdh.removeAll(filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(EECDH, eecdh);
        addListAlias(aDSS, filterByAuthentication(allCiphers, Collections.singleton(Authentication.DSS)));
        aliases.put("DSS", aliases.get(aDSS));
        addListAlias(aDH, filterByAuthentication(allCiphers, Collections.singleton(Authentication.DH)));
        Set<Cipher> aecdh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH));
        addListAlias(AECDH, filterByAuthentication(aecdh, Collections.singleton(Authentication.aNULL)));
        addListAlias(aECDH, filterByAuthentication(allCiphers, Collections.singleton(Authentication.ECDH)));
        addListAlias(ECDSA, filterByAuthentication(allCiphers, Collections.singleton(Authentication.ECDSA)));
        aliases.put(aECDSA, aliases.get(ECDSA));
        addListAlias(kFZA, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.FZA)));
        addListAlias(aFZA, filterByAuthentication(allCiphers, Collections.singleton(Authentication.FZA)));
        addListAlias(eFZA, filterByEncryption(allCiphers, Collections.singleton(Encryption.FZA)));
        addListAlias(FZA, filter(allCiphers, null, Collections.singleton(KeyExchange.FZA), Collections.singleton(Authentication.FZA), Collections.singleton(Encryption.FZA), null, null));
        addListAlias(Constants.SSL_PROTO_TLSv1_2, filterByProtocol(allCiphers, Collections.singleton(Protocol.TLSv1_2)));
        addListAlias(Constants.SSL_PROTO_TLSv1_1, filterByProtocol(allCiphers, Collections.singleton(Protocol.SSLv3)));
        addListAlias(Constants.SSL_PROTO_TLSv1, filterByProtocol(allCiphers, new HashSet<>(Arrays.asList(Protocol.TLSv1, Protocol.SSLv3))));
        aliases.put(Constants.SSL_PROTO_SSLv3, aliases.get(Constants.SSL_PROTO_TLSv1));
        addListAlias(Constants.SSL_PROTO_SSLv2, filterByProtocol(allCiphers, Collections.singleton(Protocol.SSLv2)));
        addListAlias(DH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.DHr, KeyExchange.DHd, KeyExchange.EDH))));
        Set<Cipher> adh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH));
        adh.retainAll(filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(ADH, adh);
        addListAlias(AES128, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128, Encryption.AES128CCM, Encryption.AES128CCM8, Encryption.AES128GCM))));
        addListAlias(AES256, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES256, Encryption.AES256CCM, Encryption.AES256CCM8, Encryption.AES256GCM))));
        addListAlias(AES, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128, Encryption.AES128CCM, Encryption.AES128CCM8, Encryption.AES128GCM, Encryption.AES256, Encryption.AES256CCM, Encryption.AES256CCM8, Encryption.AES256GCM))));
        addListAlias(AESGCM, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128GCM, Encryption.AES256GCM))));
        addListAlias(CAMELLIA, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.CAMELLIA128, Encryption.CAMELLIA256))));
        addListAlias(CAMELLIA128, filterByEncryption(allCiphers, Collections.singleton(Encryption.CAMELLIA128)));
        addListAlias(CAMELLIA256, filterByEncryption(allCiphers, Collections.singleton(Encryption.CAMELLIA256)));
        addListAlias(TRIPLE_DES, filterByEncryption(allCiphers, Collections.singleton(Encryption.TRIPLE_DES)));
        addListAlias(DES, filterByEncryption(allCiphers, Collections.singleton(Encryption.DES)));
        addListAlias(RC4, filterByEncryption(allCiphers, Collections.singleton(Encryption.RC4)));
        addListAlias(RC2, filterByEncryption(allCiphers, Collections.singleton(Encryption.RC2)));
        addListAlias(IDEA, filterByEncryption(allCiphers, Collections.singleton(Encryption.IDEA)));
        addListAlias(SEED, filterByEncryption(allCiphers, Collections.singleton(Encryption.SEED)));
        addListAlias(MD5, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.MD5)));
        addListAlias(SHA1, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.SHA1)));
        aliases.put(SHA, aliases.get(SHA1));
        addListAlias(SHA256, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.SHA256)));
        addListAlias(SHA384, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.SHA384)));
        addListAlias(aGOST, filterByAuthentication(allCiphers, new HashSet<>(Arrays.asList(Authentication.GOST01, Authentication.GOST94))));
        addListAlias(aGOST01, filterByAuthentication(allCiphers, Collections.singleton(Authentication.GOST01)));
        addListAlias(aGOST94, filterByAuthentication(allCiphers, Collections.singleton(Authentication.GOST94)));
        addListAlias(kGOST, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.GOST)));
        addListAlias(GOST94, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.GOST94)));
        addListAlias(GOST89MAC, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.GOST89MAC)));
        addListAlias(PSK, filter(allCiphers, null, new HashSet<>(Arrays.asList(KeyExchange.PSK, KeyExchange.RSAPSK, KeyExchange.DHEPSK, KeyExchange.ECDHEPSK)), Collections.singleton(Authentication.PSK), null, null, null));
        addListAlias(KRB5, filter(allCiphers, null, Collections.singleton(KeyExchange.KRB5), Collections.singleton(Authentication.KRB5), null, null, null));
        addListAlias(aSRP, filterByAuthentication(allCiphers, Collections.singleton(Authentication.SRP)));
        addListAlias(kSRP, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.SRP)));
        addListAlias(SRP, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.SRP)));
        initialized = true;
        // Despite what the OpenSSL docs say, DEFAULT also excludes SSLv2
        addListAlias(DEFAULT, parse("ALL:!EXPORT:!eNULL:!aNULL:!SSLv2:!DES:!RC2:!RC4"));
        // COMPLEMENTOFDEFAULT is also not exactly as defined by the docs
        Set<Cipher> complementOfDefault = filterByKeyExchange(all, new HashSet<>(Arrays.asList(KeyExchange.EDH,KeyExchange.EECDH)));
        complementOfDefault = filterByAuthentication(complementOfDefault, Collections.singleton(Authentication.aNULL));
        complementOfDefault.removeAll(aliases.get(eNULL));
        complementOfDefault.addAll(aliases.get(Constants.SSL_PROTO_SSLv2));
        complementOfDefault.addAll(aliases.get(EXPORT));
        complementOfDefault.addAll(aliases.get(DES));
        complementOfDefault.addAll(aliases.get(RC2));
        complementOfDefault.addAll(aliases.get(RC4));
        addListAlias(COMPLEMENTOFDEFAULT, complementOfDefault);
    }

    static void addListAlias(String alias, Set<Cipher> ciphers) {
        aliases.put(alias, new ArrayList<>(ciphers));
    }

    static void moveToEnd(final LinkedHashSet<Cipher> ciphers, final String alias) {
        moveToEnd(ciphers, aliases.get(alias));
    }

    static void moveToEnd(final LinkedHashSet<Cipher> ciphers, final Collection<Cipher> toBeMovedCiphers) {
        List<Cipher> movedCiphers = new ArrayList<>(toBeMovedCiphers);
        movedCiphers.retainAll(ciphers);
        ciphers.removeAll(movedCiphers);
        ciphers.addAll(movedCiphers);
    }

    static void moveToStart(final LinkedHashSet<Cipher> ciphers, final Collection<Cipher> toBeMovedCiphers) {
        List<Cipher> movedCiphers = new ArrayList<>(toBeMovedCiphers);
        List<Cipher> originalCiphers = new ArrayList<>(ciphers);
        movedCiphers.retainAll(ciphers);
        ciphers.clear();
        ciphers.addAll(movedCiphers);
        ciphers.addAll(originalCiphers);
    }

    static void add(final LinkedHashSet<Cipher> ciphers, final String alias) {
        ciphers.addAll(aliases.get(alias));
    }

    static void remove(final LinkedHashSet<Cipher> ciphers, final String alias) {
        ciphers.removeAll(aliases.get(alias));
    }

    static LinkedHashSet<Cipher> strengthSort(final LinkedHashSet<Cipher> ciphers) {
        /*
         * This routine sorts the ciphers with descending strength. The sorting
         * must keep the pre-sorted sequence, so we apply the normal sorting
         * routine as '+' movement to the end of the list.
         */
        Set<Integer> keySizes = new HashSet<>();
        for (Cipher cipher : ciphers) {
            keySizes.add(Integer.valueOf(cipher.getStrength_bits()));
        }
        List<Integer> strength_bits = new ArrayList<>(keySizes);
        Collections.sort(strength_bits);
        Collections.reverse(strength_bits);
        final LinkedHashSet<Cipher> result = new LinkedHashSet<>(ciphers);
        for (int strength : strength_bits) {
            moveToEnd(result, filterByStrengthBits(ciphers, strength));
        }
        return result;
    }

    static LinkedHashSet<Cipher> defaultSort(final LinkedHashSet<Cipher> ciphers) {
        final LinkedHashSet<Cipher> result = new LinkedHashSet<>(ciphers.size());
        /* Now arrange all ciphers by preference: */

        /* Everything else being equal, prefer ephemeral ECDH over other key exchange mechanisms */
        result.addAll(filterByKeyExchange(ciphers, Collections.singleton(KeyExchange.EECDH)));
        /* AES is our preferred symmetric cipher */
        moveToStart(result, filterByEncryption(result, new HashSet<>(Arrays.asList(Encryption.AES128, Encryption.AES128GCM,
                Encryption.AES256, Encryption.AES256GCM))));
        result.addAll(filterByEncryption(ciphers, new HashSet<>(Arrays.asList(Encryption.AES128, Encryption.AES128GCM,
                Encryption.AES256, Encryption.AES256GCM))));
        /* Temporarily enable everything else for sorting */
        result.addAll(ciphers);

        /* Low priority for SSLv2 */
        moveToEnd(result, filterByProtocol(result, Collections.singleton(Protocol.SSLv2)));

        /* Low priority for MD5 */
        moveToEnd(result, filterByMessageDigest(result, Collections.singleton(MessageDigest.MD5)));

        /* Move anonymous ciphers to the end.  Usually, these will remain disabled.
         * (For applications that allow them, they aren't too bad, but we prefer
         * authenticated ciphers.) */
        moveToEnd(result, filterByAuthentication(result, Collections.singleton(Authentication.aNULL)));

        /* Move ciphers without forward secrecy to the end */
        moveToEnd(result, filterByAuthentication(result, Collections.singleton(Authentication.ECDH)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.RSA)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.PSK)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.KRB5)));
        /* RC4 is sort-of broken -- move the the end */
        moveToEnd(result, filterByEncryption(result, Collections.singleton(Encryption.RC4)));
        return strengthSort(result);
    }

    static Set<Cipher> filterByStrengthBits(Set<Cipher> ciphers, int strength_bits) {
        Set<Cipher> result = new LinkedHashSet<>(ciphers.size());
        for (Cipher cipher : ciphers) {
            if (cipher.getStrength_bits() == strength_bits) {
                result.add(cipher);
            }
        }
        return result;
    }

    static Set<Cipher> filterByProtocol(Set<Cipher> ciphers, Set<Protocol> protocol) {
        return filter(ciphers, protocol, null, null, null, null, null);
    }

    static Set<Cipher> filterByKeyExchange(Set<Cipher> ciphers, Set<KeyExchange> kx) {
        return filter(ciphers, null, kx, null, null, null, null);
    }

    static Set<Cipher> filterByAuthentication(Set<Cipher> ciphers, Set<Authentication> au) {
        return filter(ciphers, null, null, au, null, null, null);
    }

    static Set<Cipher> filterByEncryption(Set<Cipher> ciphers, Set<Encryption> enc) {
        return filter(ciphers, null, null, null, enc, null, null);
    }

    static Set<Cipher> filterByEncryptionLevel(Set<Cipher> ciphers, Set<EncryptionLevel> level) {
        return filter(ciphers, null, null, null, null, level, null);
    }

    static Set<Cipher> filterByMessageDigest(Set<Cipher> ciphers, Set<MessageDigest> mac) {
        return filter(ciphers, null, null, null, null, null, mac);
    }

    static Set<Cipher> filter(Set<Cipher> ciphers, Set<Protocol> protocol, Set<KeyExchange> kx,
            Set<Authentication> au, Set<Encryption> enc, Set<EncryptionLevel> level, Set<MessageDigest> mac) {
        Set<Cipher> result = new LinkedHashSet<>(ciphers.size());
        for (Cipher cipher : ciphers) {
            if (protocol != null && protocol.contains(cipher.getProtocol())) {
                result.add(cipher);
            }
            if (kx != null && kx.contains(cipher.getKx())) {
                result.add(cipher);
            }
            if (au != null && au.contains(cipher.getAu())) {
                result.add(cipher);
            }
            if (enc != null && enc.contains(cipher.getEnc())) {
                result.add(cipher);
            }
            if (level != null && level.contains(cipher.getLevel())) {
                result.add(cipher);
            }
            if (mac != null && mac.contains(cipher.getMac())) {
                result.add(cipher);
            }
        }
        return result;
    }

    public static LinkedHashSet<Cipher> parse(String expression) {
        if (!initialized) {
            init();
        }
        String[] elements = expression.split(SEPARATOR);
        LinkedHashSet<Cipher> ciphers = new LinkedHashSet<>();
        Set<Cipher> removedCiphers = new HashSet<>();
        for (String element : elements) {
            if (element.startsWith(DELETE)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    remove(ciphers, alias);
                }
            } else if (element.startsWith(EXCLUDE)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    removedCiphers.addAll(aliases.get(alias));
                } else {
                    log.warn(sm.getString("jsse.openssl.unknownElement", alias));
                }
            } else if (element.startsWith(TO_END)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    moveToEnd(ciphers, alias);
                }
            } else if ("@STRENGTH".equals(element)) {
                strengthSort(ciphers);
                break;
            } else if (aliases.containsKey(element)) {
                add(ciphers, element);
            } else if (element.contains(AND)) {
                String[] intersections = element.split("\\" + AND);
                if(intersections.length > 0 && aliases.containsKey(intersections[0])) {
                    List<Cipher> result = new ArrayList<>(aliases.get(intersections[0]));
                    for(int i = 1; i < intersections.length; i++) {
                        if(aliases.containsKey(intersections[i])) {
                            result.retainAll(aliases.get(intersections[i]));
                        }
                    }
                     ciphers.addAll(result);
                }
            }
        }
        ciphers.removeAll(removedCiphers);
        return defaultSort(ciphers);
    }

    public static List<String> convertForJSSE(Collection<Cipher> ciphers) {
        List<String> result = new ArrayList<>(ciphers.size());
        for (Cipher cipher : ciphers) {
            result.addAll(cipher.getJsseNames());
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("jsse.openssl.effectiveCiphers", displayResult(ciphers, true, ",")));
        }
        return result;
    }

    /**
     * Parse the specified expression according to the OpenSSL syntax and returns a list of standard cipher names.
     *
     * @param expression the openssl expression to define a list of cipher.
     * @return the corresponding list of ciphers.
     */
    public static List<String> parseExpression(String expression) {
        return convertForJSSE(parse(expression));
    }

    public static String jsseToOpenSSL(String cipher) {
        if (!initialized) {
            init();
        }
        return jsseToOpenSSL.get(cipher);
    }

    static String displayResult(Collection<Cipher> ciphers, boolean useJSSEFormat, String separator) {
        if (ciphers.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(ciphers.size() * 16);
        for (Cipher cipher : ciphers) {
            if (useJSSEFormat) {
                for (String name : cipher.getJsseNames()) {
                    builder.append(name);
                    builder.append(separator);
                }
            } else {
                builder.append(cipher.getOpenSSLAlias());
            }
            builder.append(separator);
        }
        return builder.toString().substring(0, builder.length() - 1);
    }
}
