package project;
import java.util.*;

/**
 * SymbolTable.java
 * Stores each variable's exact lexeme, data type, and current value.
 * Required by both SemanticAnalyzer and IRGenerator.
 */
public class SymbolTable {

    /** Supported CL data types */
    public enum VarType { INT, FLOAT, STRING, CHAR, UNKNOWN }

    /** One row in the symbol table */
    public static class Entry {
        public final String lexeme;   // exact variable name as written
        public VarType      type;
        public String       value;    // stored as String; null means "declared but unset"

        public Entry(String lexeme, VarType type, String value) {
            this.lexeme = lexeme;
            this.type   = type;
            this.value  = value;
        }

        @Override
        public String toString() {
            return String.format("%-15s %-10s %s", lexeme, type, value == null ? "<undef>" : value);
        }
    }

    // Ordered map so we print in declaration order
    private final LinkedHashMap<String, Entry> table = new LinkedHashMap<>();

    // ----------------------------------------------------------------
    // Insertion / update
    // ----------------------------------------------------------------

    /** Declare a new variable (or overwrite). */
    public void declare(String lexeme, VarType type, String value) {
        table.put(lexeme, new Entry(lexeme, type, value));
    }

    /** Update the value of an already-declared variable. */
    public void setValue(String lexeme, String value) throws SemanticException {
        Entry e = table.get(lexeme);
        if (e == null) throw new SemanticException("Undeclared variable: " + lexeme);
        e.value = value;
    }

    // ----------------------------------------------------------------
    // Lookup
    // ----------------------------------------------------------------

    public boolean isDeclared(String lexeme) {
        return table.containsKey(lexeme);
    }

    public Entry lookup(String lexeme) throws SemanticException {
        Entry e = table.get(lexeme);
        if (e == null) throw new SemanticException("Undeclared variable: " + lexeme);
        return e;
    }

    /** Returns null instead of throwing — useful during IR generation. */
    public Entry lookupSafe(String lexeme) {
        return table.get(lexeme);
    }

    public VarType getType(String lexeme) throws SemanticException {
        return lookup(lexeme).type;
    }

    // ----------------------------------------------------------------
    // Pretty-print
    // ----------------------------------------------------------------

    public void print() {
        System.out.println(String.format("%-15s %-10s %s", "LEXEME", "TYPE", "VALUE"));
        System.out.println("-".repeat(40));
        for (Entry e : table.values()) {
            System.out.println(e);
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /** Parse a declared-type string ("int", "float", …) into VarType. */
    public static VarType parseType(String typeStr) {
        switch (typeStr.toLowerCase()) {
            case "int":    return VarType.INT;
            case "float":  return VarType.FLOAT;
            case "string": return VarType.STRING;
            case "char":   return VarType.CHAR;
            default:       return VarType.UNKNOWN;
        }
    }

    /** Infer the type of a literal token value. */
    public static VarType inferLiteralType(String value) {
        if (value == null) return VarType.UNKNOWN;
        if (value.startsWith("\"")) return VarType.STRING;
        if (value.startsWith("'"))  return VarType.CHAR;
        if (value.contains("."))    return VarType.FLOAT;
        return VarType.INT;
    }

    /** True if two types are compatible for arithmetic. */
    public static boolean isNumeric(VarType t) {
        return t == VarType.INT || t == VarType.FLOAT;
    }
}
