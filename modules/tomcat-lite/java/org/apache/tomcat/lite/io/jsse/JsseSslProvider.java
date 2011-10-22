/*
 */
package org.apache.tomcat.lite.io.jsse;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.DumpChannel;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;
import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.lite.io.SslProvider;
import org.apache.tomcat.lite.io.WrappedException;
import org.apache.tomcat.lite.io.IOConnector.ConnectedCallback;


public class JsseSslProvider implements SslProvider {

    /**
     * TODO: option to require validation.
     * TODO: remember cert signature. This is needed to support self-signed
     * certs, like those used by the test.
     *
     */
    public static class BasicTrustManager implements X509TrustManager {

        private X509Certificate[] chain;

        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            this.chain = chain;
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            this.chain = chain;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    public static TrustManager[] trustAllCerts = new TrustManager[] {
        new BasicTrustManager() };

    static String[] enabledCiphers;

    static final boolean debug = false;

    IOConnector net;
    private KeyManager[] keyManager;
    SSLContext sslCtx;
    boolean server;
    private TrustManager[] trustManagers;

    public AtomicInteger handshakeCount = new AtomicInteger();
    public AtomicInteger handshakeOk = new AtomicInteger();
    public AtomicInteger handshakeErr = new AtomicInteger();
    public AtomicInteger handshakeTime = new AtomicInteger();

    Executor handshakeExecutor = Executors.newCachedThreadPool();
    static int id = 0;

    public JsseSslProvider() {
    }

    public static void setEnabledCiphers(String[] enabled) {
        enabledCiphers = enabled;
    }

    public void start() {

    }

    SSLContext getSSLContext() {
        if (sslCtx == null) {
            try {
                sslCtx = SSLContext.getInstance("TLS");
                if (trustManagers == null) {
                    trustManagers =
                        new TrustManager[] {new BasicTrustManager()};

                }
                sslCtx.init(keyManager, trustManagers, null);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (KeyManagementException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return sslCtx;
    }

    public IOConnector getNet() {
        if (net == null) {
            getSSLContext();
            net = new SocketConnector();
        }
        return net;
    }

    @Override
    public IOChannel channel(IOChannel net, String host, int port) throws IOException {
      if (debug) {
          net = DumpChannel.wrap("S-ENC-" + id, net);
        }
        SslChannel ch = new SslChannel()
            .setTarget(host, port)
            .setSslContext(getSSLContext())
            .setSslProvider(this);
        net.setHead(ch);
        return ch;
    }

    @Override
    public SslChannel serverChannel(IOChannel net) throws IOException {
        SslChannel ch = new SslChannel()
            .setSslContext(getSSLContext())
            .setSslProvider(this).withServer();
        ch.setSink(net);
        return ch;
    }

    public void acceptor(final ConnectedCallback sc, CharSequence port, Object extra)
            throws IOException {
        getNet().acceptor(new ConnectedCallback() {
            @Override
            public void handleConnected(IOChannel ch) throws IOException {
                IOChannel first = ch;
                if (debug) {
                    first = DumpChannel.wrap("S-ENC-" + id, ch);
                }

                IOChannel sslch = serverChannel(first);
                sslch.setSink(first);
                first.setHead(sslch);

                if (debug) {
                    sslch = DumpChannel.wrap("S-CLR-" + id, sslch);
                    id++;
                }

                sc.handleConnected(sslch);
            }
        }, port, extra);
    }

    public void connect(final String host, final int port, final ConnectedCallback sc)
            throws IOException {
        getNet().connect(host, port, new ConnectedCallback() {

            @Override
            public void handleConnected(IOChannel ch) throws IOException {
                IOChannel first = ch;
                if (debug) {
                    first = DumpChannel.wrap("ENC-" + id, first);
                }

                IOChannel sslch = channel(first, host, port);
//                first.setHead(sslch);

                if (debug) {
                    sslch = DumpChannel.wrap("CLR-" + id, sslch);
                    id++;
                }

                sc.handleConnected(sslch);
            }

        });
    }

    public JsseSslProvider withKeyManager(KeyManager[] kms) {
        this.keyManager = kms;
        return this;
    }

    public JsseSslProvider setKeystoreFile(String file, String pass) throws IOException {
        return setKeystore(new FileInputStream(file), pass);
    }

    public JsseSslProvider setKeystoreResource(String res, String pass) throws IOException {
        return setKeystore(this.getClass().getClassLoader().getResourceAsStream(res),
                pass);
    }

    public JsseSslProvider setKeystore(InputStream file, String pass) {
        char[] passphrase = pass.toCharArray();
        KeyStore ks;
        try {
            String type = KeyStore.getDefaultType();
            System.err.println("Keystore: " + type);
            // Java: JKS
            // Android: BKS
            ks = KeyStore.getInstance(type);
            ks.load(file, passphrase);
            KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, passphrase);

            TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            keyManager = kmf.getKeyManagers();
            trustManagers = tmf.getTrustManagers();
        } catch (KeyStoreException e) {
            // No JKS keystore ?
            // TODO Auto-generated catch block
        }catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CertificateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return this;
    }

    public JsseSslProvider setKeys(X509Certificate cert, PrivateKey privKey) {
        keyManager = new KeyManager[] {
                new TestKeyManager(cert, privKey)
        };
        return this;
    }

    public JsseSslProvider setKeyFiles(String certPem, String keyFile)
            throws IOException {


        return this;
    }

    public JsseSslProvider setKeyRes(String certPem, String keyFile)
            throws IOException {
        setKeys(this.getClass().getClassLoader().getResourceAsStream(certPem),
                this.getClass().getClassLoader().getResourceAsStream(keyFile));
        return this;
    }

    private void setKeys(InputStream certPem,
            InputStream keyDer) throws IOException {
        BBuffer keyB = BBuffer.allocate(2048);
        keyB.readAll(keyDer);
        byte[] key = new byte[keyB.remaining()];
        keyB.getByteBuffer().get(key);

        setKeys(certPem, key);
    }

    public JsseSslProvider setKeys(String certPem, byte[] keyBytes) throws IOException{
        InputStream is = new ByteArrayInputStream(certPem.getBytes());
        return setKeys(is, keyBytes);
    }

    /**
     * Initialize using a PEM certificate and key bytes.
     * ( TODO: base64 dep to set the key as PEM )
     *
     *  openssl genrsa 1024 > host.key
     *  openssl pkcs8 -topk8 -nocrypt -in host.key -inform PEM
     *     -out host.der -outform DER
     *  openssl req -new -x509 -nodes -sha1 -days 365 -key host.key > host.cert
     *
     */
    public JsseSslProvider setKeys(InputStream certPem, byte[] keyBytes) throws IOException{
        // convert key
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keysp = new PKCS8EncodedKeySpec(keyBytes);
            PrivateKey priv = kf.generatePrivate (keysp);

            // Convert cert pem to certificate
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            final X509Certificate cert =  (X509Certificate) cf.generateCertificate(certPem);

            setKeys(cert, priv);
        } catch (Throwable t) {
            throw new WrappedException(t);
        }
        return this;
    }

    public class TestKeyManager extends X509ExtendedKeyManager {
        X509Certificate cert;
        PrivateKey privKey;

        public TestKeyManager(X509Certificate cert2, PrivateKey privKey2) {
            cert = cert2;
            privKey = privKey2;
        }

        public String chooseEngineClientAlias(String[] keyType,
                java.security.Principal[] issuers, javax.net.ssl.SSLEngine engine) {
            return "client";
        }

        public String chooseEngineServerAlias(String keyType,
                java.security.Principal[] issuers, javax.net.ssl.SSLEngine engine) {
            return "server";
        }

        public String chooseClientAlias(String[] keyType,
                                        Principal[] issuers, Socket socket) {
            return "client";
        }

        public String chooseServerAlias(String keyType,
                                        Principal[] issuers, Socket socket) {
            return "server";
        }

        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[] {cert};
        }

        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        public PrivateKey getPrivateKey(String alias) {

            return privKey;
        }

        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return null;
        }
    }

