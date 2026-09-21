# generate big cert chain - see bug 70236

set -eu

DAYS=7300
SUBJECT_PREFIX="/C=US/ST=CA/L=Springfield/O=Apache Software Foundation/OU=Apache Tomcat"

newkey() {
    openssl genrsa -out "$1.key" 8192 2>/dev/null
}

# Self-signed root CA
newkey root
openssl req -x509 -new -key root.key -out root.crt -days $DAYS -sha256 \
    -subj "$SUBJECT_PREFIX/CN=Apache Tomcat Test Long Chain Root" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"

# Six intermediates, i01 signed by the root, i02 by i01, and so on
PREV=root
for N in 01 02 03 04 05 06; do
    newkey "i$N"
    openssl req -new -key "i$N.key" -out "i$N.csr" \
        -subj "$SUBJECT_PREFIX/CN=Apache Tomcat Test Long Chain Intermediate $N"
    openssl x509 -req -in "i$N.csr" -CA "$PREV.crt" -CAkey "$PREV.key" -CAcreateserial \
        -out "i$N.crt" -days $DAYS -sha256 \
        -extfile <(printf 'basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n')
    PREV="i$N"
done

# Leaf, signed by the last intermediate (i06)
newkey leaf
openssl req -new -key leaf.key -out leaf.csr -subj "$SUBJECT_PREFIX/CN=localhost"
openssl x509 -req -in leaf.csr -CA i06.crt -CAkey i06.key -CAcreateserial \
    -out leaf.crt -days $DAYS -sha256 \
    -extfile <(printf '%s\n' \
        "basicConstraints=critical,CA:FALSE" \
        "keyUsage=critical,digitalSignature,keyEncipherment" \
        "extendedKeyUsage=serverAuth" \
        "subjectAltName=DNS:localhost,IP:127.0.0.1")

# Assemble the outputs used by TesterSupport / TestSsl
openssl x509 -in leaf.crt -text > localhost-rsa-longchain-cert.pem
cp leaf.key localhost-rsa-longchain-key.pem
cat i06.crt i05.crt i04.crt i03.crt i02.crt i01.crt root.crt > localhost-rsa-longchain-chain.pem

echo "Wrote localhost-rsa-longchain-cert.pem, localhost-rsa-longchain-key.pem, localhost-rsa-longchain-chain.pem"
