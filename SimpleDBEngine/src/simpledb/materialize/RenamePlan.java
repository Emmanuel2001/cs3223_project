package simpledb.materialize;

import java.util.*;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;

public class RenamePlan implements Plan {
   private Plan p;
   private Schema sch;
   private Map<String,String> newtoold = new HashMap<String,String>();

   public RenamePlan(Plan p, List<String> newnames, Schema sch) {
      this.p = p;
      this.sch = sch;
      List<String> oldnames = p.schema().fields();
      for (int i = 0; i < newnames.size(); i++)
         newtoold.put(newnames.get(i), oldnames.get(i));
   }

   public Scan open() {
      return new RenameScan(p.open(), newtoold);
   }

   public int blocksAccessed() {
      return p.blocksAccessed();
   }

   public int recordsOutput() {
      return p.recordsOutput();
   }

   public int distinctValues(String fldname) {
      return p.distinctValues(newtoold.get(fldname));
   }

   public Schema schema() {
      return sch;
   }

   public String describe() {
      return "Rename";
   }

   public List<Plan> children() {
      return Arrays.asList(p);
   }

   private static class RenameScan implements Scan {
      private Scan s;
      private Map<String,String> map;

      RenameScan(Scan s, Map<String,String> map) {
         this.s = s;
         this.map = map;
      }
      public void beforeFirst()            { s.beforeFirst(); }
      public boolean next()                { return s.next(); }
      public int getInt(String f)          { return s.getInt(map.get(f)); }
      public String getString(String f)    { return s.getString(map.get(f)); }
      public Constant getVal(String f)     { return s.getVal(map.get(f)); }
      public boolean hasField(String f)    { return map.containsKey(f); }
      public void close()                  { s.close(); }
   }
}
