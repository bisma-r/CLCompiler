#!/usr/bin/env bash
# =============================================================
# build_jar.sh  —  Build using javacc.jar (no PATH install needed)
#
# Download javacc.jar from:
#   https://github.com/javacc/javacc/releases
# Place it in the lib/ folder, then run:
#   ./build_jar.sh              # just build
#   ./build_jar.sh run          # build + run on samples/sample1.cl
#   ./build_jar.sh run sample2  # build + run on samples/sample2.cl
# =============================================================

set -e

SRC_DIR="src"
OUT_DIR="out"
JAR="lib/javacc.jar"

if [ ! -f "$JAR" ]; then
    echo "ERROR: $JAR not found."
    echo "Download javacc-X.X-bin.tar.gz from https://javacc.github.io/javacc/"
    echo "and copy javacc.jar to lib/"
    exit 1
fi

mkdir -p "$OUT_DIR"

echo "[1/4] JJTree pass..."
java -cp "$JAR" jjtree -OUTPUT_DIRECTORY="$OUT_DIR" "$SRC_DIR/CL.jjt"

echo "[2/4] JavaCC pass..."
java -cp "$JAR" javacc -OUTPUT_DIRECTORY="$OUT_DIR" "$OUT_DIR/CL.jj"

echo "[3/4] Compiling..."
javac -d "$OUT_DIR" "$SRC_DIR"/*.java "$OUT_DIR"/*.java

echo "[4/4] Done."

if [ "$1" = "run" ]; then
    SAMPLE="${2:-sample1}"
    echo ""
    echo "======================================================"
    echo " Compiling samples/${SAMPLE}.cl"
    echo "======================================================"
    java -cp "$OUT_DIR" CLParser "samples/${SAMPLE}.cl"
fi
