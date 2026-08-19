#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/v2ray-tester-1.0.0-jar-with-dependencies.jar"

# --- Check that Java is installed ---
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java was not found on this computer."
    echo
    echo "This tool requires a Java 8 or newer runtime (JRE)."
    echo "You can download a free JRE here:"
    echo
    echo "    https://adoptium.net/temurin/releases/"
    echo
    echo "After installing Java, run this script again."
    echo
    exit 1
fi

# --- Check that the jar is next to this script ---
if [ ! -f "$JAR" ]; then
    echo "[ERROR] The application jar was not found in this folder:"
    echo "    $JAR"
    echo
    echo "Please keep the .jar file and this script in the same folder."
    echo
    exit 1
fi

# --- Start the application ---
java -jar "$JAR"
status=$?
echo
echo "Application finished (exit code $status)."
echo
exit $status
