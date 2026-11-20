param([long]$ConfigTimeoutSeconds)

$progressPreference = 'silentlyContinue'  # no progress bars

$CONFIG=Join-Path $PSScriptRoot 'config.ps1'
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

$timer = [System.Diagnostics.Stopwatch]::StartNew()
while ((-not (Test-Path -LiteralPath $CONFIG -PathType Leaf)) -or (Test-FileLock $CONFIG)) {
   if ($timer.Elapsed.TotalSeconds -ge $ConfigTimeoutSeconds) {
      Write-Output "Timed out waiting for $CONFIG after $ConfigTimeoutSeconds seconds"
      exit 1
   }
   Write-Output "Waiting for config file, sleeping for 1 second"
   sleep 1
}

Write-Output "Found config file $CONFIG"
. "$CONFIG"

if (!$JENKINS_URL) {
   Write-Output "JENKINS_URL is not defined! Exiting."
   exit 1
}

$JENKINS_URL = $JENKINS_URL.TrimEnd('/')

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

if ($INBOUND_ARGS_B64) {
   $inboundArgsFile = [IO.Path]::GetTempFileName()
   [IO.File]::WriteAllBytes($inboundArgsFile, [Convert]::FromBase64String($INBOUND_ARGS_B64))
   $RUN_CMD += ' ("@" + $inboundArgsFile)'
} else {
   $RUN_CMD += ' -jar slave.jar'
}

if ($NO_RECONNECT_SLAVE -eq "true") {
   $RUN_CMD="$RUN_CMD -noReconnect"
}

if ($NO_CERTIFICATE_CHECK -eq "true") {
   $RUN_CMD="$RUN_CMD -noCertificateCheck"
}

if ($SLAVE_OPTS) {
   $RUN_CMD="$RUN_CMD $SLAVE_OPTS"
}

if (!$INBOUND_ARGS_B64) {
   $RUN_CMD="$RUN_CMD -jnlpUrl $JENKINS_URL/$COMPUTER_URL/slave-agent.jnlp"
}

if ($COMPUTER_SECRET) {
   $RUN_CMD="$RUN_CMD -secret $COMPUTER_SECRET"
}

cd $JENKINS_HOME

try {
   [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

   Write-Output "Invoke-WebRequest -Uri $JENKINS_URL/jnlpJars/slave.jar -Outfile $JENKINS_HOME/slave.jar";
   Invoke-WebRequest -UseBasicParsing -Uri "$JENKINS_URL/jnlpJars/slave.jar" -Outfile "$JENKINS_HOME/slave.jar";

   Write-Output "$RUN_CMD";
   Invoke-Expression "$RUN_CMD";

   exit $LASTEXITCODE
} catch {
   exit 1
} finally {
   if ($inboundArgsFile) {
      Remove-Item -LiteralPath $inboundArgsFile
   }
}
