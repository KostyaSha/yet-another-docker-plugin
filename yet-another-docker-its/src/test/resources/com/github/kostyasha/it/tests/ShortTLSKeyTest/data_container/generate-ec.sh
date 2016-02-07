#!/usr/bin/env bash

set -e
rm -rf keys/*
pushd "keys"

## CA
#    -param_enc explicit \
openssl ecparam \
    -name secp521r1 \
    -genkey \
    -out ca-key.pem

openssl req \
    -new \
    -x509 \
    -subj "/C=US/ST=Denial/L=Springfield/O=Dis/CN=www.example.com" \
    -key ca-key.pem \
    -out ca.pem

## server
openssl ecparam \
    -name secp521r1 \
    -genkey \
    -out server-key.pem

openssl req \
    -new \
    -sha256 \
    -subj "/CN=192.168.99.100" \
    -key server-key.pem \
    -out server.csr

echo subjectAltName = IP:192.168.99.100,IP:127.0.0.1 > extfile.cnf

openssl x509 \
    -req \
    -days 365 \
    -in server.csr \
    -CA ca.pem \
    -CAkey ca-key.pem \
    -CAcreateserial \
    -extfile extfile.cnf \
    -out server-cert.pem


openssl ecparam \
    -name secp521r1 \
    -genkey \
    -out key.pem

openssl req \
    -subj '/CN=client' \
    -new \
    -key key.pem \
    -out client.csr

echo extendedKeyUsage = clientAuth > extfile.cnf

openssl x509 \
    -req \
    -days 365 \
    -in client.csr \
    -CA ca.pem \
    -CAkey ca-key.pem \
    -CAcreateserial \
    -extfile extfile.cnf \
    -out cert.pem

rm -rfv client.csr server.csr

chmod -v 0400 ca-key.pem key.pem server-key.pem
chmod -v 0444 ca.pem server-cert.pem cert.pem

popd
