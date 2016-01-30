#!/usr/bin/env bash
# https://docs.docker.com/engine/articles/https/ + non-interactive options

openssl genrsa \
    -aes256 \
    -passout pass:foobar \
    -out ca-key.pem 4096

openssl req \
    -new \
    -x509 \
    -passin pass:foobar \
    -subj "/C=US/ST=Denial/L=Springfield/O=Dis/CN=www.example.com" \
    -days 365 \
    -key ca-key.pem \
    -sha256 \
    -out ca.pem

openssl genrsa -out server-key.pem 4096
openssl req \
    -subj "/CN=192.168.99.100" \
    -sha256 \
    -new \
    -key server-key.pem \
    -out server.csr

echo subjectAltName = IP:192.168.99.100,IP:127.0.0.1 > extfile.cnf

openssl x509 \
    -req \
    -passin pass:foobar \
    -days 365 \
    -sha256 \
    -in server.csr \
    -CA ca.pem \
    -CAkey ca-key.pem \
    -CAcreateserial \
    -out server-cert.pem \
    -extfile extfile.cnf

openssl genrsa -out key.pem 4096
openssl req -subj '/CN=client' -new -key key.pem -out client.csr

echo extendedKeyUsage = clientAuth > extfile.cnf

openssl x509 \
    -req \
    -passin pass:foobar \
    -days 365 \
    -sha256 \
    -in client.csr \
    -CA ca.pem \
    -CAkey ca-key.pem \
    -CAcreateserial \
    -out cert.pem \
    -extfile extfile.cnf

rm -rfv client.csr server.csr

chmod -v 0400 ca-key.pem key.pem server-key.pem
chmod -v 0444 ca.pem server-cert.pem cert.pem
