#!/usr/bin/env sh

set -uxe

export CONFIG="/tmp/config.sh"
while [ ! -f "$CONFIG" ]; do
    echo "No config, sleeping for 1 second"
    sleep 1
done

echo "Found config file"
. "$CONFIG"
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
    if [ -x "$(command -v useradd)" ]; then
        useradd -d "$JENKINS_HOME" "$JENKINS_USER"
    elif [ -x "$(command -v adduser)" ]; then
        adduser -D -h "${JENKINS_HOME}" "$JENKINS_USER"
    else
        echo "Error: can't add user. useradd or assuser didn't found."
    fi
fi

if [ ! -d "$JENKINS_HOME" ]; then
    mkdir -p "$JENKINS_HOME"
    chown "$JENKINS_USER". "$JENKINS_HOME"
fi

cd "$JENKINS_HOME"

if [ "$NO_CERTIFICATE_CHECK" = "true" ]
then
    # busybox has no options
    if wget  --help 2>&1| grep BusyBox ; then
        WGET_OPTIONS=""
    else
        WGET_OPTIONS=" --no-check-certificate"
    fi

    CURL_OPTIONS=" -k"
    NO_SLAVE_CERT=" -noCertificateCheck"
else
    WGET_OPTIONS=""
    CURL_OPTIONS=""
    NO_SLAVE_CERT=""
fi

# download slave jar
# TODO some caching mechanism with checksums
if [ -x "$(command -v wget)" ]; then
   wget $WGET_OPTIONS "${JENKINS_URL}/jnlpJars/slave.jar" -O "slave.jar"
elif [ -x "$(command -v curl)" ]; then
    curl $CURL_OPTIONS --remote-name "${JENKINS_URL}/jnlpJars/slave.jar"
else
    echo "Error: no wget or curl for fetching slave.jar."
fi

env # debug

RUN_CMD="java"

if [ -n "$JAVA_OPTS" ] ; then
   RUN_CMD="$RUN_CMD $JAVA_OPTS"
fi

RUN_CMD="$RUN_CMD -jar slave.jar"

if [ "$RECONNECT_SLAVE" != "true" ]; then
    RUN_CMD="$RUN_CMD -noReconnect"
fi

RUN_CMD="$RUN_CMD$NO_SLAVE_CERT"

if [ -n "$SLAVE_OPTS" ] ; then
   RUN_CMD="$RUN_CMD $SLAVE_OPTS"
fi

RUN_CMD="$RUN_CMD -jnlpUrl ${JENKINS_URL}/${COMPUTER_URL}/slave-agent.jnlp"
if [ -n "$COMPUTER_SECRET" ]; then
 RUN_CMD="$RUN_CMD -secret $COMPUTER_SECRET"
fi

if [ "$(id -nu)" != "$JENKINS_USER" ]; then
    if [ -x "$(command -v gosu)" ]; then
        RUN_CMD="gosu $JENKINS_USER $RUN_CMD"
    else
        RUN_CMD="su - $JENKINS_USER -c '$RUN_CMD'"
    fi
fi

eval "$RUN_CMD"
