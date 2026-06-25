package project;
import java.util.*;

/**
 * Optimizer.java
 *
 * Performs two optimisation passes on the quadruple array produced by
 * IRGenerator:
 *
 *  Pass 1 – Algebraic simplification
 *      Remove identity operations that don't change program state:
 *          X = X + 0   →  removed
 *          X = X - 0   →  removed
 *          X = X * 1   →  removed
 *          X = X / 1   →  removed
 *          X = 0 + X   →  removed  (commutative +)
 *          X = 1 * X   →  removed  (commutative *)
 *          X = X * 0   →  result is always 0, replace with  X = 0
 *          X = 0 * X   →  same
 *
 *  Pass 2 – Temporary variable elimination
 *      When the IR generator emits patterns like
 *          T1 = X * 13
 *          Y  = T1
 *      collapse them into
 *          Y  = X * 13
 *      (the temporary is used exactly once and only as an intermediate
 *      rvalue in the very next instruction)
 */
public class Optimizer {

    private List<IRGenerator.Quadruple> quads;

    public Optimizer(List<IRGenerator.Quadruple> quads) {
        // Work on a fresh mutable copy so the original IR is untouched
        this.quads = new ArrayList<>(quads);
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    public void optimize() {
        // Run both passes until the IR stops changing.  Order matters: the
        // IR generator splits every binary op through a temp
        //      T1 = a + 0
        //      a  = T1
        // so the algebraic-identity pattern (X = X op identity) only becomes
        // visible after temp elimination collapses the pair into  a = a + 0.
        // Iterating to a fixed point also handles cases where algebraic
        // rewrites (e.g.  X = X * 0  →  X = 0) expose further temp-copy pairs.
        int prevSize;
        do {
            prevSize = quads.size();
            eliminateTemporaries();
            algebraicSimplification();
        } while (quads.size() != prevSize);
    }

    public List<IRGenerator.Quadruple> getQuadruples() { return quads; }

    public void printQuadruples() {
        System.out.println(String.format("%-5s %-10s %-12s %-12s %s",
                "IDX", "OP", "ARG1", "ARG2", "RESULT"));
        System.out.println("-".repeat(55));
        for (int i = 0; i < quads.size(); i++) {
            System.out.println(String.format("%-5d %s", i, quads.get(i)));
        }
    }

    public void printStatements() {
        IRGenerator.printStatements(quads);
    }

    // ----------------------------------------------------------------
    // Pass 1: algebraic simplification
    // ----------------------------------------------------------------

    private void algebraicSimplification() {
        List<IRGenerator.Quadruple> optimized = new ArrayList<>();

        for (IRGenerator.Quadruple q : quads) {
            IRGenerator.Quadruple simplified = trySimplify(q);
            if (simplified != null) {          // null → discard the instruction
                optimized.add(simplified);
            }
        }

        quads = optimized;
    }

    /**
     * Returns the (possibly rewritten) quadruple, or null if it should be
     * removed entirely.
     */
    private IRGenerator.Quadruple trySimplify(IRGenerator.Quadruple q) {
        String op  = q.op;
        String a1  = q.arg1;
        String a2  = q.arg2;
        String res = q.result;

        switch (op) {
            case "+":
                //  X = X + 0   or   X = 0 + X  →  remove
                if (isZero(a2) && a1.equals(res)) return null;
                if (isZero(a1) && a2.equals(res)) return null;
                break;

            case "-":
                //  X = X - 0  →  remove
                if (isZero(a2) && a1.equals(res)) return null;
                break;

            case "*":
                //  X = X * 0   or  X = 0 * X  →  X = 0
                if (isZero(a2) || isZero(a1)) {
                    return new IRGenerator.Quadruple("=", "0", "", res);
                }
                //  X = X * 1   or  X = 1 * X  →  remove
                if (isOne(a2) && a1.equals(res)) return null;
                if (isOne(a1) && a2.equals(res)) return null;
                break;

            case "/":
                //  X = X / 1  →  remove
                if (isOne(a2) && a1.equals(res)) return null;
                break;

            default:
                break;
        }

        return q;   // keep as-is
    }

    // ----------------------------------------------------------------
    // Pass 2: temporary variable elimination
    // ----------------------------------------------------------------

    /**
     * Looks for back-to-back pairs:
     *     (OP,  A, B, Tn)          -- Tn is a compiler-generated temp
     *     (=,   Tn, ,  X)          -- Tn assigned to real variable X
     *
     * And collapses them to:
     *     (OP,  A, B, X)
     */
    private void eliminateTemporaries() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < quads.size() - 1; i++) {
                IRGenerator.Quadruple curr = quads.get(i);
                IRGenerator.Quadruple next = quads.get(i + 1);

                // curr must produce a temp; next must be a copy of that temp
                if (!isTemp(curr.result)) continue;
                if (!next.op.equals("=")) continue;
                if (!next.arg1.equals(curr.result)) continue;
                if (!next.arg2.isEmpty()) continue;   // copy, not binary op

                // Temp is only used once overall — verify
                String temp = curr.result;
                if (countUsages(temp, i + 2) > 0) continue;  // used later too

                // Collapse: replace curr's result with next's result
                curr.result = next.result;
                quads.remove(i + 1);
                changed = true;
            }
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static boolean isZero(String s) {
        try { return Double.parseDouble(s) == 0.0; }
        catch (NumberFormatException e) { return false; }
    }

    private static boolean isOne(String s) {
        try { return Double.parseDouble(s) == 1.0; }
        catch (NumberFormatException e) { return false; }
    }

    /** True if the name looks like a compiler-generated temporary (T1, T2, …). */
    private static boolean isTemp(String name) {
        return name.matches("T\\d+");
    }

    /** Count how many times `name` appears as arg1 or arg2 from index `from` onward. */
    private int countUsages(String name, int from) {
        int count = 0;
        for (int i = from; i < quads.size(); i++) {
            IRGenerator.Quadruple q = quads.get(i);
            if (q.arg1.equals(name) || q.arg2.equals(name)) count++;
        }
        return count;
    }
}
