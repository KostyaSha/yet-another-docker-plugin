$progressPreference = 'silentlyContinue'  # no progress bars

$configTimeout = 60
$CONFIG="c:\config.ps1"
while (-not (Test-Path $CONFIG)) {
   $configTimeout = $configTimeout - 1
   if ($configTimeout -le 0) {
      Write-Output "No config file found after 60s! Exiting."
      exit 1
   }
   Write-Output "No config, sleeping for 1 second"
   sleep 1
}

function Test-FileLock {
   param (
      [parameter(Mandatory=$true)]
      [string]$Path
   )

   $oFile = New-Object System.IO.FileInfo $Path

   try
   {
      $oStream = $oFile.Open([System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
      if ($oStream)
      {
         $oStream.Close()
      }
      $false
   }
   catch
   {
      # file is locked by a process.
      $true
   }
}

while (Test-FileLock $CONFIG) {
   Write-Output "Config file is still being created, sleeping for 1 second"
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
   Write-Host "Enabling TLS 1.2 ...";
   $tls12RegBase = "HKLM:\\\\SYSTEM\\CurrentControlSet\\Control\\SecurityProviders\\SCHANNEL\\Protocols\\TLS 1.2";
   if (Test-Path $tls12RegBase) { throw ("'{0}' already exists!" -f $tls12RegBase) }
   New-Item -Path ("{0}/Client" -f $tls12RegBase) -Force;
   New-Item -Path ("{0}/Server" -f $tls12RegBase) -Force;
   New-ItemProperty -Path ("{0}/Client" -f $tls12RegBase) -Name "DisabledByDefault" -PropertyType DWORD -Value 0 -Force;
   New-ItemProperty -Path ("{0}/Client" -f $tls12RegBase) -Name "Enabled" -PropertyType DWORD -Value 1 -Force;
   New-ItemProperty -Path ("{0}/Server" -f $tls12RegBase) -Name "DisabledByDefault" -PropertyType DWORD -Value 0 -Force;
   New-ItemProperty -Path ("{0}/Server" -f $tls12RegBase) -Name "Enabled" -PropertyType DWORD -Value 1 -Force;

   Write-Output "Invoke-WebRequest -Uri $JENKINS_URL/jnlpJars/slave.jar -Outfile $JENKINS_HOME/slave.jar";
   Invoke-WebRequest -Uri "$JENKINS_URL/jnlpJars/slave.jar" -Outfile "$JENKINS_HOME/slave.jar";

   Write-Output "$RUN_CMD";
   Invoke-Expression "$RUN_CMD";

   exit 0
} catch {
   exit 1
}
