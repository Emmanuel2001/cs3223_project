package simpledb.plan;

import simpledb.tx.Transaction;
import simpledb.parse.QueryData;
import simpledb.materialize.*;

public class SetOpPlanner {
   public static Plan plan(QueryPlanner qp, QueryData data, Transaction tx) {
      Plan p = qp.createPlan(data.operands().get(0), tx);
      for (int i = 1; i < data.operands().size(); i++) {
         Plan q = qp.createPlan(data.operands().get(i), tx);
         p = new SetOpPlan(tx, p, q, data.setOps().get(i-1), data.setAlls().get(i-1));
      }
      if (data.hasOrderBy()) {
         for (String f : data.sortFields())
            if (!p.schema().hasField(f))
               throw new IllegalArgumentException("unknown field in order by: " + f
                     + " (use the field names of the first query)");
         p = new SortPlan(tx, p, data.sortFields(), data.sortOrders());
      }
      return p;
   }
}
