package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.query.UpdateScan;
import simpledb.record.Schema;
import simpledb.tx.Transaction;


import java.util.*;

/**
 * The Scan class for the <i>hashjoin</i> operator.
 * A static hash implementation.
 * A fixed number of buckets is allocated (currently, 100),
 * and each bucket is implemented as an Arraylist
 */
public class HashJoinScan implements Scan {
   public static int NUM_BUCKETS = 100;

   private final Transaction tx;
   private final Scan s1;
   private final Plan p2;
   private final String fldname1, fldname2;
   private Map<Integer, UpdateScan> hashTable;
   private Constant currentS1Value = null;
   private UpdateScan currentBucket;
   
   /**
    * Create a hash join scan for the two underlying plans.
    * @param p1 the LHS plan
    * @param p2 the RHS plan, which will be partitioned
    * @param fldname1 the LHS join field
    * @param fldname2 the RHS join field
    */
   public HashJoinScan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
      this.tx = tx;
      this.s1 = p1.open();
      this.p2 = p2;
      this.fldname1 = fldname1;
      this.fldname2 = fldname2;

      // Build hash table from s2
      buildHashTable();
   }

   private int bucketIndex(Constant key) {
      return key.hashCode() % NUM_BUCKETS;
   }

   private void copyRecord(Schema sch, Scan from, UpdateScan to) {
      to.insert();
      sch.fields().forEach(name -> to.setVal(name, from.getVal(name)));
   }
   
   private void buildHashTable() {
      hashTable = new HashMap<>();

      Scan s2 = p2.open();
      s2.beforeFirst();
      
      while (s2.next()) {
         int idx = bucketIndex(s2.getVal(fldname2));

         UpdateScan bucket = hashTable.computeIfAbsent(idx, _ -> {
            TempTable t = new TempTable(tx, p2.schema());
            UpdateScan s = t.open();
            s.beforeFirst();
            return s;
         });
         copyRecord(p2.schema(), s2, bucket);
      }

      s2.close();
   }
   
   /**
    * Position the scan before its first record.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      s1.beforeFirst();
   }
   
   /**
    * Move to the next record where s1 and s2 join.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (true) {
         // If we have a current bucket, try next record in bucket
         while (currentBucket != null && currentBucket.next()) {
            if (currentBucket.getVal(fldname2).equals(currentS1Value))
               return true;
         }
         
         // Try move to next s1 record
         if (!s1.next()) {
            return false;
         }

         // Update based on new s1 record
         currentS1Value = s1.getVal(fldname1);
         currentBucket = hashTable.get(bucketIndex(currentS1Value));
         if (currentBucket != null)
            currentBucket.beforeFirst();
      }
   }
   
   /**
    * Return the integer value of the specified field.
    * @see simpledb.query.Scan#getInt(java.lang.String)
    */
   public int getInt(String fldname) {
      if (s1.hasField(fldname))
         return s1.getInt(fldname);
      else
         return currentBucket.getInt(fldname);
   }
   
   /**
    * Return the string value of the specified field.
    * @see simpledb.query.Scan#getString(java.lang.String)
    */
   public String getString(String fldname) {
      if (s1.hasField(fldname))
         return s1.getString(fldname);
      else
         return currentBucket.getString(fldname);
   }
   
   /**
    * Return the value of the specified field.
    * @see simpledb.query.Scan#getVal(java.lang.String)
    */
   public Constant getVal(String fldname) {
      if (s1.hasField(fldname))
         return s1.getVal(fldname);
      else
         return currentBucket.getVal(fldname);
   }
   
   /**
    * Return true if the specified field is in either underlying scan.
    * @see simpledb.query.Scan#hasField(java.lang.String)
    */
   public boolean hasField(String fldname) {
      return s1.hasField(fldname) || p2.schema().hasField(fldname);
   }
   
   /**
    * Close both underlying scans.
    * @see simpledb.query.Scan#close()
    */
   public void close() {
      s1.close();
      for (UpdateScan b: hashTable.values()) {
         b.close();
      }
   }
}
