package simpledb.plan;

import java.util.*;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.record.Layout;
import simpledb.tx.Transaction;

public class StarExpander {
   public static void expand(QueryData data, Transaction tx, MetadataMgr mdm) {
      if (!data.isSelectAll() || !data.fields().isEmpty())
         return;
      List<String> flds = new ArrayList<String>();
      for (String tblname : data.tables())
         for (String f : fieldsOf(tblname, tx, mdm))
            if (!flds.contains(f))
               flds.add(f);
      data.setFields(flds);
   }

   static List<String> fieldsOf(String name, Transaction tx, MetadataMgr mdm) {
      String viewdef = mdm.getViewDef(name, tx);
      if (viewdef != null) {
         QueryData viewdata = new Parser(viewdef).query();
         expand(viewdata, tx, mdm);
         return viewdata.fields();
      }
      Layout layout = mdm.getLayout(name, tx);
      if (layout.schema().fields().isEmpty())
         throw new IllegalArgumentException("table " + name + " does not exist");
      return layout.schema().fields();
   }
}
