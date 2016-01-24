#!/bin/bash 
set -e
set -o pipefail

mvn clean verify -Ptravis | grep -v Download

