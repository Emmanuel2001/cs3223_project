package simpledb.materialize;

import simpledb.query.*;

public class SumFn implements AggregationFn {
   private String fldname;
   private int sum;

   public SumFn(String fldname) {
      this.fldname = fldname;
   }

   public void processFirst(Scan s) {
      sum = s.getInt(fldname);
   }

   public void processNext(Scan s) {
      sum += s.getInt(fldname);
   }

   public String fieldName() {
      return "sumof" + fldname;
   }

   public Constant value() {
      return new Constant(sum);
   }
}
