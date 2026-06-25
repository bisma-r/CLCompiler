package project;
import java.util.*;

/**
 * CodeGenerator.java
 *
 * Translates the (possibly optimised) quadruple list into CISC-style
 * assembly code as described in the project specification.
 *
 * Register model
 * --------------
 * We use a simple register pool: R0, R1, R2, R3.
 * A trivial "next available" allocator is used; registers are freed when
 * a result is stored to a memory location (ST instruction).
 *
 * Instruction set assumed (CISC, x86-like pseudocode)
 * ----------------------------------------------------
 *   LD   Rn, mem       — load variable / literal into register
 *   ST   mem, Rn       — store register to memory variable
 *   ADD  Rd, Rs, src   — Rd = Rs + src
 *   SUB  Rd, Rs, src   — Rd = Rs – src
 *   MUL  Rd, Rs, src   — Rd = Rs * src
 *   DIV  Rd, Rs, src   — Rd = Rs / src
 *   NEG  Rd, Rs        — Rd = –Rs
 *   CMP  Rs, src       — compare (sets flags)
 *   Jxx  label         — conditional jump (JGT, JLT, JGE, JLE, JEQ, JNE)
 *   JMP  label         — unconditional jump
 *   LABEL label:       — label definition
 *   PRINT Rn           — output register value (maps to outString)
 */
public class CodeGenerator {

    private final List<IRGenerator.Quadruple> quads;
    private final List<String>                assembly = new ArrayList<>();

    // Simple register pool
    private static final String[] REGISTERS = {"R0", "R1", "R2", "R3"};
    // Maps variable/temp name → register currently holding its value
    private final Map<String, String> regMap = new LinkedHashMap<>();
    private int nextReg = 0;

    public CodeGenerator(List<IRGenerator.Quadruple> quads) {
        this.quads = quads;
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    public void generate() {
        for (IRGenerator.Quadruple q : quads) {
            emit("; " + q);           // emit original IR as comment
            translateQuad(q);
        }
    }

    public void printAssembly() {
        for (String line : assembly) {
            System.out.println(line);
        }
    }

    public List<String> getAssembly() { return assembly; }

    // ----------------------------------------------------------------
    // Quadruple → assembly
    // ----------------------------------------------------------------

    private void translateQuad(IRGenerator.Quadruple q) {
        switch (q.op) {

            /* ----------------------------------------------------------
             *  Simple copy:  result = arg1
             * ---------------------------------------------------------- */
            case "=": {
                String reg = loadOperand(q.arg1);
                storeResult(q.result, reg);
                break;
            }

            /* ----------------------------------------------------------
             *  Arithmetic binary ops
             * ---------------------------------------------------------- */
            case "+": case "-": case "*": case "/": {
                String r1  = loadOperand(q.arg1);
                String r2  = loadOperand(q.arg2);
                String rd  = allocReg(q.result);
                String mnemonic = opToMnemonic(q.op);
                emit(mnemonic + " " + rd + ", " + r1 + ", " + r2);
                regMap.put(q.result, rd);
                if (!isTemp(q.result)) {
                    emit("ST   " + q.result + ", " + rd);
                }
                break;
            }

            /* ----------------------------------------------------------
             *  Unary minus:  result = –arg1
             * ---------------------------------------------------------- */
            case "minus": {
                String r1 = loadOperand(q.arg1);
                String rd = allocReg(q.result);
                emit("NEG  " + rd + ", " + r1);
                regMap.put(q.result, rd);
                if (!isTemp(q.result)) emit("ST   " + q.result + ", " + rd);
                break;
            }

            /* ----------------------------------------------------------
             *  Label definition
             * ---------------------------------------------------------- */
            case "label": {
                emit(q.arg1 + ":");
                break;
            }

            /* ----------------------------------------------------------
             *  Unconditional jump:  goto Lx
             * ---------------------------------------------------------- */
            case "goto": {
                emit("JMP  " + q.arg1);
                break;
            }

            /* ----------------------------------------------------------
             *  Conditional jump:  if <op> arg1, arg2 → goto Lx
             *  q.op  = "if <op>"   (e.g. "if >=")
             *  q.result = "goto Lx"
             * ---------------------------------------------------------- */
            default: {
                if (q.op.startsWith("if ")) {
                    String condOp = q.op.substring(3).trim();
                    String r1     = loadOperand(q.arg1);
                    String r2     = loadOperand(q.arg2);
                    emit("CMP  " + r1 + ", " + r2);
                    String label = q.result.replace("goto ", "").trim();
                    emit(condToJump(condOp) + " " + label);
                } else if (q.op.equals("print")) {
                    String r = loadOperand(q.arg1);
                    emit("PRINT " + r);
                } else {
                    emit("; [unhandled] " + q);
                }
                break;
            }
        }
    }

    // ----------------------------------------------------------------
    // Register management helpers
    // ----------------------------------------------------------------

    /** Load a value (variable name or literal) into a register. Returns reg name. */
    private String loadOperand(String operand) {
        if (operand == null || operand.isEmpty()) return REGISTERS[0];

        // Already in a register?
        if (regMap.containsKey(operand)) return regMap.get(operand);

        // Allocate a new register and load
        String reg = allocReg(operand);
        emit("LD   " + reg + ", " + operand);
        regMap.put(operand, reg);
        return reg;
    }

    /** Store a register's value to a variable and update regMap. */
    private void storeResult(String dest, String reg) {
        regMap.put(dest, reg);
        if (!isTemp(dest)) {
            emit("ST   " + dest + ", " + reg);
        }
    }

    /** Allocate the next available register (round-robin). */
    private String allocReg(String name) {
        String reg = REGISTERS[nextReg % REGISTERS.length];
        nextReg++;
        // Remove any previous mapping to this register
        regMap.values().removeIf(v -> v.equals(reg));
        return reg;
    }

    // ----------------------------------------------------------------
    // Mnemonic helpers
    // ----------------------------------------------------------------

    private static String opToMnemonic(String op) {
        switch (op) {
            case "+": return "ADD ";
            case "-": return "SUB ";
            case "*": return "MUL ";
            case "/": return "DIV ";
            default:  return op;
        }
    }

    private static String condToJump(String op) {
        switch (op) {
            case ">":  return "JGT ";
            case "<":  return "JLT ";
            case ">=": return "JGE ";
            case "<=": return "JLE ";
            case "==": return "JEQ ";
            case "<>": return "JNE ";
            default:   return "JMP ";
        }
    }

    private static boolean isTemp(String s) {
        return s.matches("T\\d+");
    }

    private void emit(String line) {
        assembly.add(line);
    }
}
