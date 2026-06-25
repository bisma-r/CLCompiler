# CL Compiler — Compiler Construction Spring 2026
### Milestones 1 & 2 | Dr. Sabina Akhtar

A complete compiler for the **CL (Classroom Language)** written in Java using **JavaCC / JJTree**.

---

## Project Structure

```
CLCompiler/
├── src/
│   ├── CL.jjt                  ← Scanner + Parser (JJTree grammar)
│   ├── SymbolTable.java         ← Symbol table (lexeme, type, value)
│   ├── SemanticException.java   ← Custom exception for semantic errors
│   ├── SemanticAnalyzer.java    ← Semantic analysis using symbol table
│   ├── IRGenerator.java         ← Intermediate code (quadruples)
│   ├── Optimizer.java           ← Algebraic simplification + temp elimination
│   └── CodeGenerator.java       ← CISC assembly code generation
├── samples/
│   ├── sample1.cl               ← Simple addition
│   ├── sample2.cl               ← Factorial using loopif
│   ├── sample3.cl               ← switchFor + all data types
│   ├── sample4.cl               ← Optimizer test (identity operations)
│   └── sample5.cl               ← Milestone 2 spec example (a = b*-c + b*-c)
├── lib/                         ← Place javacc.jar here
├── out/                         ← Generated files (auto-created on build)
├── build.sh                     ← Build script (requires jjtree/javacc on PATH)
└── build_jar.sh                 ← Build script (uses lib/javacc.jar)
```

---

## Prerequisites

- **Java JDK 8+**
- **JavaCC** (includes both `jjtree` and `javacc` tools)

### Installing JavaCC

**Option A — Direct download (recommended)**
1. Go to: https://github.com/javacc/javacc/releases
2. Download the latest `javacc-X.X-bin.tar.gz`
3. Extract and copy `javacc.jar` into the `lib/` folder

**Option B — Package manager**
```bash
# macOS
brew install javacc

# Ubuntu / Debian
sudo apt-get install javacc

# Windows (Chocolatey)
choco install javacc
```

---

## Build & Run

### Using `lib/javacc.jar` (no PATH setup needed)

```bash
# Build only
./build_jar.sh

# Build and run sample1.cl
./build_jar.sh run

# Build and run a specific sample (1–5)
./build_jar.sh run sample2
./build_jar.sh run sample5
```

### Using system-installed javacc

```bash
./build.sh run sample1
```

### Manual steps (if scripts don't work)

```bash
mkdir -p out

# Step 1: JJTree (grammar → parser + AST)
java -cp lib/javacc.jar jjtree -OUTPUT_DIRECTORY=out src/CL.jjt

# Step 2: JavaCC (generate parser Java source)
java -cp lib/javacc.jar javacc -OUTPUT_DIRECTORY=out out/CL.jj

# Step 3: Compile everything
javac -d out src/*.java out/*.java

# Step 4: Run on a sample
java -cp out CLParser samples/sample1.cl
```

---

## What Each Phase Does

### Front-End (Milestone 1)

| File | Role |
|------|------|
| `CL.jjt` | Defines all tokens (keywords, operators, literals, identifiers) and the grammar rules for CL programs. JJTree annotations build the AST automatically. |
| `SymbolTable.java` | Hash map of `lexeme → Entry(lexeme, type, value)`. Supports `int`, `float`, `string`, `char`. |
| `SemanticAnalyzer.java` | Walks the AST, populates the symbol table from the `variables:` block, then checks all statements for undeclared variables, type mismatches, and valid operators. |

### Back-End (Milestone 2)

| File | Role |
|------|------|
| `IRGenerator.java` | Walks the AST and emits **quadruples** `(OP, ARG1, ARG2, RESULT)` stored in an `ArrayList`. Handles assignments, arithmetic, `loopif`, `switchFor`, and `outString`. |
| `Optimizer.java` | **Pass 1** — algebraic simplification (removes `x+0`, `x*1`, etc.). **Pass 2** — temporary variable elimination (`T1=X*13; Y=T1` → `Y=X*13`). |
| `CodeGenerator.java` | Translates optimised quadruples to CISC assembly using `LD`, `ST`, `ADD`, `SUB`, `MUL`, `DIV`, `NEG`, `CMP`, conditional jumps, and `PRINT`. |

---

## CL Language Summary

```
startProgram
    variables:
        int    varName = value;
        float  varName = value;
        string varName = "value";
        char   varName = 'v';
    code:
        varName = expression;
        loopif condExpr holds
            statements;
        endloop
        switchFor (varName)
            case value : statement;
            other      : statement;
        endswitchFor
        outString(expression);
endProgram
```

**Operators:** `+  -  *  /`  
**Conditions:** `<=  >=  ==  <>  <  >`  

---

## Sample Output (sample5.cl — `a = b * -c + b * -c`)

```
====== SYMBOL TABLE ======
LEXEME          TYPE       VALUE
----------------------------------------
a               INT        0
b               INT        2
c               INT        3

====== INTERMEDIATE CODE (Quadruples) ======
IDX   OP         ARG1         ARG2         RESULT
-------------------------------------------------------
0     minus      c                         T1
1     *          b            T1           T2
2     minus      c                         T3
3     *          b            T3           T4
4     +          T2           T4           T5
5     =          T5                        a
6     print      a                         

====== OPTIMIZED IR ======
IDX   OP         ARG1         ARG2         RESULT
-------------------------------------------------------
0     minus      c                         T1
1     *          b            T1           T2
2     minus      c                         T3
3     *          b            T3           a       ← T4 eliminated
4     +          T2           a            a       ← T5 eliminated
6     print      a                         

====== TARGET ASSEMBLY CODE ======
; minus c  T1
LD   R0, c
NEG  R1, R0
; * b T1 T2
LD   R2, b
MUL  R3, R2, R1
...
```

---

## Submission

Zip your project as instructed:  
`S5<section>G<group_number>`  
e.g., `S5AG12.zip`

Include: `src/`, `samples/`, `lib/`, `build_jar.sh`, and this `README.md`.

---

*Good Luck!*
