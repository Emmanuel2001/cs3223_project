package simpledb.materialize;

import simpledb.query.*;

public class NestedLoopJoinScan implements Scan {
   private Scan s1, s2;
   private Predicate joinpred;
   private boolean hasmore1;   // does the outer scan have a current record?

   public NestedLoopJoinScan(Scan s1, Scan s2, Predicate joinpred) {
      this.s1 = s1;
      this.s2 = s2;
      this.joinpred = joinpred;
      beforeFirst();
   }

   public void beforeFirst() {
      s1.beforeFirst();
      hasmore1 = s1.next();   // false if the outer scan is empty
      s2.beforeFirst();
   }

   public boolean next() {
      while (advance()) {
         if (joinpred.isSatisfied(this))
            return true;
      }
      return false;
   }

   private boolean advance() {
      while (hasmore1) {
         if (s2.next())
            return true;
         hasmore1 = s1.next();
         s2.beforeFirst();
      }
      return false;
   }

   public int getInt(String fldname) {
      if (s1.hasField(fldname))
         return s1.getInt(fldname);
      else
         return s2.getInt(fldname);
   }

   public String getString(String fldname) {
      if (s1.hasField(fldname))
         return s1.getString(fldname);
      else
         return s2.getString(fldname);
   }

   public Constant getVal(String fldname) {
      if (s1.hasField(fldname))
         return s1.getVal(fldname);
      else
         return s2.getVal(fldname);
   }

   public boolean hasField(String fldname) {
      return s1.hasField(fldname) || s2.hasField(fldname);
   }

   public void close() {
      s1.close();
      s2.close();
   }
}
