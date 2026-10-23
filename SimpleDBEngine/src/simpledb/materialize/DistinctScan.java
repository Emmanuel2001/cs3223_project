package simpledb.materialize;

import java.util.List;
import simpledb.query.*;

public class DistinctScan implements Scan {
   private Scan s;
   private List<String> fields;
   private GroupValue prev = null;

   public DistinctScan(Scan s, List<String> fields) {
      this.s = s;
      this.fields = fields;
      beforeFirst();
   }

   public void beforeFirst() {
      s.beforeFirst();
      prev = null;
   }

   public boolean next() {
      while (s.next()) {
         GroupValue gv = new GroupValue(s, fields);
         if (prev == null || !prev.equals(gv)) {
            prev = gv;
            return true;
         }
      }
      return false;
   }

   public int getInt(String fldname) {
      return s.getInt(fldname);
   }

   public String getString(String fldname) {
      return s.getString(fldname);
   }

   public Constant getVal(String fldname) {
      return s.getVal(fldname);
   }

   public boolean hasField(String fldname) {
      return s.hasField(fldname);
   }

   public void close() {
      s.close();
   }
}
