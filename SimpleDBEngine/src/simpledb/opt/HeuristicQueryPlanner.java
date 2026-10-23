package simpledb.opt;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.QueryData;
import simpledb.plan.*;
import simpledb.materialize.*;

/**
 * A query planner that optimizes using a heuristic-based algorithm.
 * @author Edward Sciore
 */
public class HeuristicQueryPlanner implements QueryPlanner {
   private Collection<TablePlanner> tableplanners = new ArrayList<>();
   private MetadataMgr mdm;
   
   public HeuristicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Creates an optimized left-deep query plan using the following
    * heuristics.
    * H1. Choose the smallest table (considering selection predicates)
    * to be first in the join order.
    * H2. Add the table to the join order which
    * results in the smallest output.
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      
      if (data.hasSetOps())
         return SetOpPlanner.plan(this, data, tx);
      StarExpander.expand(data, tx, mdm);   // select * -> explicit field list
      tableplanners = new ArrayList<>();   // start clean even if an earlier query failed half-way
      // Step 1:  Create a TablePlanner object for each mentioned table
      for (String tblname : data.tables()) {
         if (mdm.getViewDef(tblname, tx) != null)
            throw new IllegalArgumentException("views are not supported by the heuristic query planner: " + tblname);
         TablePlanner tp = new TablePlanner(tblname, data.pred(), tx, mdm);
         tableplanners.add(tp);
      }
      
      // Step 2:  Choose the lowest-size plan to begin the join order
      Plan currentplan = getLowestSelectPlan();
      
      // Step 3:  Repeatedly add a plan to the join order
      while (!tableplanners.isEmpty()) {
         Plan p = getLowestJoinPlan(currentplan);
         if (p != null)
            currentplan = p;
         else  // no applicable join
            currentplan = getLowestProductPlan(currentplan);
      }
      
      // group by / aggregates
      if (data.hasAggregation())
         currentplan = GroupByPlan.forQuery(tx, currentplan, data);

      // order by on a field that is not selected: sort before projecting
      boolean sortfirst = false;
      if (data.hasOrderBy()) {
         for (String f : data.sortFields()) {
            if (!currentplan.schema().hasField(f))
               throw new IllegalArgumentException("unknown field in order by: " + f);
            if (!data.fields().contains(f))
               sortfirst = true;
         }
         if (sortfirst && data.isDistinct())
            throw new IllegalArgumentException(
                  "with DISTINCT, the order by fields must appear in the select list");
         if (sortfirst)
            currentplan = new SortPlan(tx, currentplan, data.sortFields(), data.sortOrders());
      }
      Plan projectplan = new ProjectPlan(currentplan, data.fields());

      // distinct
      if (data.isDistinct())
         projectplan = new DistinctPlan(tx, projectplan);

      // order by
      if (data.hasOrderBy() && !sortfirst)
         return new SortPlan(tx, projectplan, data.sortFields(), data.sortOrders());
      return projectplan;
   }
   
   private Plan getLowestSelectPlan() {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeSelectPlan();
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }
   
   private Plan getLowestJoinPlan(Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeJoinPlan(current);
         if (plan != null && (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput())) {
            besttp = tp;
            bestplan = plan;
         }
      }
      if (bestplan != null)
         tableplanners.remove(besttp);
      return bestplan;
   }
   
   private Plan getLowestProductPlan(Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeProductPlan(current);
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }

   public void setPlanner(Planner p) {
      // for use in planning views, which
      // for simplicity this code doesn't do.
   }
}
