#!/usr/bin/env bash
set -ev

./gradlew --no-daemon --version
./gradlew --no-daemon build "$@"
