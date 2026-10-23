package simpledb.query;

import java.util.HashMap;
import java.util.Map;

public class TermTest {
   private static int failures = 0;

   private static class RowScan implements Scan {
      private final Map<String,Constant> row = new HashMap<>();
      RowScan put(String f, int v)    { row.put(f, new Constant(v)); return this; }
      RowScan put(String f, String v) { row.put(f, new Constant(v)); return this; }
      public void beforeFirst() {}
      public boolean next() { return false; }
      public int getInt(String f) { return row.get(f).asInt(); }
      public String getString(String f) { return row.get(f).asString(); }
      public Constant getVal(String f) { return row.get(f); }
      public boolean hasField(String f) { return row.containsKey(f); }
      public void close() {}
   }

   private static void check(String what, Object expected, Object actual) {
      boolean ok = (expected == null) ? actual == null : expected.equals(actual);
      if (!ok) {
         failures++;
         System.out.println("FAIL  " + what + ": expected " + expected + " but got " + actual);
      }
   }

   public static void main(String[] args) {
      String[] oprs = {"=", "<", "<=", ">", ">=", "!=", "<>"};
      // the row: A=5, B=7, S="bob", T="carl"
      RowScan s = new RowScan().put("A", 5).put("B", 7).put("S", "bob").put("T", "carl");
      Constant five = new Constant(5), seven = new Constant(7);

      // expected truth values for A opr B (5 vs 7) and B opr A (7 vs 5)
      boolean[] aVsB = {false, true,  true,  false, false, true,  true };
      boolean[] bVsA = {false, false, false, true,  true,  true,  true };
      // and for A opr 5 (equal operands)
      boolean[] aVs5 = {true,  false, true,  false, true,  false, false};

      for (int i = 0; i < oprs.length; i++) {
         String o = oprs[i];
         boolean isEq = o.equals("=");

         check("A " + o + " B",   aVsB[i], new Term(new Expression("A"), new Expression("B"), o).isSatisfied(s));
         check("B " + o + " A",   bVsA[i], new Term(new Expression("B"), new Expression("A"), o).isSatisfied(s));
         check("A " + o + " 5",   aVs5[i], new Term(new Expression("A"), new Expression(five), o).isSatisfied(s));
         check("5 " + o + " A",   aVs5[i], new Term(new Expression(five), new Expression("A"), o).isSatisfied(s));
         check("S " + o + " T",   aVsB[i], new Term(new Expression("S"), new Expression("T"), o).isSatisfied(s));
         check("S " + o + " 'bob'", aVs5[i],
               new Term(new Expression("S"), new Expression(new Constant("bob")), o).isSatisfied(s));

         Term fc = new Term(new Expression("A"), new Expression(five), o);
         Term cf = new Term(new Expression(five), new Expression("A"), o);
         check("equatesWithConstant(A) for A " + o + " 5", isEq ? five : null, fc.equatesWithConstant("A"));
         check("equatesWithConstant(A) for 5 " + o + " A", isEq ? five : null, cf.equatesWithConstant("A"));
         check("equatesWithConstant(B) for A " + o + " 5", null, fc.equatesWithConstant("B"));

         Term ff = new Term(new Expression("A"), new Expression("B"), o);
         check("equatesWithField(A) for A " + o + " B", isEq ? "B" : null, ff.equatesWithField("A"));
         check("equatesWithField(B) for A " + o + " B", isEq ? "A" : null, ff.equatesWithField("B"));
         check("equatesWithField(C) for A " + o + " B", null, ff.equatesWithField("C"));
         // a field-vs-constant term never equates with a field, and vice versa
         check("equatesWithField(A) for A " + o + " 5", null, fc.equatesWithField("A"));
         check("equatesWithConstant(A) for A " + o + " B", null, ff.equatesWithConstant("A"));

         check("toString for " + o, "A" + o + "5", fc.toString());
      }

      // the two-argument constructor still means "="
      Term legacy = new Term(new Expression("A"), new Expression(five));
      check("default operator", "=", legacy.operator());
      check("default constant", five, legacy.equatesWithConstant("A"));

      if (failures == 0)
         System.out.println("TermTest: all checks passed.");
      else {
         System.out.println("TermTest: " + failures + " check(s) FAILED.");
         System.exit(1);
      }
   }
}
