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

sudo -E apt-get update
sudo -E apt-get install -q -y wget
sudo -E apt-get -q -y --purge remove docker-engine || :
sudo -E apt-cache policy docker-engine

./.travis/get-docker-com.sh
#mkdir "${HOME}/.cache" || :
#pushd "${HOME}/.cache"
# wget -N "https://apt.dockerproject.org/repo/pool/main/d/docker-engine/docker-engine_${DOCKER_VERSION}_amd64.deb"
# sudo apt-get -f install
# sudo dpkg -i "$(ls *${DOCKER_VERSION}*)"
#popd
#rm -f "src/test/resources/logback.xml"

pushd "yet-another-docker-its"
    mv "src/test/resources/travis-logback.xml" "src/test/resources/logback.xml"
popd

echo 'DOCKER_OPTS="-H=unix:///var/run/docker.sock -H=tcp://0.0.0.0:2375"' | sudo tee -a /etc/default/docker
sudo -E restart docker
sleep 10
docker version
docker info
ip a
ip r ls
docker network inspect bridge
docker network inspect --format='{{(index .IPAM.Config 0).Gateway }}' bridge


cat <<EOF > "${HOME}/.docker-java.properties"
DOCKER_TLS_VERIFY=""
DOCKER_HOST=tcp://$(ip r | grep default | awk '{ print $3 }'):2375

EOF

set +u

