#!/usr/bin/env sh
set -eu
VERSION=3.9.9
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_HOME="$ROOT/.mvn/apache-maven-$VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$ROOT/.mvn"
  curl -fsSL "https://archive.apache.org/dist/maven/maven-3/$VERSION/binaries/apache-maven-$VERSION-bin.tar.gz" | tar -xz -C "$ROOT/.mvn"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"

