package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;

public class SetOpPlan implements Plan {
   private Plan left, right;     // right is already renamed to left's field names
   private Plan union;           // for UNION [ALL]
   private Plan sl, sr;          // sorted inputs, for INTERSECT / EXCEPT
   private String op;
   private boolean all;
   private List<String> fields;

   public SetOpPlan(Transaction tx, Plan p1, Plan p2, String op, boolean all) {
      Schema s1 = p1.schema(), s2 = p2.schema();
      fields = new ArrayList<String>(s1.fields());
      List<String> f2 = s2.fields();
      if (fields.size() != f2.size())
         throw new IllegalArgumentException(op + ": both queries must select the same number of fields ("
                                            + fields.size() + " vs " + f2.size() + ")");
      for (int i = 0; i < fields.size(); i++)
         if (s1.type(fields.get(i)) != s2.type(f2.get(i)))
            throw new IllegalArgumentException(op + ": field " + (i+1) + " (" + fields.get(i)
                                               + " vs " + f2.get(i) + ") has different types in the two queries");
      this.op = op;
      this.all = all;
      this.left = p1;
      this.right = f2.equals(fields) ? p2 : new RenamePlan(p2, fields, s1);

      if (op.equals("union")) {
         Plan appended = new AppendPlan(left, right);
         union = all ? appended : new DistinctPlan(tx, appended);
      }
      else {
         // DistinctPlan output is already sorted on all fields, in schema order
         sl = all ? new SortPlan(tx, left, fields) : new DistinctPlan(tx, left);
         sr = all ? new SortPlan(tx, right, fields) : new DistinctPlan(tx, right);
      }
   }

   public Scan open() {
      if (union != null)
         return union.open();
      return new MergeSetScan(sl.open(), sr.open(), fields, op.equals("intersect"));
   }

   public int blocksAccessed() {
      if (union != null)
         return union.blocksAccessed();
      return sl.blocksAccessed() + sr.blocksAccessed();
   }

   public int recordsOutput() {
      if (union != null)
         return union.recordsOutput();
      if (op.equals("intersect"))
         return Math.min(left.recordsOutput(), right.recordsOutput());
      return left.recordsOutput();
   }

   public int distinctValues(String fldname) {
      return left.distinctValues(fldname);
   }

   public Schema schema() {
      return left.schema();
   }

   public String describe() {
      return op.substring(0, 1).toUpperCase() + op.substring(1) + (all ? " All" : "");
   }

   public List<Plan> children() {
      return Arrays.asList(left, right);
   }

   private static class MergeSetScan implements Scan {
      private Scan a, b;
      private List<String> fields;
      private boolean intersect;
      private boolean hasA, hasB, advanceA, advanceB;

      MergeSetScan(Scan a, Scan b, List<String> fields, boolean intersect) {
         this.a = a;
         this.b = b;
         this.fields = fields;
         this.intersect = intersect;
         beforeFirst();
      }

      public void beforeFirst() {
         a.beforeFirst();
         b.beforeFirst();
         hasA = a.next();
         hasB = b.next();
         advanceA = advanceB = false;
      }

      private int compare() {
         for (String f : fields) {
            int c = a.getVal(f).compareTo(b.getVal(f));
            if (c != 0)
               return c;
         }
         return 0;
      }

      public boolean next() {
         if (advanceA) { hasA = a.next(); advanceA = false; }
         if (advanceB) { hasB = b.next(); advanceB = false; }
         while (hasA) {
            int c = hasB ? compare() : -1;
            if (intersect) {
               if (!hasB)
                  return false;          // nothing left to match
               if (c < 0)
                  hasA = a.next();
               else if (c > 0)
                  hasB = b.next();
               else {                    // present on both sides
                  advanceA = advanceB = true;
                  return true;
               }
            }
            else {
               if (c < 0) {              // only in the first input
                  advanceA = true;
                  return true;
               }
               else if (c > 0)
                  hasB = b.next();
               else {                    // cancels one copy on each side
                  hasA = a.next();
                  hasB = b.next();
               }
            }
         }
         return false;
      }

      public int getInt(String f)          { return a.getInt(f); }
      public String getString(String f)    { return a.getString(f); }
      public Constant getVal(String f)     { return a.getVal(f); }
      public boolean hasField(String f)    { return a.hasField(f); }
      public void close()                  { a.close(); b.close(); }
   }
}
