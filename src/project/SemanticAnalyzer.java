package project;
/**
 * SemanticAnalyzer.java
 *
 * Walks the AST produced by the JavaCC/JJTree parser and enforces the
 * semantic rules of the CL language:
 *
 *  1. Every variable used must be declared in the variables block.
 *  2. No variable may be declared twice.
 *  3. Type compatibility for arithmetic and assignment.
 *  4. Condition operands must be numeric or comparable.
 *  5. outString argument is any valid expression (no restriction).
 *
 * It also populates the SymbolTable with declared variables and their
 * initial values.
 */
public class SemanticAnalyzer {

    private final SymbolTable symTable;

    public SemanticAnalyzer(SymbolTable symTable) {
        this.symTable = symTable;
    }

    // ----------------------------------------------------------------
    // Entry point
    // ----------------------------------------------------------------

    public void analyze(SimpleNode root) throws SemanticException {
        // root is Program → children: VarBlock, CodeBlock
        visitProgram(root);
    }

    // ----------------------------------------------------------------
    // Program
    // ----------------------------------------------------------------

    private void visitProgram(SimpleNode node) throws SemanticException {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            SimpleNode child = (SimpleNode) node.jjtGetChild(i);
            if (child instanceof ASTVarBlock) {
                visitVarBlock(child);
            } else if (child instanceof ASTCodeBlock) {
                visitCodeBlock(child);
            }
        }
    }

    // ----------------------------------------------------------------
    // Variable block  — populates symbol table
    // ----------------------------------------------------------------

    private void visitVarBlock(SimpleNode block) throws SemanticException {
        for (int i = 0; i < block.jjtGetNumChildren(); i++) {
            SimpleNode decl = (SimpleNode) block.jjtGetChild(i);
            if (!(decl instanceof ASTVarDecl)) continue;

            // jjtGetValue() was set to "type lexeme" in the grammar
            String raw    = (String) decl.jjtGetValue();
            String[] parts = raw.split(" ", 2);
            String typeStr = parts[0];
            String lexeme  = parts[1];

            if (symTable.isDeclared(lexeme)) {
                throw new SemanticException("Variable '" + lexeme + "' declared more than once.");
            }

            SymbolTable.VarType type = SymbolTable.parseType(typeStr);

            // Optional initializer is the first (and only) child
            String initValue = null;
            if (decl.jjtGetNumChildren() > 0) {
                SimpleNode lit = (SimpleNode) decl.jjtGetChild(0);
                initValue = (String) lit.jjtGetValue();
                // Type-check the literal
                checkLiteralType(type, initValue, lexeme);
            }

            symTable.declare(lexeme, type, initValue);
        }
    }

    // ----------------------------------------------------------------
    // Code block
    // ----------------------------------------------------------------

    private void visitCodeBlock(SimpleNode block) throws SemanticException {
        for (int i = 0; i < block.jjtGetNumChildren(); i++) {
            visitStatement((SimpleNode) block.jjtGetChild(i));
        }
    }

    private void visitStatement(SimpleNode stmt) throws SemanticException {
        if (stmt instanceof ASTAssignStmt) {
            visitAssign(stmt);
        } else if (stmt instanceof ASTLoopStmt) {
            visitLoop(stmt);
        } else if (stmt instanceof ASTSwitchStmt) {
            visitSwitch(stmt);
        } else if (stmt instanceof ASTOutStringStmt) {
            visitOutString(stmt);
        }
    }

    // ----------------------------------------------------------------
    // Assignment:  id = expr ;
    // ----------------------------------------------------------------

    private void visitAssign(SimpleNode stmt) throws SemanticException {
        String lhs = (String) stmt.jjtGetValue();

        if (!symTable.isDeclared(lhs)) {
            throw new SemanticException("Assignment to undeclared variable '" + lhs + "'.");
        }

        SymbolTable.VarType lhsType = symTable.getType(lhs);
        SimpleNode rhs = (SimpleNode) stmt.jjtGetChild(0);
        SymbolTable.VarType rhsType = inferExprType(rhs);

        if (!typesCompatible(lhsType, rhsType)) {
            throw new SemanticException(
                "Type mismatch in assignment to '" + lhs +
                "': cannot assign " + rhsType + " to " + lhsType + ".");
        }
    }

    // ----------------------------------------------------------------
    // Loop statement
    // ----------------------------------------------------------------

    private void visitLoop(SimpleNode loop) throws SemanticException {
        // child 0: CondExpr; children 1..n: AssignStmt
        SimpleNode cond = (SimpleNode) loop.jjtGetChild(0);
        visitCondExpr(cond);
        for (int i = 1; i < loop.jjtGetNumChildren(); i++) {
            visitStatement((SimpleNode) loop.jjtGetChild(i));
        }
    }

    private void visitCondExpr(SimpleNode cond) throws SemanticException {
        // children: lhs-expr  rhs-expr  (operator stored in value)
        SimpleNode lhs = (SimpleNode) cond.jjtGetChild(0);
        SimpleNode rhs = (SimpleNode) cond.jjtGetChild(1);
        SymbolTable.VarType lt = inferExprType(lhs);
        SymbolTable.VarType rt = inferExprType(rhs);
        if (!typesCompatible(lt, rt)) {
            throw new SemanticException(
                "Type mismatch in condition: " + lt + " vs " + rt);
        }
    }

    // ----------------------------------------------------------------
    // Switch statement
    // ----------------------------------------------------------------

    private void visitSwitch(SimpleNode sw) throws SemanticException {
        String var = (String) sw.jjtGetValue();
        if (!symTable.isDeclared(var)) {
            throw new SemanticException("switchFor uses undeclared variable '" + var + "'.");
        }
        for (int i = 0; i < sw.jjtGetNumChildren(); i++) {
            SimpleNode child = (SimpleNode) sw.jjtGetChild(i);
            if (child instanceof ASTCaseClause || child instanceof ASTOtherClause) {
                // last child of each clause is an AssignStmt
                SimpleNode innerStmt = (SimpleNode) child.jjtGetChild(child.jjtGetNumChildren() - 1);
                visitStatement(innerStmt);
            }
        }
    }

    // ----------------------------------------------------------------
    // outString
    // ----------------------------------------------------------------

    private void visitOutString(SimpleNode node) throws SemanticException {
        // Just check that variables inside the expression are declared
        SimpleNode expr = (SimpleNode) node.jjtGetChild(0);
        inferExprType(expr);   // will throw if undeclared var found
    }

    // ----------------------------------------------------------------
    // Type inference for expressions
    // ----------------------------------------------------------------

    private SymbolTable.VarType inferExprType(SimpleNode node) throws SemanticException {
        if (node instanceof ASTLiteral) {
            return SymbolTable.inferLiteralType((String) node.jjtGetValue());
        }

        if (node instanceof ASTIdentNode) {
            String name = (String) node.jjtGetValue();
            if (!symTable.isDeclared(name)) {
                throw new SemanticException("Use of undeclared variable '" + name + "'.");
            }
            return symTable.getType(name);
        }

        if (node instanceof ASTBinaryOp) {
            SymbolTable.VarType left  = inferExprType((SimpleNode) node.jjtGetChild(0));
            SymbolTable.VarType right = inferExprType((SimpleNode) node.jjtGetChild(1));
            if (!SymbolTable.isNumeric(left) || !SymbolTable.isNumeric(right)) {
                throw new SemanticException(
                    "Arithmetic operation requires numeric operands, got " + left + " and " + right);
            }
            // INT op FLOAT → FLOAT
            return (left == SymbolTable.VarType.FLOAT || right == SymbolTable.VarType.FLOAT)
                   ? SymbolTable.VarType.FLOAT : SymbolTable.VarType.INT;
        }

        if (node instanceof ASTUnaryOp) {
            SymbolTable.VarType t = inferExprType((SimpleNode) node.jjtGetChild(0));
            if (!SymbolTable.isNumeric(t)) {
                throw new SemanticException("Unary minus requires numeric operand, got " + t);
            }
            return t;
        }

        // Fallback: recurse into first child
        if (node.jjtGetNumChildren() > 0) {
            return inferExprType((SimpleNode) node.jjtGetChild(0));
        }

        return SymbolTable.VarType.UNKNOWN;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private boolean typesCompatible(SymbolTable.VarType a, SymbolTable.VarType b) {
        if (a == b) return true;
        // INT and FLOAT are mutually compatible
        if (SymbolTable.isNumeric(a) && SymbolTable.isNumeric(b)) return true;
        // UNKNOWN is a wildcard (unresolved literal/expression)
        if (a == SymbolTable.VarType.UNKNOWN || b == SymbolTable.VarType.UNKNOWN) return true;
        return false;
    }

    private void checkLiteralType(SymbolTable.VarType declared, String literal, String name)
            throws SemanticException {
        SymbolTable.VarType actual = SymbolTable.inferLiteralType(literal);
        if (!typesCompatible(declared, actual)) {
            throw new SemanticException(
                "Variable '" + name + "' declared as " + declared +
                " but initialised with " + actual + " literal.");
        }
    }
}
