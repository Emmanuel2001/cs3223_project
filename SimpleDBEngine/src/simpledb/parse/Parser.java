package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.record.*;

/**
 * The SimpleDB parser.
 * @author Edward Sciore
 */
public class Parser {
   private Lexer lex;
   
   public Parser(String s) {
      lex = new Lexer(s);
   }
   
// Methods for parsing predicates, terms, expressions, constants, and fields
   
   public String field() {
      return lex.eatId();
   }
   
   public Constant constant() {
      if (lex.matchStringConstant())
         return new Constant(lex.eatStringConstant());
      else
         return new Constant(lex.eatIntConstant());
   }
   
   public Expression expression() {
      if (lex.matchId())
         return new Expression(field());
      else
         return new Expression(constant());
   }
   
   public Term term() {
	   Expression lhs = expression();
	   String opr = lex.eatOpr();
	   Expression rhs = expression();
	   return new Term(lhs, rhs, opr);
	}
   
   public Predicate predicate() {
      Predicate pred = new Predicate(term());
      if (lex.matchKeyword("and")) {
         lex.eatKeyword("and");
         pred.conjoinWith(predicate());
      }
      return pred;
   }
   
// Methods for parsing queries
   
   public QueryData query() {
      boolean explain = false;
      if (lex.matchKeyword("explain") || lex.matchKeyword("display")) {
         explain = true;
         if (lex.matchKeyword("explain"))
            lex.eatKeyword("explain");
         else
            lex.eatKeyword("display");
      }
      QueryData first = selectQuery();
      if (!(lex.matchKeyword("union") || lex.matchKeyword("intersect") || lex.matchKeyword("except"))) {
         first.setExplain(explain);
         return first;
      }
      // select ... {union | intersect | except} [all] select ... : evaluated left to right
      List<QueryData> operands = new ArrayList<QueryData>();
      List<String> ops = new ArrayList<String>();
      List<Boolean> alls = new ArrayList<Boolean>();
      operands.add(first);
      while (lex.matchKeyword("union") || lex.matchKeyword("intersect") || lex.matchKeyword("except")) {
         String op = lex.matchKeyword("union") ? "union" : lex.matchKeyword("intersect") ? "intersect" : "except";
         lex.eatKeyword(op);
         boolean all = false;
         if (lex.matchKeyword("all")) {
            lex.eatKeyword("all");
            all = true;
         }
         ops.add(op);
         alls.add(all);
         operands.add(selectQuery());
      }
      // an order by clause is only allowed at the very end, and applies to the whole result
      for (int i = 0; i < operands.size() - 1; i++)
         if (operands.get(i).hasOrderBy())
            throw new BadSyntaxException("order by is only allowed at the end of a union/intersect/except query");
      QueryData last = operands.get(operands.size() - 1);
      List<String> sortfields = new ArrayList<String>(last.sortFields());
      List<Boolean> sortorders = new ArrayList<Boolean>(last.sortOrders());
      last.sortFields().clear();
      last.sortOrders().clear();
      QueryData data = new QueryData(new ArrayList<String>(), new ArrayList<String>(),
                                     new Predicate(), sortfields, sortorders);
      data.setSetOps(operands, ops, alls);
      data.setExplain(explain);
      return data;
   }

   private QueryData selectQuery() {
      lex.eatKeyword("select");
      boolean distinct = false;
      if (lex.matchKeyword("distinct")) {
         lex.eatKeyword("distinct");
         distinct = true;
      }
      List<String> fields = new ArrayList<String>();
      List<String> plainfields = new ArrayList<String>();
      List<String> aggfuncs = new ArrayList<String>();
      List<String> aggfields = new ArrayList<String>();
      boolean selectAll = false;
      if (lex.matchDelim('*')) {      // select * : all fields of the tables
         lex.eatDelim('*');
         selectAll = true;
      }
      else
         selectList(fields, plainfields, aggfuncs, aggfields);
      lex.eatKeyword("from");
      Collection<String> tables = tableList();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      List<String> groupfields = new ArrayList<String>();
      if (lex.matchKeyword("group")) {
         lex.eatKeyword("group");
         lex.eatKeyword("by");
         groupList(groupfields);
      }
      List<String> sortfields = new ArrayList<String>();
      List<Boolean> sortorders = new ArrayList<Boolean>();
      if (lex.matchKeyword("order")) {
         lex.eatKeyword("order");
         lex.eatKeyword("by");
         orderByList(sortfields, sortorders);
      }
      if (selectAll && !groupfields.isEmpty())
         throw new BadSyntaxException("select * cannot be combined with group by");
      // every plain field must be a group by field
      if (!groupfields.isEmpty() || !aggfuncs.isEmpty())
         for (String f : plainfields)
            if (!groupfields.contains(f))
               throw new BadSyntaxException("field " + f
                     + " must appear in the group by clause or inside an aggregate function");
      QueryData data = new QueryData(fields, tables, pred, sortfields, sortorders,
                                     groupfields, aggfuncs, aggfields);
      data.setDistinct(distinct);
      data.setSelectAll(selectAll);
      return data;
   }

