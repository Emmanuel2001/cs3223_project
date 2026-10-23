package embedded;

import java.io.*;
import java.sql.*;
import java.util.*;

import simpledb.jdbc.embedded.EmbeddedDriver;
import simpledb.opt.JoinMethods;

public class ExperimentRunner {
   // name, index, hash, merge  (nested loops is always the last resort)
   private static final Object[][] CONFIGS = {
      {"default",        true,  true,  true },
      {"index",          true,  false, false},
      {"hash",           false, true,  false},
      {"merge",          false, false, true },
      {"nested-loops",   false, false, false},
   };
   private static final String[][] CONFIG_LABELS = {
      {"default", "optimizer free to choose (index, hash, sort-merge, then nested loops)"},
      {"index", "index join where an index exists, else nested loops"},
      {"hash", "hash join only"},
      {"merge", "sort-merge join only"},
      {"nested-loops", "nested-loops join only"},
   };

   private static final String Q1 =
      "select sname, dname from student, dept where majorid = did";
   private static final String Q2 =
      "select sname, title from student, enroll, section, course "
    + "where sid = studentid and sectionid = sectid and courseid = cid";

   // size of the larger database
   // base sizes times -Dexp.scale (default 4): 2400 students, 4800 enrolments, ...
   private static final int SCALE = Integer.getInteger("exp.scale", 4);
   private static final int NDEPT = 20, NSTUDENT = 600 * SCALE, NCOURSE = 40 * SCALE,
                            NSECTION = 100 * SCALE, NENROLL = 1200 * SCALE;

   public static void main(String[] args) throws Exception {
      if (args.length > 0 && args[0].equals("one")) {
         // one DATASET EXPERIMENT CONFIG REPS WARMUPS
         Connection conn = open(args[1], false);
         Result r = measure(conn, args[2].equals("1") ? Q1 : Q2, config(args[3]),
                            Integer.parseInt(args[4]), Integer.parseInt(args[5]));
         System.out.println("RESULT|" + args[1] + "|" + args[2] + "|" + args[3] + "|" + r.csv());
         for (String line : r.plan)
            System.out.println("PLAN|" + args[1] + "|" + args[2] + "|" + args[3] + "|" + line);
         conn.close();
         return;
      }
      if (args.length > 0 && args[0].equals("build")) {
         open(args[1], true).close();
         return;
      }

      PrintWriter csv = new PrintWriter(new FileWriter("experiment_results.csv"));
      csv.println("dataset,experiment,config,rows,median_ms,mean_ms,min_ms,max_ms");
      String[] datasets = {"sample", "large"};
      int[] reps    = {200, 3};
      int[] warmups = {30, 1};
      for (int d = 0; d < datasets.length; d++) {
         Connection conn = open(datasets[d], true);
         for (int exp = 1; exp <= 2; exp++) {
            String sql = exp == 1 ? Q1 : Q2;
            System.out.println();
            System.out.println("=== " + datasets[d] + " database, Experiment " + exp + " ===");
            System.out.println(sql);
            for (Object[] cfg : CONFIGS) {
               Result r = measure(conn, sql, cfg, reps[d], warmups[d]);
               System.out.printf("%-14s rows=%-5d median=%9.3f ms  mean=%9.3f  min=%9.3f  max=%9.3f%n",
                                 cfg[0], r.rows, r.median, r.mean, r.min, r.max);
               csv.println(datasets[d] + "," + exp + "," + cfg[0] + "," + r.csv());
               for (String line : r.plan)
                  System.out.println("      " + line);
            }
         }
         conn.close();
      }
      csv.close();
      System.out.println();
      System.out.println("Results written to experiment_results.csv");
   }

   private static Object[] config(String name) {
      for (Object[] c : CONFIGS)
         if (c[0].equals(name))
            return c;
      throw new IllegalArgumentException("unknown config " + name);
   }

   private static class Result {
      int rows;
      double median, mean, min, max;
      List<String> plan = new ArrayList<String>();
      String csv() {
         return String.format(Locale.ROOT, "%d,%.3f,%.3f,%.3f,%.3f", rows, median, mean, min, max);
      }
   }

