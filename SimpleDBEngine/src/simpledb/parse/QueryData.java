package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.materialize.*;

/**
 * Data for the SQL <i>select</i> statement.
 * @author Edward Sciore
 */
public class QueryData {
   private List<String> fields;
   private Collection<String> tables;
   private Predicate pred;
   private List<String> sortfields;
   private List<Boolean> sortorders; // true = ascending, false = descending
   private List<String> groupfields;
   private List<String> aggfuncs;   // "count", "sum", "avg", "min" or "max"
   private List<String> aggfields;  // parallel to aggfuncs: the aggregated field
   private boolean distinct = false;
   private boolean explain = false;
   private List<QueryData> operands = null;   // set operations: the SELECTs being combined
   private List<String> setops = null;        // "union", "intersect" or "except", between operands
   private List<Boolean> setalls = null;      // true if that operation was written with ALL
   private boolean selectall = false;   // select * (fields are filled in by the planner)

   /**
    * Saves the field and table list and predicate.
    * There is no order by clause.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
      this(fields, tables, pred, new ArrayList<String>(), new ArrayList<Boolean>());
   }

   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<String> sortfields, List<Boolean> sortorders) {
      this(fields, tables, pred, sortfields, sortorders,
           new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>());
   }

   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<String> sortfields, List<Boolean> sortorders,
                     List<String> groupfields, List<String> aggfuncs, List<String> aggfields) {
      this.fields = fields;
      this.tables = tables;
      this.pred = pred;
      this.sortfields = sortfields;
      this.sortorders = sortorders;
      this.groupfields = groupfields;
      this.aggfuncs = aggfuncs;
      this.aggfields = aggfields;
   }

   /**
    * Returns the fields mentioned in the select clause.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
   }

   public void setSetOps(List<QueryData> operands, List<String> setops, List<Boolean> setalls) {
      this.operands = operands;
      this.setops = setops;
      this.setalls = setalls;
   }

   public boolean hasSetOps() {
      return operands != null;
   }

   public List<QueryData> operands() {
      return operands;
   }

   public List<String> setOps() {
      return setops;
   }

   public List<Boolean> setAlls() {
      return setalls;
   }

   public boolean isSelectAll() {
      return selectall;
   }

   public void setSelectAll(boolean selectall) {
      this.selectall = selectall;
   }

   public void setFields(List<String> fields) {
      this.fields = fields;
   }

   /**
    * Returns the tables mentioned in the from clause.
    * @return a collection of table names
    */
   public Collection<String> tables() {
      return tables;
   }

   /**
    * Returns the predicate that describes which
    * records should be in the output table.
    * @return the query predicate
    */
   public Predicate pred() {
      return pred;
   }

   public List<String> sortFields() {
      return sortfields;
   }

   public List<Boolean> sortOrders() {
      return sortorders;
   }

   public boolean hasOrderBy() {
      return !sortfields.isEmpty();
   }

   public List<String> groupFields() {
      return groupfields;
   }

   public List<String> aggFuncs() {
      return aggfuncs;
   }

   public List<String> aggFields() {
      return aggfields;
   }

   public boolean isDistinct() {
      return distinct;
   }

   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
   }

   public boolean isExplain() {
      return explain;
   }

   public void setExplain(boolean explain) {
      this.explain = explain;
   }

   public boolean hasAggregation() {
      return !groupfields.isEmpty() || !aggfuncs.isEmpty();
   }

   public List<AggregationFn> aggregationFns() {
      List<AggregationFn> fns = new ArrayList<AggregationFn>();
      Set<String> seen = new HashSet<String>();
      for (int i = 0; i < aggfuncs.size(); i++) {
         String func = aggfuncs.get(i);
         String fld = aggfields.get(i);
         if (!seen.add(func + "of" + fld))
            continue;
         if (func.equals("count"))
            fns.add(new CountFn(fld));
         else if (func.equals("sum"))
            fns.add(new SumFn(fld));
         else if (func.equals("avg"))
            fns.add(new AvgFn(fld));
         else if (func.equals("min"))
            fns.add(new MinFn(fld));
         else if (func.equals("max"))
            fns.add(new MaxFn(fld));
         else
            throw new BadSyntaxException();
      }
      return fns;
   }

   private String orderByString() {
      String result = " order by ";
      for (int i = 0; i < sortfields.size(); i++)
         result += sortfields.get(i) + (sortorders.get(i) ? " asc" : " desc") + ", ";
      return result.substring(0, result.length()-2);
   }

   public String toString() {
      if (hasSetOps()) {
         String result = operands.get(0).toString();
         for (int i = 1; i < operands.size(); i++)
            result += " " + setops.get(i-1) + (setalls.get(i-1) ? " all " : " ") + operands.get(i).toString();
         if (hasOrderBy())
            result += orderByString();
         return result;
      }
      String result = distinct ? "select distinct " : "select ";
      if (selectall)
         result += "*, ";
      else for (String fldname : fields) {
         // turn an aggregate output name such as "sumofgrade" back into sum(grade)
         String item = fldname;
         for (int i = 0; i < aggfuncs.size(); i++)
            if (fldname.equals(aggfuncs.get(i) + "of" + aggfields.get(i)))
               item = aggfuncs.get(i) + "(" + aggfields.get(i) + ")";
         result += item + ", ";
      }
      result = result.substring(0, result.length()-2); //remove final comma
      result += " from ";
      for (String tblname : tables)
         result += tblname + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      String predstring = pred.toString();
      if (!predstring.equals(""))
         result += " where " + predstring;
      if (!groupfields.isEmpty()) {
         result += " group by ";
         for (String fldname : groupfields)
            result += fldname + ", ";
         result = result.substring(0, result.length()-2);
      }
      if (hasOrderBy()) {
         result += " order by ";
         for (int i = 0; i < sortfields.size(); i++) {
            result += sortfields.get(i) + (sortorders.get(i) ? " asc" : " desc") + ", ";
         }
         result = result.substring(0, result.length()-2);
      }
      return result;
   }
}
