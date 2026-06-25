package project;
import java.util.*;

/**
 * IRGenerator.java
 *
 * Traverses the AST and emits three-address code stored as an array of
 * Quadruple objects.  Each quadruple has the form:
 *
 *      (OP, ARG1, ARG2, RESULT)
 *
 * as required by the project specification.
 *
 * Supported constructs:
 *   - Variable declarations (skipped — handled at runtime)
 *   - Assignment statements
 *   - Arithmetic expressions (binary & unary)
 *   - loopif … holds … endloop
 *   - switchFor … case … other … endswitchFor
 *   - outString
 */
public class IRGenerator {

    // ----------------------------------------------------------------
    // Quadruple data class
    // ----------------------------------------------------------------

    public static class Quadruple {
        public String op;
        public String arg1;
        public String arg2;
        public String result;

        public Quadruple(String op, String arg1, String arg2, String result) {
            this.op     = op;
            this.arg1   = arg1 != null ? arg1 : "";
            this.arg2   = arg2 != null ? arg2 : "";
            this.result = result != null ? result : "";
        }

        @Override
        public String toString() {
            return String.format("%-10s %-12s %-12s %s", op, arg1, arg2, result);
        }
    }

    // ----------------------------------------------------------------
    // State
    // ----------------------------------------------------------------

    private final SymbolTable          symTable;
    private final List<Quadruple>      quads     = new ArrayList<>();
    private       int                  tempCount = 0;
    private       int                  labelCount= 0;

    public IRGenerator(SymbolTable symTable) {
        this.symTable = symTable;
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    public void generate(SimpleNode root) throws SemanticException {
        visitProgram(root);
    }

    public List<Quadruple> getQuadruples() { return quads; }

    public void printQuadruples() {
        System.out.println(String.format("%-5s %-10s %-12s %-12s %s",
                "IDX", "OP", "ARG1", "ARG2", "RESULT"));
        System.out.println("-".repeat(55));
        for (int i = 0; i < quads.size(); i++) {
            System.out.println(String.format("%-5d %s", i, quads.get(i)));
        }
    }

    public void printStatements() {
        printStatements(quads);
    }

    /**
     * Renders the quadruple list as readable three-address statements,
     * e.g.   t1 = b * c
     *        a  = t1 + d
     *        print a
     */
    public static void printStatements(List<Quadruple> qs) {
        for (Quadruple q : qs) {
            System.out.println(formatStatement(q));
        }
    }

    public static String formatStatement(Quadruple q) {
        String op  = q.op;
        String a1  = q.arg1;
        String a2  = q.arg2;
        String res = q.result;

        // Simple assignment:  result = arg1
        if (op.equals("=")) {
            return res + " = " + a1;
        }

        // Unary minus:  result = minus arg1   (rendered:  result = -arg1)
        if (op.equals("minus")) {
            return res + " = -" + a1;
        }

        // Arithmetic binary operators
        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
            return res + " = " + a1 + " " + op + " " + a2;
        }

        // outString
        if (op.equals("print")) {
            return "print " + a1;
        }

        // label L1   →   L1:
        if (op.equals("label")) {
            return a1 + ":";
        }

        // goto Lx
        if (op.equals("goto")) {
            return "goto " + a1;
        }

        // Conditional jump:  emitted as ("if <op>", lhs, rhs, "goto Lx")
        if (op.startsWith("if ")) {
            String relOp = op.substring(3);
            return "if " + a1 + " " + relOp + " " + a2 + " " + res;
        }

        // Fallback: print the raw quadruple
        return q.toString();
    }

    // ----------------------------------------------------------------
    // New temporaries / labels
    // ----------------------------------------------------------------

    private String newTemp()  { return "T" + (++tempCount); }
    private String newLabel() { return "L" + (++labelCount); }

    private void emit(String op, String a1, String a2, String res) {
        quads.add(new Quadruple(op, a1, a2, res));
    }

    // ----------------------------------------------------------------
    // AST visitors
    // ----------------------------------------------------------------

