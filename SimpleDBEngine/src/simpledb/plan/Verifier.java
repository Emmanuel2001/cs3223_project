package simpledb.plan;

import java.util.*;
import static java.sql.Types.*;
import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.query.*;
import simpledb.record.*;

public class Verifier {
   private MetadataMgr mdm;

   public Verifier(MetadataMgr mdm) {
      this.mdm = mdm;
   }

   private static void fail(String msg) {
      throw new IllegalArgumentException(msg);
   }

   private static void need(Map<String,Integer> types, String fld, String where) {
      if (!types.containsKey(fld))
         fail("unknown field " + fld + " in " + where);
   }

   public void verifyQuery(QueryData data, Transaction tx) {
      typesOf(data, tx);
   }

   private Map<String,Integer> typesOf(QueryData data, Transaction tx) {
      if (data.hasSetOps()) {
         Map<String,Integer> first = null;
         for (QueryData q : data.operands()) {
            Map<String,Integer> m = typesOf(q, tx);
            if (first == null)
               first = m;
         }
         for (String f : data.sortFields())
            if (!first.containsKey(f))
               fail("unknown field " + f + " in order by (use the field names of the first query)");
         return first;
      }

      // from clause
      Map<String,Integer> in = new LinkedHashMap<String,Integer>();
      Set<String> seen = new HashSet<String>();
      for (String tbl : data.tables()) {
         if (!seen.add(tbl))
            fail("table " + tbl + " appears twice in the from clause (aliases are not supported)");
         in.putAll(sourceTypes(tbl, tx));
      }
      // where clause and group by
      checkPredicate(data.pred(), in, "where clause");
      for (String g : data.groupFields())
         need(in, g, "group by");

      // select list
      Map<String,Integer> out = new LinkedHashMap<String,Integer>();
      if (data.isSelectAll())
         out.putAll(in);
      else
         for (String f : data.fields()) {
            int ai = aggIndex(data, f);
            if (ai < 0) {
               need(in, f, "select list");
               out.put(f, in.get(f));
               continue;
            }
            String func = data.aggFuncs().get(ai), fld = data.aggFields().get(ai);
            if (!(func.equals("count") && fld.equals("*"))) {
               need(in, fld, func + "(" + fld + ")");
               if (!func.equals("count") && in.get(fld) != INTEGER)
                  fail(func + "(" + fld + ") needs an integer field");
            }
            out.put(f, INTEGER);
         }

      // order by
      for (String f : data.sortFields()) {
         boolean ok = out.containsKey(f)
                   || (data.hasAggregation() ? data.groupFields().contains(f) : in.containsKey(f));
         if (!ok)
            fail("unknown field " + f + " in order by");
      }
      return out;
   }

   private static int aggIndex(QueryData data, String outname) {
      for (int i = 0; i < data.aggFuncs().size(); i++)
         if (outname.equals(data.aggFuncs().get(i) + "of" + data.aggFields().get(i)))
            return i;
      return -1;
   }

   private Map<String,Integer> sourceTypes(String name, Transaction tx) {
      String viewdef = mdm.getViewDef(name, tx);
      if (viewdef != null)
         return typesOf(new Parser(viewdef).query(), tx);
      return schemaTypes(tableSchema(name, tx));
   }

   private Schema tableSchema(String name, Transaction tx) {
      Schema sch = mdm.getLayout(name, tx).schema();
      if (sch.fields().isEmpty())
         fail("table " + name + " does not exist");
      return sch;
   }

   private Schema updatableTable(String name, Transaction tx, String action) {
      if (mdm.getViewDef(name, tx) != null)
         fail("cannot " + action + " " + name + ": it is a view");
      return tableSchema(name, tx);
   }

   private static Map<String,Integer> schemaTypes(Schema sch) {
      Map<String,Integer> m = new LinkedHashMap<String,Integer>();
      for (String f : sch.fields())
         m.put(f, sch.type(f));
      return m;
   }

   private void checkPredicate(Predicate pred, Map<String,Integer> types, String where) {
      for (Term t : pred.terms()) {
         Integer lt = exprType(t.lhs(), types, where);
         Integer rt = exprType(t.rhs(), types, where);
         if (lt != null && rt != null && !lt.equals(rt))
            fail("type mismatch in " + where + ": " + t);
      }
   }

