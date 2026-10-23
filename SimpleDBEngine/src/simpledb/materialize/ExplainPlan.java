package simpledb.materialize;

import java.util.*;
import simpledb.record.Schema;
import simpledb.plan.Plan;
import simpledb.query.Scan;

public class ExplainPlan implements Plan {
   private List<String> lines = new ArrayList<String>();
   private Schema sch = new Schema();

   public ExplainPlan(Plan p) {
      describe(p, 0);
      int width = 1;
      for (String line : lines)
         width = Math.max(width, line.length());
      sch.addStringField(ExplainScan.FIELD, width);
   }

   private void describe(Plan p, int depth) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < depth; i++)
         sb.append("   ");
      if (depth > 0)
         sb.append("-> ");
      sb.append(p.describe());
      sb.append("   (est. ").append(estimate(p, true)).append(" blocks, ")
        .append(estimate(p, false)).append(" rows)");
      lines.add(sb.toString());
      for (Plan child : p.children())
         describe(child, depth + 1);
   }

   // the optimizer's estimate, or "?" if the plan cannot give one
   private String estimate(Plan p, boolean blocks) {
      try {
         return "" + (blocks ? p.blocksAccessed() : p.recordsOutput());
      }
      catch (RuntimeException e) {
         return "?";
      }
   }

   public Scan open() {
      return new ExplainScan(lines);
   }

   public int blocksAccessed() {
      return 0;
   }

   public int recordsOutput() {
      return lines.size();
   }

   public int distinctValues(String fldname) {
      return lines.size();
   }

   public Schema schema() {
      return sch;
   }
}
