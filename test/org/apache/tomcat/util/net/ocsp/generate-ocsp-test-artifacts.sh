#!/bin/sh

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Generate OCSP-ready test material for Tomcat integration tests.
#
# Output:
#   ca-cert.pem
#   server-cert.pem
#   server-key.pem
#   trustStore.p12
#   trust-password
#   ocsp-good.der
#   ocsp-revoked.der
#
# Usage: generate-ocsp-test-artifacts.sh
#

PASS="changeit"
WORK_DIR="ocsp-work"

command -v openssl >/dev/null 2>&1 || (printf "OpenSSL not found. Please install it.\r\n" && exit)
command -v keytool >/dev/null 2>&1 || (printf "keytool not found. Please install it.\r\n" && exit)

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"/private "$WORK_DIR"/newcerts "$WORK_DIR"/certs
touch "$WORK_DIR/index"
echo 1000 > "$WORK_DIR/serial"

printf "Writing minimal OpenSSL config..."
cat > "$WORK_DIR/openssl.cnf" <<'EOF'
[ ca ]
default_ca = CA_default

[ CA_default ]
dir               = .
database          = $dir/index
new_certs_dir     = $dir/newcerts
serial            = $dir/serial
default_md        = sha256
policy            = policy_loose
copy_extensions   = copy
private_key       = $dir/private/ca.key.pem
certificate       = $dir/certs/ca-cert.pem

[ policy_loose ]
commonName        = supplied

[ v3_ca ]
basicConstraints = critical,CA:TRUE
keyUsage         = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer

[ v3_server ]
basicConstraints = critical,CA:FALSE
keyUsage         = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
authorityInfoAccess  = OCSP;URI:http://127.0.0.1:8888/ocsp
subjectAltName   = @san
[ san ]
IP.1 = 127.0.0.1
DNS.1 = localhost

[ v3_client ]
basicConstraints = critical,CA:FALSE
keyUsage         = critical,digitalSignature,keyEncipherment
extendedKeyUsage = clientAuth
# Make the AIA field >127 bytes to test CVE-2017-15698
authorityInfoAccess = OCSP;URI:http://127.0.0.1:8889/ocsp/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa

[ v3_ocsp ]
basicConstraints = critical,CA:FALSE
keyUsage         = critical,digitalSignature
extendedKeyUsage = OCSPSigning
EOF
printf "Done.\r\n"

cd "$WORK_DIR" || (printf "Something went wrong.\r\n" && exit)

printf "Generating CA key and certificate...\r\n"
openssl genrsa -out private/ca.key.pem 2048
openssl req -x509 -new -nodes -key private/ca.key.pem -days 3650 -subj "/CN=Test CA" -config openssl.cnf -extensions v3_ca -out certs/ca-cert.pem
printf "Done.\r\n"

printf "Generating server key and certificate...\r\n"
openssl genrsa -out private/server.key.pem 2048
openssl req -new -key private/server.key.pem -out server.csr.pem -subj "/CN=localhost"
openssl ca -batch -config openssl.cnf -extensions v3_server -in server.csr.pem -out certs/server.cert.pem -days 365
printf "Done.\r\n"

printf "Generating OCSP responder key and certificate...\r\n"
openssl genrsa -out private/ocsp.key.pem 2048
openssl req -new -key private/ocsp.key.pem -out ocsp.csr.pem -subj "/CN=Test OCSP Responder"
openssl ca -batch -config openssl.cnf -extensions v3_ocsp -in ocsp.csr.pem -out certs/ocsp.cert.pem -days 365
printf "Done.\r\n"

printf "Building OCSP request for the server certificate...\r\n"
openssl ocsp -issuer certs/ca-cert.pem -cert certs/server.cert.pem -no_nonce -reqout request.der
printf "Done.\r\n"

printf "Answering request with good status (ocsp-good.der)...\r\n"
openssl ocsp -index index -CA certs/ca-cert.pem -rsigner certs/ocsp.cert.pem -rkey private/ocsp.key.pem -no_nonce -ndays 365 -reqin request.der -respout ../ocsp-good.der
printf "Done.\r\n"

printf "Revoking the server certificate in the CA database...\r\n"
openssl ca -config openssl.cnf -revoke certs/server.cert.pem -crl_reason keyCompromise
printf "Done.\r\n"

printf "Answering request with REVOKED status (ocsp-revoked.der)...\r\n"
openssl ocsp -index index -CA certs/ca-cert.pem -rsigner certs/ocsp.cert.pem -rkey private/ocsp.key.pem -no_nonce -ndays 365 -reqin request.der -respout ../ocsp-revoked.der
printf "Done.\r\n"

cp certs/ca-cert.pem ..
cp private/server.key.pem ../server-key.pem
cp certs/server.cert.pem ../server-cert.pem

printf "Creating PKCS12 client's truststore (trustStore.p12) with the CA...\r\n"
rm -f ../trustStore.p12
echo "$PASS" > ../trust-password
keytool -importcert -alias ocsp-ca -file certs/ca-cert.pem -keystore ../trustStore.p12 -storetype PKCS12 -storepass "$PASS" -noprompt
printf "Done.\r\n"

printf "Generating client key and certificate...\r\n"
openssl genrsa -out private/client.key.pem 2048
openssl req -new -key private/client.key.pem -out client.csr.pem -subj "/CN=test-client"
openssl ca -batch -config openssl.cnf -extensions v3_client -in client.csr.pem -out certs/client.cert.pem -days 365
printf "Done.\r\n"

printf "Building OCSP request for the CLIENT certificate...\r\n"
openssl ocsp -issuer certs/ca-cert.pem -cert certs/client.cert.pem -no_nonce -reqout client-request.der
printf "Done.\r\n"

printf "Answering request with good status for client (ocsp-client-good.der)...\r\n"
openssl ocsp -index index -CA certs/ca-cert.pem -rsigner certs/ocsp.cert.pem -rkey private/ocsp.key.pem -no_nonce -ndays 365 -reqin client-request.der -respout ../ocsp-client-good.der
printf "Done.\r\n"

printf "Revoking the client certificate in the CA database...\r\n"
openssl ca -config openssl.cnf -revoke certs/client.cert.pem -crl_reason keyCompromise
printf "Done.\r\n"

printf "Answering request with REVOKED status for client (ocsp-client-revoked.der)...\r\n"
openssl ocsp -index index -CA certs/ca-cert.pem -rsigner certs/ocsp.cert.pem -rkey private/ocsp.key.pem -no_nonce -ndays 365 -reqin client-request.der -respout ../ocsp-client-revoked.der
printf "Done.\r\n"

printf "Creating PKCS12 client keystore for mutual TLS...\r\n"
echo "$PASS" > ../client-password
openssl pkcs12 -export -name ocsp-client -out ../client-keystore.p12 -inkey private/client.key.pem -in certs/client.cert.pem -certfile certs/ca-cert.pem -passout pass:"$PASS"
printf "Done.\r\n"

printf "\r\nOptional verification:\r\n"
printf "  openssl ocsp -respin ocsp-good.der -verify_other ocsp-work/certs/ocsp.cert.pem -CAfile ca-cert.pem\r\n"
printf "  openssl ocsp -respin ocsp-revoked.der -verify_other ocsp-work/certs/ocsp.cert.pem -CAfile ca-cert.pem\r\n"