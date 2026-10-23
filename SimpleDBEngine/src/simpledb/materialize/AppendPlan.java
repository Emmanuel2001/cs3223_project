package simpledb.materialize;

import java.util.*;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;

public class AppendPlan implements Plan {
   private Plan p1, p2;

   public AppendPlan(Plan p1, Plan p2) {
      this.p1 = p1;
      this.p2 = p2;
   }

   public Scan open() {
      return new AppendScan(p1.open(), p2.open());
   }

   public int blocksAccessed() {
      return p1.blocksAccessed() + p2.blocksAccessed();
   }

   public int recordsOutput() {
      return (int) Math.min(Integer.MAX_VALUE, (long) p1.recordsOutput() + p2.recordsOutput());
   }

   public int distinctValues(String fldname) {
      return Math.max(p1.distinctValues(fldname), p2.distinctValues(fldname));
   }

   public Schema schema() {
      return p1.schema();
   }

   public String describe() {
      return "Append";
   }

   public List<Plan> children() {
      return Arrays.asList(p1, p2);
   }

   private static class AppendScan implements Scan {
      private Scan s1, s2;
      private boolean onfirst = true;

      AppendScan(Scan s1, Scan s2) {
         this.s1 = s1;
         this.s2 = s2;
      }
      public void beforeFirst() {
         s1.beforeFirst();
         s2.beforeFirst();
         onfirst = true;
      }
      public boolean next() {
         if (onfirst) {
            if (s1.next())
               return true;
            onfirst = false;
         }
         return s2.next();
      }
      private Scan cur()                   { return onfirst ? s1 : s2; }
      public int getInt(String f)          { return cur().getInt(f); }
      public String getString(String f)    { return cur().getString(f); }
      public Constant getVal(String f)     { return cur().getVal(f); }
      public boolean hasField(String f)    { return s1.hasField(f); }
      public void close()                  { s1.close(); s2.close(); }
   }
}
