#!/usr/bin/env bash
set -euo pipefail
quality_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="${QUALITY_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B -f "$quality_dir/tools-pom.xml" dependency:copy-dependencies -DoutputDirectory=tools/jmeter-libs
mkdir -p "$quality_dir/tools/jmeter-home/bin" "$quality_dir/tools/jmeter-home/lib/ext" "$quality_dir/tools/jmeter-home/lib/junit"
for name in saveservice.properties jmeter.properties reportgenerator.properties upgrade.properties; do
 curl --fail --location --retry 2 --max-time 90 "https://raw.githubusercontent.com/apache/jmeter/rel/v5.6.3/bin/$name" -o "$quality_dir/tools/jmeter-home/bin/$name"
done
javac -proc:none -cp "$quality_dir/tools/jmeter-libs/*" -d "$quality_dir/tools" "$quality_dir/JMeterCli.java"