    // TODO: add a mode that trust a defined list of certs, like SSH

    /**
     * Make URLConnection accept all certificates.
     * Use only for testing !
     */
    public static void testModeURLConnection() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, JsseSslProvider.trustAllCerts, null);

            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                    new HostnameVerifier() {

                        @Override
                        public boolean verify(String hostname,
                                SSLSession session) {
                            try {
                                Certificate[] certs = session.getPeerCertificates();
                                // TODO...
                                // see org/apache/http/conn/ssl/AbstractVerifier
                            } catch (SSLPeerUnverifiedException e) {
                                e.printStackTrace();
                            }
                            return true;
                        }

                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Utilities
    public static byte[] getPrivateKeyFromStore(String file, String pass)
            throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        store.load(new FileInputStream(file), pass.toCharArray());
        Key key = store.getKey("tomcat", "changeit".toCharArray());
        PrivateKey pk = (PrivateKey) key;
        byte[] encoded = pk.getEncoded();
        return encoded;
    }

    public static byte[] getCertificateFromStore(String file, String pass)
            throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        store.load(new FileInputStream(file), pass.toCharArray());
        Certificate certificate = store.getCertificate("tomcat");

        return certificate.getEncoded();
    }

    public static KeyPair generateRsaOrDsa(boolean rsa) throws Exception {
        if (rsa) {
            KeyPairGenerator keyPairGen =
                KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(1024);

            RSAKeyGenParameterSpec keySpec = new RSAKeyGenParameterSpec(1024,
                    RSAKeyGenParameterSpec.F0);
            keyPairGen.initialize(keySpec);

            KeyPair rsaKeyPair = keyPairGen.generateKeyPair();

            return rsaKeyPair;
        } else {
            KeyPairGenerator keyPairGen =
                KeyPairGenerator.getInstance("DSA");
            keyPairGen.initialize(1024);

            KeyPair pair = keyPairGen.generateKeyPair();

            return pair;
        }
    }

    /**
     * I know 2 ways to generate certs:
     *  - keytool
     *  - openssl req -x509 -nodes -days 365 \
     *    -newkey rsa:1024 -keyout mycert.pem -out mycert.pem
     *  openssl s_server -accept 9443 -cert mycert.pem -debug -msg -state -www
     */
}