   private void groupList(List<String> groupfields) {
      String f = field();
      if (!groupfields.contains(f))
         groupfields.add(f);
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         groupList(groupfields);
      }
   }

   private void orderByList(List<String> sortfields, List<Boolean> sortorders) {
      sortfields.add(field());
      boolean ascending = true;
      if (lex.matchKeyword("asc"))
         lex.eatKeyword("asc");
      else if (lex.matchKeyword("desc")) {
         lex.eatKeyword("desc");
         ascending = false;
      }
      sortorders.add(ascending);
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         orderByList(sortfields, sortorders);
      }
   }
   
   private void selectList(List<String> fields, List<String> plainfields,
                           List<String> aggfuncs, List<String> aggfields) {
      String name = field();
      if (lex.matchDelim('(')) {
         if (!(name.equals("count") || name.equals("sum") || name.equals("avg")
               || name.equals("min") || name.equals("max")))
            throw new BadSyntaxException();
         lex.eatDelim('(');
         String fld;
         if (name.equals("count") && lex.matchDelim('*')) {   // count(*)
            lex.eatDelim('*');
            fld = "*";
         }
         else
            fld = field();
         lex.eatDelim(')');
         aggfuncs.add(name);
         aggfields.add(fld);
         if (!fields.contains(name + "of" + fld))
            fields.add(name + "of" + fld);
      }
      else {
         fields.add(name);
         plainfields.add(name);
      }
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         selectList(fields, plainfields, aggfuncs, aggfields);
      }
   }
   
   private Collection<String> tableList() {
      Collection<String> L = new ArrayList<String>();
      L.add(lex.eatId());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(tableList());
      }
      return L;
   }
   
// Methods for parsing the various update commands
   
   public Object updateCmd() {
      if (lex.matchKeyword("insert"))
         return insert();
      else if (lex.matchKeyword("delete"))
         return delete();
      else if (lex.matchKeyword("update"))
         return modify();
      else
         return create();
   }
   
   private Object create() {
      lex.eatKeyword("create");
      if (lex.matchKeyword("table"))
         return createTable();
      else if (lex.matchKeyword("view"))
         return createView();
      else
         return createIndex();
   }
   
// Method for parsing delete commands
   
   public DeleteData delete() {
      lex.eatKeyword("delete");
      lex.eatKeyword("from");
      String tblname = lex.eatId();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new DeleteData(tblname, pred);
   }
   
// Methods for parsing insert commands
   
   public InsertData insert() {
      lex.eatKeyword("insert");
      lex.eatKeyword("into");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      List<String> flds = fieldList();
      lex.eatDelim(')');
      lex.eatKeyword("values");
      lex.eatDelim('(');
      List<Constant> vals = constList();
      lex.eatDelim(')');
      return new InsertData(tblname, flds, vals);
   }
   
   private List<String> fieldList() {
      List<String> L = new ArrayList<String>();
      L.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(fieldList());
      }
      return L;
   }
   
   private List<Constant> constList() {
      List<Constant> L = new ArrayList<Constant>();
      L.add(constant());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(constList());
      }
      return L;
   }
   
// Method for parsing modify commands
   
   public ModifyData modify() {
      lex.eatKeyword("update");
      String tblname = lex.eatId();
      lex.eatKeyword("set");
      String fldname = field();
      lex.eatDelim('=');
      Expression newval = expression();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new ModifyData(tblname, fldname, newval, pred);
   }
   
// Method for parsing create table commands
   
   public CreateTableData createTable() {
      lex.eatKeyword("table");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      Schema sch = fieldDefs();
      lex.eatDelim(')');
      return new CreateTableData(tblname, sch);
   }
   
   private Schema fieldDefs() {
      Schema schema = fieldDef();
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         Schema schema2 = fieldDefs();
         schema.addAll(schema2);
      }
      return schema;
   }
   
   private Schema fieldDef() {
      String fldname = field();
      return fieldType(fldname);
   }
   
   private Schema fieldType(String fldname) {
      Schema schema = new Schema();
      if (lex.matchKeyword("int")) {
         lex.eatKeyword("int");
         schema.addIntField(fldname);
      }
      else {
         lex.eatKeyword("varchar");
         lex.eatDelim('(');
         int strLen = lex.eatIntConstant();
         lex.eatDelim(')');
         schema.addStringField(fldname, strLen);
      }
      return schema;
   }
   
// Method for parsing create view commands
   
   public CreateViewData createView() {
      lex.eatKeyword("view");
      String viewname = lex.eatId();
      lex.eatKeyword("as");
      QueryData qd = query();
      return new CreateViewData(viewname, qd);
   }
   
   
//  Method for parsing create index commands
   
   public CreateIndexData createIndex() {
	   lex.eatKeyword("index");
	   String idxname = lex.eatId();
	   lex.eatKeyword("on");
	   String tblname = lex.eatId();
	   lex.eatDelim('(');
	   String fldname = field();
	   lex.eatDelim(')');
	   String idxtype = "hash";
	   if (lex.matchKeyword("using")) {
	      lex.eatKeyword("using");
	      if (lex.matchKeyword("btree")) {
	         lex.eatKeyword("btree");
	         idxtype = "btree";
	      } else {
	         lex.eatKeyword("hash");
	      }
	   }
	   return new CreateIndexData(idxname, tblname, fldname, idxtype);
	}
}

