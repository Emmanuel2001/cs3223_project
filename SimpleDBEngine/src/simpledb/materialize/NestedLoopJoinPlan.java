package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.plan.ProductPlan;
import simpledb.query.*;
import simpledb.record.Schema;

public class NestedLoopJoinPlan implements Plan {
   private Plan p1, p2;
   private Predicate joinpred;
   private ProductPlan prodplan; // used only for cost/schema estimates

   public NestedLoopJoinPlan(Plan p1, Plan p2, Predicate joinpred) {
      this.p1 = p1;
      this.p2 = p2;
      this.joinpred = joinpred;
      this.prodplan = new ProductPlan(p1, p2);
   }

   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      return new NestedLoopJoinScan(s1, s2, joinpred);
   }

   public int blocksAccessed() {
      return prodplan.blocksAccessed();
   }

   public int recordsOutput() {
      int factor = joinpred.reductionFactor(prodplan);
      return Math.max(1, prodplan.recordsOutput() / factor);
   }

   public int distinctValues(String fldname) {
      return prodplan.distinctValues(fldname);
   }

   public Schema schema() {
      return prodplan.schema();
   }

   public String describe() {
      return "NestedLoopJoin [" + joinpred + "]";
   }

   public java.util.List<Plan> children() {
      return java.util.Arrays.asList(p1, p2);
   }
}
