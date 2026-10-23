package simpledb.plan;

import simpledb.tx.Transaction;
import simpledb.parse.*;
import simpledb.materialize.ExplainPlan;
import simpledb.metadata.MetadataMgr;

/**
 * The object that executes SQL statements.
 * @author Edward Sciore
 */
public class Planner {
   private QueryPlanner qplanner;
   private UpdatePlanner uplanner;
   private Verifier verifier;   // null: no verification
   
   public Planner(QueryPlanner qplanner, UpdatePlanner uplanner) {
      this(qplanner, uplanner, null);
   }

   public Planner(QueryPlanner qplanner, UpdatePlanner uplanner, MetadataMgr mdm) {
      this.qplanner = qplanner;
      this.uplanner = uplanner;
      this.verifier = mdm == null ? null : new Verifier(mdm);
   }
   
   /**
    * Creates a plan for an SQL select statement, using the supplied planner.
    * @param qry the SQL query string
    * @param tx the transaction
    * @return the scan corresponding to the query plan
    */
   public Plan createQueryPlan(String qry, Transaction tx) {
      Parser parser = new Parser(qry);
      QueryData data = parser.query();
      if (verifier != null) verifier.verifyQuery(data, tx);
      Plan p = qplanner.createPlan(data, tx);
      if (data.isExplain())
         return new ExplainPlan(p);   // describe the plan instead of running it
      return p;
   }
   
   /**
    * Executes an SQL insert, delete, modify, or
    * create statement.
    * The method dispatches to the appropriate method of the
    * supplied update planner,
    * depending on what the parser returns.
    * @param cmd the SQL update string
    * @param tx the transaction
    * @return an integer denoting the number of affected records
    */
   public int executeUpdate(String cmd, Transaction tx) {
      Parser parser = new Parser(cmd);
      Object data = parser.updateCmd();
      if (verifier != null) verifier.verifyUpdate(data, tx);
      if (data instanceof InsertData)
         return uplanner.executeInsert((InsertData)data, tx);
      else if (data instanceof DeleteData)
         return uplanner.executeDelete((DeleteData)data, tx);
      else if (data instanceof ModifyData)
         return uplanner.executeModify((ModifyData)data, tx);
      else if (data instanceof CreateTableData)
         return uplanner.executeCreateTable((CreateTableData)data, tx);
      else if (data instanceof CreateViewData)
         return uplanner.executeCreateView((CreateViewData)data, tx);
      else if (data instanceof CreateIndexData)
         return uplanner.executeCreateIndex((CreateIndexData)data, tx);
      else
         return 0;
   }
}
