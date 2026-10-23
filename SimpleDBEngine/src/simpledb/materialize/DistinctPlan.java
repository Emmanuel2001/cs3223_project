package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.Schema;
import simpledb.plan.Plan;
import simpledb.query.Scan;

public class DistinctPlan implements Plan {
   private Plan sorted;
   private List<String> fields;

   public DistinctPlan(Transaction tx, Plan p) {
      fields = new ArrayList<String>(p.schema().fields());
      sorted = new SortPlan(tx, p, fields);
   }

   public Scan open() {
      return new DistinctScan(sorted.open(), fields);
   }

   public int blocksAccessed() {
      return sorted.blocksAccessed();
   }

   public int recordsOutput() {
      long combos = 1;
      for (String f : fields) {
         combos *= Math.max(1, sorted.distinctValues(f));
         if (combos >= sorted.recordsOutput())
            return sorted.recordsOutput();
      }
      return (int) Math.min(combos, sorted.recordsOutput());
   }

   public int distinctValues(String fldname) {
      return sorted.distinctValues(fldname);
   }

   public Schema schema() {
      return sorted.schema();
   }

   public String describe() {
      return "Distinct";
   }

   public List<Plan> children() {
      return Arrays.asList(sorted);
   }
}
