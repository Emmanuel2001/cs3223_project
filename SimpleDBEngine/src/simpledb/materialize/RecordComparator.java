package simpledb.materialize;

import java.util.*;

import simpledb.query.*;

/**
 * A comparator for scans.
 * @author Edward Sciore
 */
public class RecordComparator implements Comparator<Scan> {
   private List<String> fields;
   private List<Boolean> sortorders; // true = ascending, false = descending, one per field

   /**
    * Create a comparator using the specified fields,
    * using the ordering implied by its iterator.
    * All fields are compared in ascending order.
    * @param fields a list of field names
    */
   public RecordComparator(List<String> fields) {
      this(fields, allAscending(fields.size()));
   }

   /**
    * Create a comparator using the specified fields, each with its
    * own ascending/descending direction.
    * @param fields a list of field names
    * @param sortorders a parallel list: true if the corresponding field
    * should be compared ascending, false if descending
    */
   public RecordComparator(List<String> fields, List<Boolean> sortorders) {
      this.fields = fields;
      this.sortorders = sortorders;
   }

   private static List<Boolean> allAscending(int n) {
      List<Boolean> orders = new ArrayList<>();
      for (int i = 0; i < n; i++)
         orders.add(true);
      return orders;
   }

   /**
    * Compare the current records of the two specified scans.
    * The sort fields are considered in turn.
    * When a field is encountered for which the records have
    * different values, those values are used as the result
    * of the comparison (negated if that field is descending).
    * If the two records have the same values for all
    * sort fields, then the method returns 0.
    * @param s1 the first scan
    * @param s2 the second scan
    * @return the result of comparing each scan's current record according to the field list
    */
   public int compare(Scan s1, Scan s2) {
      for (int i = 0; i < fields.size(); i++) {
         String fldname = fields.get(i);
         Constant val1 = s1.getVal(fldname);
         Constant val2 = s2.getVal(fldname);
         int result = val1.compareTo(val2);
         if (result != 0)
            return sortorders.get(i) ? result : -result;
      }
      return 0;
   }
}
