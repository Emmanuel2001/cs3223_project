package simpledb.opt;

// -Dsimpledb.joins=index,hash,merge,nl keeps only the listed join methods (unset = all)
public class JoinMethods {
   public static boolean INDEX = true;
   public static boolean HASH  = true;
   public static boolean MERGE = true;
   public static boolean TRACE = false;

   static {
      String list = System.getProperty("simpledb.joins");
      if (list != null) {
         INDEX = HASH = MERGE = false;
         for (String tok : list.split(",")) {
            switch (tok.trim().toLowerCase()) {
               case "index": INDEX = true; break;
               case "hash":  HASH  = true; break;
               case "merge": MERGE = true; break;
               default: break;   // "nl" (or anything else): nested loops only
            }
         }
      }
      TRACE = Boolean.getBoolean("simpledb.tracejoins");
   }
}
