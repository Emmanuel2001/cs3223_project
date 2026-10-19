package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.plan.ProductPlan;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The Plan class for the <i>nestedloopjoin</i> operator: joins two
 * plans on an arbitrary predicate without materializing the full
 * cross product first. Cost/schema estimates are delegated to an
 * internal ProductPlan (same physical access pattern as a block
 * nested-loops join), but open() returns a fused
 * NestedLoopJoinScan instead of a ProductScan wrapped in a
 * separate SelectScan.
 * @author Edward Sciore
 */
public class NestedLoopJoinPlan implements Plan {
   private Plan p1, p2;
   private Predicate joinpred;
   private ProductPlan prodplan; // used only for cost/schema estimates

   /**
    * Create a nested-loops join plan for the two specified plans.
    * @param p1 the LHS (outer) plan
    * @param p2 the RHS (inner) plan
    * @param joinpred the predicate to join on
    */
   public NestedLoopJoinPlan(Plan p1, Plan p2, Predicate joinpred) {
      this.p1 = p1;
      this.p2 = p2;
      this.joinpred = joinpred;
      this.prodplan = new ProductPlan(p1, p2);
   }

   /**
    * Opens the two underlying plans and returns a fused
    * nested-loops join scan.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      return new NestedLoopJoinScan(s1, s2, joinpred);
   }

   /**
    * Estimates the number of block accesses, same formula as the
    * underlying product (a block nested-loops join reads the inner
    * plan's blocks once per block of the outer plan).
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      return prodplan.blocksAccessed();
   }

   /**
    * Estimates the number of output records: the size of the
    * product, reduced by the join predicate's selectivity.
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      int factor = joinpred.reductionFactor(prodplan);
      return Math.max(1, prodplan.recordsOutput() / factor);
   }

   /**
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      return prodplan.distinctValues(fldname);
   }

   /**
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return prodplan.schema();
   }
}
