#!/usr/bin/env bash


sudo apt-get install -y -q ca-certificates

echo -n | openssl s_client -connect scan.coverity.com:443 | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' | sudo tee -a /etc/ssl/certs/ca-certificates.crt


if [ "$FAST_BUILD" == true ]; then
    echo "Fast build, skipping docker installations."
    exit 0
fi

set -exu

#docker info
#docker version

ip a
ip r ls
hostname -I

sudo -E apt-get update
sudo -E apt-get install -q -y wget
sudo -E apt-get -q -y --purge remove docker-engine || :
sudo -E apt-cache policy docker-engine

./.travis/get-docker-com.sh

sudo -E stop docker

#mkdir "${HOME}/.cache" || :
#pushd "${HOME}/.cache"
# wget -N "https://apt.dockerproject.org/repo/pool/main/d/docker-engine/docker-engine_${DOCKER_VERSION}_amd64.deb"
# sudo apt-get -f install
# sudo dpkg -i "$(ls *${DOCKER_VERSION}*)"
#popd
#rm -f "src/test/resources/logback.xml"

#export HOST_IP="$(ip r | grep default | awk '{ print $3 }')"
export HOST_IP="$(ip a show dev eth0 | grep "inet\b" | awk '{print $2}' | cut -d/ -f1)"
export HOST_PORT=2376
export KEY_PATH="$(pwd)/keys"

function generateKeys() {
    mkdir "keys" || :
    pushd "keys"

        ## CA
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

        ## server
        openssl genrsa \
            -out server-key.pem 4096

        openssl req \
            -new \
            -sha256 \
            -subj "/CN=${HOST_IP}" \
            -key server-key.pem \
            -out server.csr

        echo "subjectAltName = IP:192.168.99.100,IP:192.168.99.101,IP:127.0.0.1,IP:${HOST_IP}" > extfile.cnf

        openssl x509 \
            -req \
            -passin pass:foobar \
            -days 365 \
            -sha256 \
            -in server.csr \
            -CA ca.pem \
            -CAkey ca-key.pem \
            -CAcreateserial \
            -extfile extfile.cnf \
            -out server-cert.pem

        ## client
        openssl genrsa \
            -out key.pem 4096

        openssl req \
            -subj '/CN=client' \
            -new \
            -key key.pem \
            -out client.csr

        echo "extendedKeyUsage = clientAuth" > extfile-client.cnf

        openssl x509 \
            -req \
            -passin pass:foobar \
            -days 365 \
            -sha256 \
            -in client.csr \
            -CA ca.pem \
            -CAkey ca-key.pem \
            -CAcreateserial \
            -extfile extfile-client.cnf \
            -out cert.pem

        rm -rfv client.csr server.csr

        chmod -v 0440 ca-key.pem key.pem server-key.pem
        chmod -v 0444 ca.pem server-cert.pem cert.pem
    popd

}

generateKeys

TEST_KEYS="yet-another-docker-its/src/test/resources/com/github/kostyasha/it/tests/ShortTLSKeyTest/data_container/keys"

rm -rf "${TEST_KEYS}"/*

cp -ar "${KEY_PATH}"/* "${TEST_KEYS}/"

sudo cat /etc/default/docker

cat << EOF | sudo tee /etc/default/docker
DOCKER_OPTS="\
--dns 8.8.8.8 \
--dns 8.8.4.4 \
-D \
-H=unix:///var/run/docker.sock \
-H=tcp://0.0.0.0:${HOST_PORT}  \
--tlsverify \
--tlscacert=${KEY_PATH}/ca.pem \
--tlscert=${KEY_PATH}/server-cert.pem \
--tlskey=${KEY_PATH}/server-key.pem \
"
EOF

sudo cat /etc/default/docker
sudo bash -c ':> /var/log/upstart/docker.log'

date
sudo -E start docker

tries=20
sleep=5
for i in $(seq 1 $tries); do
    if sudo grep "API listen on" /var/log/upstart/docker.log ; then
        echo "Docker started. Delay $(($i * $sleep))"
        break
    elif [[ $i -ge $tries ]]; then
        echo "Docker didn't start. Exiting!"
        sudo cat /var/log/upstart/docker.log
        exit 1
    else
        echo "Docker didn't start, sleeping for 5 secs..."
        sleep $sleep
    fi
done


sudo ss -antpl

curl -V
curl -v https://${HOST_IP}:${HOST_PORT}/images/json \
  --cert ${KEY_PATH}/cert.pem \
  --key ${KEY_PATH}/key.pem \
  --cacert ${KEY_PATH}/ca.pem

docker version || sudo cat /var/log/upstart/docker.log
docker info

export DOCKER_TLS_VERIFY=1
export DOCKER_CERT_PATH=$(pwd)/keys
export DOCKER_HOST=tcp://${HOST_IP}:${HOST_PORT}

ls -la $DOCKER_CERT_PATH

docker version || sudo cat /var/log/upstart/docker.log
docker info

docker network inspect bridge
docker network inspect --format='{{(index .IPAM.Config 0).Gateway }}' bridge


cat <<EOF > "${HOME}/.docker-java.properties"
DOCKER_TLS_VERIFY=1
DOCKER_HOST=tcp://${HOST_IP}:${HOST_PORT}
DOCKER_CERT_PATH=${KEY_PATH}
EOF

set +u

