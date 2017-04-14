#!/usr/bin/env bash
set -e
# https://docs.docker.com/engine/articles/https/ + non-interactive options
rm -rf keys/*
pushd "keys"

    ## CA
    openssl req \
        -nodes \
        -new \
        -x509 \
        -extensions v3_ca \
        -keyout ca-key.pem \
        -days 365 \
        -subj "/C=US/ST=Denial/L=Springfield/O=Dis/CN=www.example.com"
	-out ca.pem

    ## server
    openssl req \
        -nodes \
        -newkey rsa \
        -keyout server-key.pem \
        -out server.csr \
        -subj "/CN=192.168.99.100"

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

    ## client
    openssl req \
        -subj '/CN=client' \
        -nodes \
        -newkey rsa \
        -keyout key.pem \
        -out client.csr

    echo "extendedKeyUsage = clientAuth" > extfile-client.cnf

    openssl x509 \
        -req \
        -days 365 \
        -in client.csr \
        -CA ca.pem \
        -CAkey ca-key.pem \
        -CAcreateserial \
        -extfile extfile-client.cnf \
        -out cert.pem

    rm -rfv client.csr server.csr

    chmod -v 0400 ca-key.pem key.pem server-key.pem
    chmod -v 0444 ca.pem server-cert.pem cert.pem
popd
