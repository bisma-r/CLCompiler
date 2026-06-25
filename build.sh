#!/usr/bin/env bash
# =============================================================
# build.sh  —  Build and optionally run the CL compiler
#
# Usage:
#   ./build.sh              # just compile
#   ./build.sh run          # compile and run on sample1.cl
#   ./build.sh run sample2  # compile and run on sample2.cl
# =============================================================

set -e

SRC_DIR="src"
OUT_DIR="out"
LIB_DIR="lib"

# ---- 0. Prerequisites ----------------------------------------
if ! command -v jjtree &>/dev/null || ! command -v javacc &>/dev/null; then
    echo "ERROR: 'jjtree' and 'javacc' must be on your PATH."
    echo "Download JavaCC from: https://javacc.github.io/javacc/"
    echo ""
    echo "If you have the javacc.jar, run:"
    echo "  java -cp javacc.jar jjtree src/CL.jjt"
    echo "  java -cp javacc.jar javacc src/CL.jj"
    echo "  javac -d out src/*.java out/*.java"
    exit 1
fi

mkdir -p "$OUT_DIR"

# ---- 1. JJTree: CL.jjt → CL.jj + AST node classes ----------
echo "[1/4] Running JJTree..."
jjtree -OUTPUT_DIRECTORY="$OUT_DIR" "$SRC_DIR/CL.jjt"

# ---- 2. JavaCC: CL.jj → Java parser source ------------------
echo "[2/4] Running JavaCC..."
javacc -OUTPUT_DIRECTORY="$OUT_DIR" "$OUT_DIR/CL.jj"

# ---- 3. Compile all Java sources ----------------------------
echo "[3/4] Compiling Java sources..."
javac -d "$OUT_DIR" "$SRC_DIR"/*.java "$OUT_DIR"/*.java

echo "[4/4] Build complete."
echo ""

# ---- 4. Optionally run -------------------------------------
if [ "$1" = "run" ]; then
    SAMPLE="${2:-sample1}"
    echo "======================================================"
    echo " Running CL compiler on: samples/${SAMPLE}.cl"
    echo "======================================================"
    java -cp "$OUT_DIR" CLParser "samples/${SAMPLE}.cl"
fi