    private void visitProgram(SimpleNode node) throws SemanticException {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            SimpleNode child = (SimpleNode) node.jjtGetChild(i);
            if (child instanceof ASTCodeBlock) visitCodeBlock(child);
        }
    }

    private void visitCodeBlock(SimpleNode block) throws SemanticException {
        for (int i = 0; i < block.jjtGetNumChildren(); i++) {
            visitStatement((SimpleNode) block.jjtGetChild(i));
        }
    }

    private void visitStatement(SimpleNode stmt) throws SemanticException {
        if (stmt instanceof ASTAssignStmt)    visitAssign(stmt);
        else if (stmt instanceof ASTLoopStmt)   visitLoop(stmt);
        else if (stmt instanceof ASTSwitchStmt) visitSwitch(stmt);
        else if (stmt instanceof ASTOutStringStmt) visitOutString(stmt);
    }

    // ----------------------------------------------------------------
    // Assignment:  lhs = expr
    // ----------------------------------------------------------------

    private void visitAssign(SimpleNode stmt) throws SemanticException {
        String lhs   = (String) stmt.jjtGetValue();
        SimpleNode rhs = (SimpleNode) stmt.jjtGetChild(0);
        String src   = genExpr(rhs);
        emit("=", src, "", lhs);
    }

    // ----------------------------------------------------------------
    // Expression evaluation — returns the temp or literal holding the value
    // ----------------------------------------------------------------

    private String genExpr(SimpleNode node) throws SemanticException {

        if (node instanceof ASTLiteral) {
            return (String) node.jjtGetValue();
        }

        if (node instanceof ASTIdentNode) {
            return (String) node.jjtGetValue();
        }

        if (node instanceof ASTUnaryOp) {
            String operand = genExpr((SimpleNode) node.jjtGetChild(0));
            String t = newTemp();
            emit("minus", operand, "", t);
            return t;
        }

        if (node instanceof ASTBinaryOp) {
            String left  = genExpr((SimpleNode) node.jjtGetChild(0));
            String right = genExpr((SimpleNode) node.jjtGetChild(1));
            String op    = (String) node.jjtGetValue();
            String t     = newTemp();
            emit(op, left, right, t);
            return t;
        }

        // Generic fallback: single-child wrapper
        if (node.jjtGetNumChildren() == 1) {
            return genExpr((SimpleNode) node.jjtGetChild(0));
        }
        if (node.jjtGetNumChildren() == 2) {
            // Treat as binary op if value is an operator symbol
            String v = node.jjtGetValue() != null ? node.jjtGetValue().toString() : "";
            if (v.matches("[+\\-*/]")) {
                String left  = genExpr((SimpleNode) node.jjtGetChild(0));
                String right = genExpr((SimpleNode) node.jjtGetChild(1));
                String t     = newTemp();
                emit(v, left, right, t);
                return t;
            }
        }

        return "?";
    }

    // ----------------------------------------------------------------
    // loopif <cond> holds … endloop
    //
    //  L_start:
    //      if NOT cond goto L_end
    //      <body>
    //      goto L_start
    //  L_end:
    // ----------------------------------------------------------------

    private void visitLoop(SimpleNode loop) throws SemanticException {
        String lStart = newLabel();
        String lEnd   = newLabel();

        // Label: start
        emit("label", lStart, "", "");

        // Condition (child 0)
        SimpleNode cond = (SimpleNode) loop.jjtGetChild(0);
        String condOp   = (String) cond.jjtGetValue();
        String lhs      = genExpr((SimpleNode) cond.jjtGetChild(0));
        String rhs      = genExpr((SimpleNode) cond.jjtGetChild(1));

        // Negate the condition for the jump-out
        String negOp = negateOp(condOp);
        emit("if " + negOp, lhs, rhs, "goto " + lEnd);

        // Body statements
        for (int i = 1; i < loop.jjtGetNumChildren(); i++) {
            visitStatement((SimpleNode) loop.jjtGetChild(i));
        }

        emit("goto", lStart, "", "");
        emit("label", lEnd, "", "");
    }

    // ----------------------------------------------------------------
    // switchFor
    //
    //  For each case:
    //      if var <> caseVal goto L_next
    //      <case body>
    //      goto L_end
    //  L_next:
    //      ...
    //  other body
    //  L_end:
    // ----------------------------------------------------------------

    private void visitSwitch(SimpleNode sw) throws SemanticException {
        String var   = (String) sw.jjtGetValue();
        String lEnd  = newLabel();

        for (int i = 0; i < sw.jjtGetNumChildren(); i++) {
            SimpleNode child = (SimpleNode) sw.jjtGetChild(i);

            if (child instanceof ASTCaseClause) {
                // child 0: Literal (case value); child 1: AssignStmt
                String caseVal = (String) ((SimpleNode) child.jjtGetChild(0)).jjtGetValue();
                String lNext   = newLabel();

                emit("if <>", var, caseVal, "goto " + lNext);
                visitStatement((SimpleNode) child.jjtGetChild(1));
                emit("goto", lEnd, "", "");
                emit("label", lNext, "", "");

            } else if (child instanceof ASTOtherClause) {
                visitStatement((SimpleNode) child.jjtGetChild(0));
            }
        }

        emit("label", lEnd, "", "");
    }

    // ----------------------------------------------------------------
    // outString
    // ----------------------------------------------------------------

    private void visitOutString(SimpleNode node) throws SemanticException {
        SimpleNode expr = (SimpleNode) node.jjtGetChild(0);
        String val = genExpr(expr);
        emit("print", val, "", "");
    }

    // ----------------------------------------------------------------
    // Helper — negate a condition operator
    // ----------------------------------------------------------------

    private String negateOp(String op) {
        switch (op) {
            case "<=": return ">";
            case ">=": return "<";
            case "==": return "<>";
            case "<>": return "==";
            case "<":  return ">=";
            case ">":  return "<=";
            default:   return "!" + op;
        }
    }
}
