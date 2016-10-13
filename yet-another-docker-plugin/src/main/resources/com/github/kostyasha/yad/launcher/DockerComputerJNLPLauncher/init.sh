#!/usr/bin/env sh

set -uxe

export CONFIG="/tmp/config.sh"
while [ ! -f "$CONFIG" ]; do
    echo "No config, sleeping for 1 second"
    sleep 1
done

echo "Found config file"
source "$CONFIG"
# require:
# $JENKINS_URL
# $COMPUTER_URL
# $COMPUTER_SECRET

if [ -z "$JENKINS_URL" ]; then
    echo "JENKINS_URL is not defined! Exiting."
    exit 1
fi

if [ -z "$COMPUTER_URL" ]; then
    echo "COMPUTER_URL is not defined! Exiting."
    exit 1
fi

if [ -z "$JENKINS_HOME" ]; then
    echo "Remote FS root is not defined! Exiting."
    exit 1
fi

if ! id -u "$JENKINS_USER"; then
    echo "Jenkins user doesn't exist, creating..."
    useradd -d "$JENKINS_HOME" "$JENKINS_USER"
fi

if [ ! -d "$JENKINS_HOME" ]; then
    mkdir -p "$JENKINS_HOME"
    chown "$JENKINS_USER". "$JENKINS_HOME"
fi

cd "$JENKINS_HOME"

if [ "$NO_CERTIFICATE_CHECK" == "true" ]
then
   WGET_OPTIONS=" --no-check-certificate"
   CURL_OPTIONS=" -k"
   NO_SLAVE_CERTIF=" -noCertificateCheck"
else
   WGET_OPTIONS=""
   CURL_OPTIONS=""
   NO_SLAVE_CERTIF=""
fi

# download slave jar
# TODO some caching mechanism with checksums
if [ -x "$(command -v wget)" ]; then
   wget $WGET_OPTIONS "${JENKINS_URL}/jnlpJars/slave.jar" -O "slave.jar"
elif [ -x "$(command -v curl)" ]; then
    curl $CURL_OPTIONS --remote-name "${JENKINS_URL}/jnlpJars/slave.jar"
fi

env # debug

RUN_CMD="java"
if [ -n "$JAVA_OPTS" ] ; then
   RUN_CMD+=" $JAVA_OPTS"
fi
RUN_CMD+=" -jar slave.jar"
RUN_CMD+=" -noReconnect"
RUN_CMD+="$NO_SLAVE_CERTIF"
if [ -n "$SLAVE_OPTS" ] ; then
   RUN_CMD+=" $SLAVE_OPTS"
fi
RUN_CMD+=" -jnlpUrl ${JENKINS_URL}/${COMPUTER_URL}/slave-agent.jnlp"
if [ -n "$COMPUTER_SECRET" ]; then
 RUN_CMD+=" -secret $COMPUTER_SECRET"
fi

if [ "$(id -nu)" != "$JENKINS_USER" ]; then
    if [ -x "$(command -v gosu)" ]; then
        RUN_CMD="gosu $JENKINS_USER $RUN_CMD"
    else
        RUN_CMD="su - $JENKINS_USER -c '$RUN_CMD'"
    fi
fi

eval "$RUN_CMD"