   private static Result measure(Connection conn, String sql, Object[] cfg, int reps, int warmups)
         throws SQLException {
      JoinMethods.INDEX = (Boolean) cfg[1];
      JoinMethods.HASH  = (Boolean) cfg[2];
      JoinMethods.MERGE = (Boolean) cfg[3];
      Result r = new Result();
      try (Statement st = conn.createStatement()) {
         try (ResultSet rs = st.executeQuery("explain " + sql)) {
            while (rs.next())
               r.plan.add(rs.getString("plan"));
         }
         for (int i = 0; i < warmups; i++)
            runOnce(st, sql);
         double[] t = new double[reps];
         for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            r.rows = runOnce(st, sql);
            t[i] = (System.nanoTime() - t0) / 1e6;
         }
         Arrays.sort(t);
         r.median = t[reps / 2];
         r.min = t[0];
         r.max = t[reps - 1];
         double sum = 0;
         for (double x : t)
            sum += x;
         r.mean = sum / reps;
      }
      return r;
   }

   private static int runOnce(Statement st, String sql) throws SQLException {
      int n = 0;
      try (ResultSet rs = st.executeQuery(sql)) {
         while (rs.next())
            n++;
      }
      return n;
   }

   private static Connection open(String dataset, boolean rebuild) throws Exception {
      String dir = "experimentdb_" + dataset;
      boolean exists = new File(dir).exists();
      if (rebuild && exists) {
         deleteTree(new File(dir));
         exists = false;
      }
      Connection conn = new EmbeddedDriver().connect("jdbc:simpledb:" + dir, null);
      if (!exists) {
         try (Statement st = conn.createStatement()) {
            if (dataset.equals("sample"))
               loadSample(st);
            else
               loadLarge(st);
         }
      }
      return conn;
   }

   private static void deleteTree(File f) {
      if (!f.getName().startsWith("experimentdb_"))
         throw new IllegalStateException("refusing to delete " + f);
      File[] kids = f.listFiles();
      if (kids != null)
         for (File k : kids)
            if (k.isDirectory()) deleteTree(k); else k.delete();
      f.delete();
   }

   private static void createTables(Statement st) throws SQLException {
      st.executeUpdate("create table STUDENT(SId int, SName varchar(10), MajorId int, GradYear int)");
      st.executeUpdate("create index idx_majorid on student(majorid)");
      st.executeUpdate("create table DEPT(DId int, DName varchar(8))");
      st.executeUpdate("create table COURSE(CId int, Title varchar(20), DeptId int)");
      st.executeUpdate("create table SECTION(SectId int, CourseId int, Prof varchar(8), YearOffered int)");
      st.executeUpdate("create table ENROLL(EId int, StudentId int, SectionId int, Grade varchar(2))");
   }

   private static void loadSample(Statement st) throws SQLException {
      createTables(st);
      String[] stud = {"(1, 'joe', 10, 2021)", "(2, 'amy', 20, 2020)", "(3, 'max', 10, 2022)",
         "(4, 'sue', 20, 2022)", "(5, 'bob', 30, 2020)", "(6, 'kim', 20, 2020)",
         "(7, 'art', 30, 2021)", "(8, 'pat', 20, 2019)", "(9, 'lee', 10, 2021)"};
      for (String v : stud)
         st.executeUpdate("insert into STUDENT(SId, SName, MajorId, GradYear) values " + v);
      String[] dept = {"(10, 'compsci')", "(20, 'math')", "(30, 'drama')"};
      for (String v : dept)
         st.executeUpdate("insert into DEPT(DId, DName) values " + v);
      String[] course = {"(12, 'db systems', 10)", "(22, 'compilers', 10)", "(32, 'calculus', 20)",
         "(42, 'algebra', 20)", "(52, 'acting', 30)", "(62, 'elocution', 30)"};
      for (String v : course)
         st.executeUpdate("insert into COURSE(CId, Title, DeptId) values " + v);
      String[] sect = {"(13, 12, 'turing', 2018)", "(23, 12, 'turing', 2019)", "(33, 32, 'newton', 2019)",
         "(43, 32, 'einstein', 2017)", "(53, 62, 'brando', 2018)"};
      for (String v : sect)
         st.executeUpdate("insert into SECTION(SectId, CourseId, Prof, YearOffered) values " + v);
      String[] enr = {"(14, 1, 13, 'A')", "(24, 1, 43, 'C')", "(34, 2, 43, 'B+')",
         "(44, 4, 33, 'B')", "(54, 4, 53, 'A')", "(64, 6, 53, 'A')"};
      for (String v : enr)
         st.executeUpdate("insert into ENROLL(EId, StudentId, SectionId, Grade) values " + v);
   }

   private static void loadLarge(Statement st) throws SQLException {
      createTables(st);
      Random r = new Random(42);
      int[] deptIds = new int[NDEPT];
      for (int i = 0; i < NDEPT; i++) {
         deptIds[i] = 10 * (i + 1);
         st.executeUpdate("insert into DEPT(DId, DName) values (" + deptIds[i] + ", 'dept" + (i + 1) + "')");
      }
      for (int i = 1; i <= NSTUDENT; i++)
         st.executeUpdate("insert into STUDENT(SId, SName, MajorId, GradYear) values (" + i + ", 's" + i + "', "
                          + deptIds[r.nextInt(NDEPT)] + ", " + (2018 + r.nextInt(6)) + ")");
      for (int i = 1; i <= NCOURSE; i++)
         st.executeUpdate("insert into COURSE(CId, Title, DeptId) values (" + i + ", 'course" + i + "', "
                          + deptIds[r.nextInt(NDEPT)] + ")");
      for (int i = 1; i <= NSECTION; i++)
         st.executeUpdate("insert into SECTION(SectId, CourseId, Prof, YearOffered) values (" + i + ", "
                          + (1 + r.nextInt(NCOURSE)) + ", 'prof" + r.nextInt(20) + "', " + (2015 + r.nextInt(8)) + ")");
      String[] grades = {"A", "B", "C", "D"};
      for (int i = 1; i <= NENROLL; i++)
         st.executeUpdate("insert into ENROLL(EId, StudentId, SectionId, Grade) values (" + i + ", "
                          + (1 + r.nextInt(NSTUDENT)) + ", " + (1 + r.nextInt(NSECTION)) + ", '" + grades[r.nextInt(4)] + "')");
   }
}
