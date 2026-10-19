package simpledb.materialize;

import simpledb.query.*;

/**
 * The Scan class for the <i>nestedloopjoin</i> operator.
 * Unlike ProductPlan+SelectPlan, the join predicate is evaluated
 * directly inside next(), so non-matching pairs are skipped
 * internally instead of being materialized and then filtered out.
 * @author Edward Sciore
 */
public class NestedLoopJoinScan implements Scan {
   private Scan s1, s2;
   private Predicate joinpred;

   /**
    * Create a nested-loops join scan for the two underlying scans.
    * @param s1 the LHS (outer) scan
    * @param s2 the RHS (inner) scan
    * @param joinpred the predicate to join on
    */
   public NestedLoopJoinScan(Scan s1, Scan s2, Predicate joinpred) {
      this.s1 = s1;
      this.s2 = s2;
      this.joinpred = joinpred;
      beforeFirst();
   }

   /**
    * Position the scan before its first record.
    * The LHS scan is positioned at its first record,
    * and the RHS scan is positioned before its first record.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      s1.beforeFirst();
      s1.next();
      s2.beforeFirst();
   }

   /**
    * Move to the next (s1,s2) pair satisfying the join predicate.
    * Advances the inner scan (s2) first; when it is exhausted,
    * resets it and advances the outer scan (s1). Returns false
    * once the outer scan is exhausted.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (advance()) {
         if (joinpred.isSatisfied(this))
            return true;
      }
      return false;
   }

   private boolean advance() {
      if (s2.next())
         return true;
      s2.beforeFirst();
      return s2.next() && s1.next();
   }

   /**
    * Return the integer value of the specified field.
    * @see simpledb.query.Scan#getInt(java.lang.String)
    */
   public int getInt(String fldname) {
      if (s1.hasField(fldname))
         return s1.getInt(fldname);
      else
         return s2.getInt(fldname);
   }

   /**
    * Return the string value of the specified field.
    * @see simpledb.query.Scan#getString(java.lang.String)
    */
   public String getString(String fldname) {
      if (s1.hasField(fldname))
         return s1.getString(fldname);
      else
         return s2.getString(fldname);
   }

   /**
    * Return the value of the specified field.
    * @see simpledb.query.Scan#getVal(java.lang.String)
    */
   public Constant getVal(String fldname) {
      if (s1.hasField(fldname))
         return s1.getVal(fldname);
      else
         return s2.getVal(fldname);
   }

   /**
    * Return true if the specified field is in either underlying scan.
    * @see simpledb.query.Scan#hasField(java.lang.String)
    */
   public boolean hasField(String fldname) {
      return s1.hasField(fldname) || s2.hasField(fldname);
   }

   /**
    * Close both underlying scans.
    * @see simpledb.query.Scan#close()
    */
   public void close() {
      s1.close();
      s2.close();
   }
}
