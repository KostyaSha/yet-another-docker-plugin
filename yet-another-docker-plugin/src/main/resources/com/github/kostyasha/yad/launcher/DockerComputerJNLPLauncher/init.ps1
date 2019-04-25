$progressPreference = 'silentlyContinue'  # no progress bars

$CONFIG="c:\config.ps1"
while (-not (Test-Path $CONFIG)) {
   Write-Output "No config, sleeping for 1 second"
   sleep 1
}

Write-Output "Found config file $CONFIG"
. "$CONFIG"

if (!$JENKINS_URL) {
   Write-Output "JENKINS_URL is not defined! Exiting."
   exit 1
}

if (!$COMPUTER_URL) {
   Write-Output "COMPUTER_URL is not defined! Exiting."
   exit 1
}

if (-not (Test-Path $JENKINS_HOME)) {
   New-Item -ItemType directory -Path c:\Temp
   $JENKINS_HOME="c:\Temp"
}

Write-Output "###################################"
Get-Item Env:
Write-Output "###################################"
Write-Output "JENKINS_URL = $JENKINS_URL"
Write-Output "JENKINS_USER = $JENKINS_USER"
Write-Output "JENKINS_HOME = $JENKINS_HOME"
Write-Output "COMPUTER_URL = $COMPUTER_URL"
Write-Output "COMPUTER_SECRET = $COMPUTER_SECRET"
Write-Output "NO_CERTIFICATE_CHECK = $NO_CERTIFICATE_CHECK"
Write-Output "JAVA_OPTS = $JAVA_OPTS"
Write-Output "SLAVE_OPTS = $SLAVE_OPTS"
Write-Output "###################################"

$RUN_CMD="java"

if ($JAVA_OPTS) {
   $RUN_CMD="$RUN_CMD $JAVA_OPTS"
}

$RUN_CMD="$RUN_CMD -jar $JENKINS_HOME/slave.jar"

if ($NO_RECONNECT_SLAVE -eq "true") {
   $RUN_CMD="$RUN_CMD -noReconnect"
}

if ($NO_CERTIFICATE_CHECK -eq "true") {
   $RUN_CMD="$RUN_CMD -noCertificateCheck"
}

if ($SLAVE_OPTS) {
   $RUN_CMD="$RUN_CMD $SLAVE_OPTS"
}

$RUN_CMD="$RUN_CMD -jnlpUrl $JENKINS_URL/$COMPUTER_URL/slave-agent.jnlp"

if ($COMPUTER_SECRET) {
   $RUN_CMD="$RUN_CMD -secret $COMPUTER_SECRET"
}

cd $JENKINS_HOME

try {
   Write-Output "Invoke-WebRequest -Uri $JENKINS_URL/jnlpJars/slave.jar -Outfile $JENKINS_HOME/slave.jar"
   [Net.ServicePointManager]::SecurityProtocol = "tls12, tls11, tls"
   Invoke-WebRequest -Uri "$JENKINS_URL/jnlpJars/slave.jar" -Outfile "$JENKINS_HOME/slave.jar"

   Write-Output "$RUN_CMD"
   Invoke-Expression "$RUN_CMD"

   exit 0
} catch {
   exit 1
}
