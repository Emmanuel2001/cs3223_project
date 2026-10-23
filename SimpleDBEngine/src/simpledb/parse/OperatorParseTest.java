package simpledb.parse;

public class OperatorParseTest {
   private static int failures = 0;

   private static boolean parses(String sql) {
      try {
         Parser p = new Parser(sql);
         if (sql.startsWith("select")) p.query(); else p.updateCmd();
         return true;
      }
      catch (BadSyntaxException ex) {
         return false;
      }
   }

   private static void expect(String sql, boolean ok) {
      if (parses(sql) != ok) {
         failures++;
         System.out.println("FAIL  " + (ok ? "should parse: " : "should be rejected: ") + sql);
      }
   }

   public static void main(String[] args) {
      for (String o : new String[] {"=", "<", "<=", ">", ">=", "!=", "<>"}) {
         expect("select a from t where a " + o + " 5", true);
         expect("select a from t where a" + o + "5", true);          // no spaces
         expect("select a from t where 5 " + o + " a", true);
         expect("select a from t where a " + o + " b", true);
         expect("select a from t where s " + o + " 'x'", true);
         expect("select a from t where a " + o + " 5 and b " + o + " 6", true);
         expect("delete from t where a " + o + " 5", true);
         expect("update t set b = 1 where a " + o + " 5", true);
         // the PredParser used by PredParserTest must agree
         try { new PredParser("a " + o + " 5 and b " + o + " c").predicate(); }
         catch (BadSyntaxException ex) {
            failures++;
            System.out.println("FAIL  PredParser should accept operator " + o);
         }
      }
      // "=" in "set b = 1" is still an assignment, not a comparison
      expect("update t set b = 1", true);
      // malformed operators
      expect("select a from t where a ! 5", false);
      expect("select a from t where a !! 5", false);
      expect("select a from t where a =< 5", false);
      expect("select a from t where a => 5", false);
      expect("select a from t where a <", false);
      expect("select a from t where a 5", false);

      if (failures == 0)
         System.out.println("OperatorParseTest: all checks passed.");
      else {
         System.out.println("OperatorParseTest: " + failures + " check(s) FAILED.");
         System.exit(1);
      }
   }
}
