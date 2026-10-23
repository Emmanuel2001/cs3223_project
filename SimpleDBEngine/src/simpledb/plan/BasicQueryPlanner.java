package simpledb.plan;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.metadata.*;
import simpledb.parse.*;
import simpledb.materialize.*;

/**
 * The simplest, most naive query planner possible.
 * @author Edward Sciore
 */
public class BasicQueryPlanner implements QueryPlanner {
   private MetadataMgr mdm;
   
   public BasicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Creates a query plan as follows.  It first takes
    * the product of all tables and views; it then selects on the predicate;
    * and finally it projects on the field list. 
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      if (data.hasSetOps())
         return SetOpPlanner.plan(this, data, tx);
      StarExpander.expand(data, tx, mdm);   // select * -> explicit field list
      //Step 1: Create a plan for each mentioned table or view.
     List<Plan> plans = new ArrayList<>();
      for (String tblname : data.tables()) {
         String viewdef = mdm.getViewDef(tblname, tx);
         if (viewdef != null) { // Recursively plan the view.
            Parser parser = new Parser(viewdef);
            QueryData viewdata = parser.query();
            plans.add(createPlan(viewdata, tx));
         }
         else
            plans.add(new TablePlan(tx, tblname, mdm));
      }
      
      //Step 2: Create the product of all table plans
      Plan p = plans.remove(0);
      for (Plan nextplan : plans)
         p = new ProductPlan(p, nextplan);
      
      //Step 3: Add a selection plan for the predicate
      p = new SelectPlan(p, data.pred());
      
      // group by / aggregates
      if (data.hasAggregation())
         p = GroupByPlan.forQuery(tx, p, data);

      // order by on a field that is not selected: sort before projecting
      boolean sortfirst = false;
      if (data.hasOrderBy()) {
         for (String f : data.sortFields()) {
            if (!p.schema().hasField(f))
               throw new IllegalArgumentException("unknown field in order by: " + f);
            if (!data.fields().contains(f))
               sortfirst = true;
         }
         if (sortfirst && data.isDistinct())
            throw new IllegalArgumentException(
                  "with DISTINCT, the order by fields must appear in the select list");
         if (sortfirst)
            p = new SortPlan(tx, p, data.sortFields(), data.sortOrders());
      }
      p = new ProjectPlan(p, data.fields());

      // distinct
      if (data.isDistinct())
         p = new DistinctPlan(tx, p);

      // order by
      if (data.hasOrderBy() && !sortfirst)
         p = new SortPlan(tx, p, data.sortFields(), data.sortOrders());
      return p;
   }
}
