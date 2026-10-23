package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.query.UpdateScan;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

import java.util.*;

public class HashJoinScan implements Scan {
   public static int MAX_PARTITIONS = 100;

   private final Schema sch1, sch2;
   private final String fldname1, fldname2;
   private final int numparts;
   private final TempTable[] parts1, parts2;
   private final boolean[] has1, has2;   // is the partition non-empty?

   private int curpart = -1;
   private Scan probe = null;                       // current left partition
   private Map<Constant,List<Map<String,Constant>>> table = null; // right partition
   private Iterator<Map<String,Constant>> matches = null;
   private Map<String,Constant> currentRight = null;

   public HashJoinScan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
      this.sch1 = p1.schema();
      this.sch2 = p2.schema();
      this.fldname1 = fldname1;
      this.fldname2 = fldname2;

      // open both inputs first so availableBuffs() counts their pinned buffers
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      // keep one buffer in reserve (moving to a new block pins it briefly)
      numparts = Math.max(1, Math.min(MAX_PARTITIONS, tx.availableBuffs() - 1));

      parts1 = new TempTable[numparts];
      parts2 = new TempTable[numparts];
      has1 = new boolean[numparts];
      has2 = new boolean[numparts];
      for (int i = 0; i < numparts; i++) {
         parts1[i] = new TempTable(tx, sch1);
         parts2[i] = new TempTable(tx, sch2);
      }

      partition(s1, sch1, fldname1, parts1, has1);
      partition(s2, sch2, fldname2, parts2, has2);
      s1.close();
      s2.close();
   }

   private int partitionOf(Constant key) {
      return Math.floorMod(key.hashCode(), numparts);
   }

   private void partition(Scan src, Schema sch, String fld,
                          TempTable[] parts, boolean[] nonempty) {
      UpdateScan[] outs = new UpdateScan[numparts];   // opened lazily
      src.beforeFirst();
      while (src.next()) {
         int i = partitionOf(src.getVal(fld));
         if (outs[i] == null)
            outs[i] = parts[i].open();
         nonempty[i] = true;
         outs[i].insert();
         for (String name : sch.fields())
            outs[i].setVal(name, src.getVal(name));
      }
      for (UpdateScan out : outs)
         if (out != null)
            out.close();
   }

   private void loadTable(int i) {
      table = new HashMap<>();
      Scan s = parts2[i].open();
      s.beforeFirst();
      while (s.next()) {
         Map<String,Constant> rec = new HashMap<>();
         for (String name : sch2.fields())
            rec.put(name, s.getVal(name));
         table.computeIfAbsent(rec.get(fldname2), k -> new ArrayList<>()).add(rec);
      }
      s.close();
   }

   private void closeProbe() {
      if (probe != null) {
         probe.close();
         probe = null;
      }
   }

   private boolean nextPartition() {
      closeProbe();
      table = null;
      matches = null;
      while (curpart < numparts - 1) {
         curpart++;
         if (!has1[curpart] || !has2[curpart])
            continue;
         loadTable(curpart);
         probe = parts1[curpart].open();
         probe.beforeFirst();
         return true;
      }
      curpart = numparts;
      return false;
   }

   public void beforeFirst() {
      closeProbe();
      table = null;
      matches = null;
      currentRight = null;
      curpart = -1;
   }

   public boolean next() {
      while (true) {
         if (matches != null && matches.hasNext()) {
            currentRight = matches.next();
            return true;
         }
         matches = null;
         if (probe != null && probe.next()) {
            List<Map<String,Constant>> l = table.get(probe.getVal(fldname1));
            if (l != null)
               matches = l.iterator();
            continue;
         }
         if (curpart >= numparts || !nextPartition())
            return false;
      }
   }

   public int getInt(String fldname) {
      return getVal(fldname).asInt();
   }

   public String getString(String fldname) {
      return getVal(fldname).asString();
   }

   public Constant getVal(String fldname) {
      if (sch1.hasField(fldname))
         return probe.getVal(fldname);
      else
         return currentRight.get(fldname);
   }

   public boolean hasField(String fldname) {
      return sch1.hasField(fldname) || sch2.hasField(fldname);
   }

   public void close() {
      closeProbe();
      table = null;
      matches = null;
   }
}
