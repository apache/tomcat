/*
 */
package org.apache.tomcat.lite.io;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
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
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;


public class SslConnector extends IOConnector {

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
    
    public SslConnector() {
    }
    
    public void start() {
        
    }
    
    public SSLContext getSSLContext() {
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
    
    public SslChannel channel(String host, int port) {
        return new SslChannel()
            .setTarget(host, port)
            .setSslContext(getSSLContext())
            .setSslConnector(this);
    }

    public SslChannel serverChannel() {
        return new SslChannel()
            .setSslContext(getSSLContext())
            .setSslConnector(this).withServer();
    }
    
    @Override
    public void acceptor(final ConnectedCallback sc, CharSequence port, Object extra) 
            throws IOException {
        getNet().acceptor(new ConnectedCallback() {
            @Override
            public void handleConnected(IOChannel ch) throws IOException {
                IOChannel first = ch;
                if (debug) {
                    DumpChannel dch = new DumpChannel("S-ENC-" + id );
                    ch.addFilterAfter(dch);
                    first = dch;
                }
                
                IOChannel sslch = serverChannel();
                sslch.setSink(first);
                first.addFilterAfter(sslch);

                if (debug) {
                    DumpChannel dch2 = new DumpChannel("S-CLR-" + id);
                    sslch.addFilterAfter(dch2);
                    sslch = dch2;
                    id++;
                }
                
                sc.handleConnected(sslch);
            }
        }, port, extra);
    }
    
    @Override
    public void connect(final String host, final int port, final ConnectedCallback sc)
            throws IOException {
        getNet().connect(host, port, new ConnectedCallback() {

            @Override
            public void handleConnected(IOChannel ch) throws IOException {
                IOChannel first = ch;
                if (debug) {
                    DumpChannel dch = new DumpChannel("ENC-" + id);
                    ch.addFilterAfter(dch);
                    first = dch;
                }
                
                IOChannel sslch = channel(host, port);
                sslch.setSink(first);
                first.addFilterAfter(sslch);

                if (debug) {
                    DumpChannel dch2 = new DumpChannel("CLR-" + id);
                    sslch.addFilterAfter(dch2);
                    sslch = dch2;
                    id++;
                }
                
                sc.handleConnected(sslch);
            }
            
        });
    }

    public SslConnector withKeyManager(KeyManager[] kms) {
        this.keyManager = kms;
        return this;
    }
    
    public SslConnector setKeysFile(String file, String pass) throws IOException {
        return setKeys(new FileInputStream(file), pass);
    }

    public SslConnector setKeysResource(String res, String pass) throws IOException {
        return setKeys(this.getClass().getClassLoader().getResourceAsStream(res), 
                pass);
    }
    
    public SslConnector setKeys(InputStream file, String pass) {
        char[] passphrase = pass.toCharArray();
        KeyStore ks;
        try {
            ks = KeyStore.getInstance("JKS");
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
    
    public SslConnector setKeys(X509Certificate cert, PrivateKey privKey) {
        keyManager = new KeyManager[] {
                new TestKeyManager(cert, privKey)
        };
        return this;
    }
    
    /**
     * Initialize using a PEM certificate and key bytes.
     * ( TODO: base64 dep to set the key as PEM )
     * 
     * 
     * Key was generated with 
     *   keytool -genkey -alias server -keyalg RSA -storepass changeit
     *   keytool -selfcert -storepass changeit -alias server
     *    
     * Then the bytes printed with printPrivateKey()
     * 
     * I found no way to generate the self-signed keys from jsse 
     * except CLI. 
     * 
     */
    public SslConnector setKeys(String certPem, byte[] keyBytes) throws NoSuchAlgorithmException, InvalidKeySpecException, GeneralSecurityException {
        // convert key 
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keysp = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey priv = kf.generatePrivate (keysp);

        // Convert cert pem to certificate
        InputStream is = new ByteArrayInputStream(certPem.getBytes());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        final X509Certificate cert =  (X509Certificate) cf.generateCertificate(is);
        
        setKeys(cert, priv);
        
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

    public static void fixUrlConnection() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, SslConnector.trustAllCerts, null);
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory());
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