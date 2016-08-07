#!/usr/bin/env bash

(
    while true
    do
        sleep 60 && echo "NO_SLEEP_TRAVIS"
    done
)&

set -ex
set -o pipefail

IS_COVERITY_SCAN_BRANCH=`ruby -e "puts '${TRAVIS_BRANCH}' =~ /\\A$COVERITY_SCAN_BRANCH_PATTERN\\z/ ? 1 : 0"`


if [ "${FAST_BUILD}" == "true" ]; then
    if [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$COVERITY" == "true" ] && [ "$IS_COVERITY_SCAN_BRANCH" = "1" ]; then
        export COVERITY_SCAN_BUILD_COMMAND="./mvnw package"
        #curl -s "https://scan.coverity.com/scripts/travisci_build_coverity_scan.sh" | bash
        ./.travis/travisci_build_coverity_scan.sh
    else
        ./mvnw package
    fi
else
    if [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$COVERITY" == "true" ] && [ "$IS_COVERITY_SCAN_BRANCH" = "1" ]; then
        export COVERITY_SCAN_BUILD_COMMAND="./mvnw verify -Pyad-its,!rerunFailingTests -q"
        #curl -s "https://scan.coverity.com/scripts/travisci_build_coverity_scan.sh" | bash
        ./.travis/travisci_build_coverity_scan.sh
    else
        ./mvnw verify -Pyad-its,!rerunFailingTests -q
    fi
fi
