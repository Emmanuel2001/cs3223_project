package simpledb.plan;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class NonEqualityPlanTest {
   private static int failures = 0;
   private static final int N = 60;

   private static void check(String what, Object expected, Object actual) {
      if (!expected.equals(actual)) {
         failures++;
         System.out.println("FAIL  " + what + ": expected " + expected + " but got " + actual);
      }
   }

   private static boolean cmp(int x, String opr, int c) {
      switch (opr) {
      case "<":  return x <  c;
      case "<=": return x <= c;
      case ">":  return x >  c;
      case ">=": return x >= c;
      case "!=":
      case "<>": return x != c;
      default:   return x == c;
      }
   }

   private static void delete(File f) {
      File[] kids = f.listFiles();
      if (kids != null) for (File k : kids) delete(k);
      f.delete();
   }

   private static Plan plan(Planner pl, String q, Transaction tx, StringBuilder out) {
      PrintStream old = System.out;
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      System.setOut(new PrintStream(buf));
      try { return pl.createQueryPlan(q, tx); }
      finally { System.setOut(old); out.append(buf); }
   }

   private static int count(Planner pl, String q, Transaction tx, StringBuilder log) {
      Plan p = plan(pl, q, tx, log);
      Scan s = p.open();
      int n = 0;
      while (s.next()) n++;
      s.close();
      return n;
   }

   public static void main(String[] args) throws Exception {
      String dir = "nonequalitytestdb";
      delete(new File(dir));
      SimpleDB db = new SimpleDB(dir);
      Planner pl = db.planner();
      Transaction tx = db.newTx();

      pl.executeUpdate("create table t(a int, b int, name varchar(10))", tx);
      pl.executeUpdate("create index ia on t(a) using hash", tx);
      pl.executeUpdate("create index ib on t(b) using btree", tx);
      // a = 0..59, b = a mod 10, name = "n<a>"
      for (int i = 0; i < N; i++)
         pl.executeUpdate("insert into t(a, b, name) values (" + i + ", " + (i % 10) + ", 'n" + i + "')", tx);

      String[] oprs = {"=", "<", "<=", ">", ">=", "!=", "<>"};
      int[] consts = {-1, 0, 5, 30, 59, 100};

      for (String o : oprs) {
         for (int c : consts) {
            int expA = 0, expB = 0;
            for (int i = 0; i < N; i++) {
               if (cmp(i, o, c))      expA++;
               if (cmp(i % 10, o, c)) expB++;
            }
            StringBuilder log = new StringBuilder();
            check("select a " + o + " " + c, expA, count(pl, "select a from t where a " + o + " " + c, tx, log));
            check("index use on a " + o + " " + c, o.equals("="), log.toString().contains("index on a used"));

            log = new StringBuilder();
            check("select b " + o + " " + c, expB, count(pl, "select b from t where b " + o + " " + c, tx, log));
            check("index use on b " + o + " " + c, o.equals("="), log.toString().contains("index on b used"));

            // constant on the left-hand side
            log = new StringBuilder();
            int expFlip = 0;
            for (int i = 0; i < N; i++) if (cmp(c, o, i)) expFlip++;
            check("select " + c + " " + o + " a", expFlip,
                  count(pl, "select a from t where " + c + " " + o + " a", tx, log));
            check("index use on " + c + " " + o + " a", o.equals("="), log.toString().contains("index on a used"));
         }
      }

      check("range 10 <= a and a < 20", 10,
            count(pl, "select a from t where a >= 10 and a < 20", tx, new StringBuilder()));
      check("a > 5 and b = 3", 5,   // a = 13,23,33,43,53
            count(pl, "select a from t where a > 5 and b = 3", tx, new StringBuilder()));
      check("name != 'n7'", N - 1,
            count(pl, "select a from t where name != 'n7'", tx, new StringBuilder()));
      check("name <> 'n7'", N - 1,
            count(pl, "select a from t where name <> 'n7'", tx, new StringBuilder()));
      int expStr = 0;
      for (int i = 0; i < N; i++) if (("n" + i).compareTo("n5") < 0) expStr++;
      check("name < 'n5'", expStr,
            count(pl, "select a from t where name < 'n5'", tx, new StringBuilder()));

      check("a != b", N - 10,       // a = b only for a = 0..9
            count(pl, "select a from t where a != b", tx, new StringBuilder()));

      pl.executeUpdate("create table u(x int)", tx);
      for (int i = 0; i < 5; i++) pl.executeUpdate("insert into u(x) values (" + i + ")", tx);
      StringBuilder log = new StringBuilder();
      int expJoin = 0;
      for (int i = 0; i < N; i++) for (int x = 0; x < 5; x++) if (i < x) expJoin++;
      check("join t,u where a < x", expJoin, count(pl, "select a, x from t, u where a < x", tx, log));
      check("no index join for a < x", false, log.toString().toLowerCase().contains("index join"));

      check("update b where a >= 50", 10, pl.executeUpdate("update t set b = 99 where a >= 50", tx));
      check("rows with b = 99", 10, count(pl, "select a from t where b = 99", tx, new StringBuilder()));
      check("delete where a <> 0 and a < 5", 4, pl.executeUpdate("delete from t where a <> 0 and a < 5", tx));
      check("delete where a < 10 (rest)", 6, pl.executeUpdate("delete from t where a < 10", tx));
      check("rows left", N - 10, count(pl, "select a from t", tx, new StringBuilder()));

      tx.commit();
      if (failures == 0)
         System.out.println("NonEqualityPlanTest: all checks passed.");
      else {
         System.out.println("NonEqualityPlanTest: " + failures + " check(s) FAILED.");
         System.exit(1);
      }
   }
}
