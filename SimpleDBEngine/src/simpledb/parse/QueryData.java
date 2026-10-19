package simpledb.parse;

import java.util.*;

import simpledb.query.*;

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

   /**
    * Saves the field and table list and predicate.
    * There is no order by clause.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
      this(fields, tables, pred, new ArrayList<String>(), new ArrayList<Boolean>());
   }

   /**
    * Saves the field and table list, predicate, and order by clause.
    * @param sortfields the fields named in the order by clause, in order
    * @param sortorders parallel list: true if the corresponding sortfield is ascending, false if descending
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<String> sortfields, List<Boolean> sortorders) {
      this.fields = fields;
      this.tables = tables;
      this.pred = pred;
      this.sortfields = sortfields;
      this.sortorders = sortorders;
   }

   /**
    * Returns the fields mentioned in the select clause.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
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

   /**
    * Returns the fields mentioned in the order by clause, in order.
    * Empty if there was no order by clause.
    * @return a list of field names
    */
   public List<String> sortFields() {
      return sortfields;
   }

   /**
    * Returns, parallel to sortFields(), whether each sort field is ascending (true) or descending (false).
    * @return a list of booleans
    */
   public List<Boolean> sortOrders() {
      return sortorders;
   }

   /**
    * Returns true if the query has an order by clause.
    */
   public boolean hasOrderBy() {
      return !sortfields.isEmpty();
   }

   public String toString() {
      String result = "select ";
      for (String fldname : fields)
         result += fldname + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      result += " from ";
      for (String tblname : tables)
         result += tblname + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      String predstring = pred.toString();
      if (!predstring.equals(""))
         result += " where " + predstring;
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