   private Integer exprType(Expression e, Map<String,Integer> types, String where) {
      if (e.isFieldName()) {
         need(types, e.asFieldName(), where);
         return types.get(e.asFieldName());
      }
      return e.asConstant().isInt() ? INTEGER : VARCHAR;
   }

   private void checkValue(Schema sch, String fld, Constant c) {
      if (sch.type(fld) == INTEGER) {
         if (!c.isInt())
            fail("field " + fld + " is an integer but the value is the string '" + c + "'");
      }
      else {
         if (c.isInt())
            fail("field " + fld + " is a string but the value is the integer " + c);
         if (c.asString().length() > sch.length(fld))
            fail("value '" + c + "' is too long for field " + fld + " (at most " + sch.length(fld) + " characters)");
      }
   }

   public void verifyUpdate(Object data, Transaction tx) {
      if (data instanceof InsertData)
         verifyInsert((InsertData) data, tx);
      else if (data instanceof DeleteData) {
         DeleteData d = (DeleteData) data;
         Schema sch = updatableTable(d.tableName(), tx, "delete from");
         checkPredicate(d.pred(), schemaTypes(sch), "where clause");
      }
      else if (data instanceof ModifyData)
         verifyModify((ModifyData) data, tx);
      else if (data instanceof CreateTableData)
         verifyCreateTable((CreateTableData) data, tx);
      else if (data instanceof CreateViewData) {
         CreateViewData d = (CreateViewData) data;
         checkNameFree(d.viewName(), tx);
         typesOf(new Parser(d.viewDef()).query(), tx);
      }
      else if (data instanceof CreateIndexData) {
         CreateIndexData d = (CreateIndexData) data;
         Schema sch = updatableTable(d.tableName(), tx, "create an index on");
         if (!sch.hasField(d.fieldName()))
            fail("table " + d.tableName() + " has no field " + d.fieldName());
         if (mdm.getIndexInfo(d.tableName(), tx).containsKey(d.fieldName()))
            fail("an index on " + d.tableName() + "." + d.fieldName() + " already exists");
      }
   }

   private void verifyInsert(InsertData d, Transaction tx) {
      Schema sch = updatableTable(d.tableName(), tx, "insert into");
      if (d.fields().size() != d.vals().size())
         fail("insert: " + d.fields().size() + " fields but " + d.vals().size() + " values");
      Set<String> seen = new HashSet<String>();
      for (int i = 0; i < d.fields().size(); i++) {
         String f = d.fields().get(i);
         if (!sch.hasField(f))
            fail("table " + d.tableName() + " has no field " + f);
         if (!seen.add(f))
            fail("field " + f + " is listed twice in the insert");
         checkValue(sch, f, d.vals().get(i));
      }
   }

   private void verifyModify(ModifyData d, Transaction tx) {
      Schema sch = updatableTable(d.tableName(), tx, "update");
      Map<String,Integer> types = schemaTypes(sch);
      if (!sch.hasField(d.targetField()))
         fail("table " + d.tableName() + " has no field " + d.targetField());
      Expression e = d.newValue();
      Integer et = exprType(e, types, "set clause");
      if (!et.equals(types.get(d.targetField())))
         fail("type mismatch in set clause: " + d.targetField() + " = " + e);
      if (!e.isFieldName())
         checkValue(sch, d.targetField(), e.asConstant());
      checkPredicate(d.pred(), types, "where clause");
   }

   private void verifyCreateTable(CreateTableData d, Transaction tx) {
      checkNameFree(d.tableName(), tx);
      Schema sch = d.newSchema();
      if (sch.fields().isEmpty())
         fail("a table needs at least one field");
      Set<String> seen = new HashSet<String>();
      for (String f : sch.fields()) {
         if (!seen.add(f))
            fail("field " + f + " is defined twice in table " + d.tableName());
         if (sch.type(f) == VARCHAR && sch.length(f) <= 0)
            fail("varchar field " + f + " needs a positive length");
      }
      int slot = new Layout(sch).slotSize();
      if (slot > tx.blockSize())
         fail("a record of table " + d.tableName() + " would take " + slot
              + " bytes, more than a block (" + tx.blockSize() + " bytes)");
   }

   private void checkNameFree(String name, Transaction tx) {
      if (mdm.getViewDef(name, tx) != null || !mdm.getLayout(name, tx).schema().fields().isEmpty())
         fail("a table or view named " + name + " already exists");
   }
}
