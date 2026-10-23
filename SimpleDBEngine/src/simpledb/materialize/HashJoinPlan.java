package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.tx.Transaction;


/**
 * The Plan class for the <i>hashjoin</i> operator.
 * @author Edward Sciore
 */
public class HashJoinPlan implements Plan {
    private final Transaction tx;
    private final Plan p1, p2;
    private final String fldname1, fldname2;
    private final Schema sch = new Schema();

    public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
        this.tx = tx;

        this.p1 = p1;
        this.p2 = p2;

        this.fldname1 = fldname1;
        this.fldname2 = fldname2;

        sch.addAll(p1.schema());
        sch.addAll(p2.schema());
    }

    @Override
    public Scan open() {
        return new HashJoinScan(tx, p1, p2, fldname1, fldname2);
    }

    /**
     * Return the number of blocks accessed during the join.
     * Partition-based hash join reads each input once and writes its
     * partitions, then reads every partition once more:
     * <pre> 3 * (B(p1) + B(p2)) </pre>
     * (the standard estimate, assuming each right partition fits in memory).
     * @see simpledb.plan.Plan#blocksAccessed()
     */
    @Override
    public int blocksAccessed() {
        return 3 * (p1.blocksAccessed() + p2.blocksAccessed());
    }

    /**
     * Return the number of records in the join.
     * Assuming uniform distribution, the formula is:
     * <pre> R(join(p1,p2)) = R(p1)*R(p2)/max{V(p1,F1),V(p2,F2)}</pre>
     * @see simpledb.plan.Plan#recordsOutput()
     */
    @Override
    public int recordsOutput() {
        int maxvals = Math.max(1, Math.max(p1.distinctValues(fldname1),
                p2.distinctValues(fldname2)));
        long out = ((long) p1.recordsOutput() * p2.recordsOutput()) / maxvals;
        return (int) Math.min(Integer.MAX_VALUE, out);
    }

    /**
     * Estimate the distinct number of field values in the join.
     * Since the join does not increase or decrease field values,
     * the estimate is the same as in the appropriate underlying query.
     * @see simpledb.plan.Plan#distinctValues(java.lang.String)
     */
    @Override
    public int distinctValues(String fldname) {
        if (p1.schema().hasField(fldname))
            return p1.distinctValues(fldname);
        else
            return p2.distinctValues(fldname);
    }

    @Override
    public Schema schema() {
        return sch;
    }

    @Override
    public String describe() {
        return "HashJoin [" + fldname1 + " = " + fldname2 + "]";
    }

    @Override
    public java.util.List<Plan> children() {
        return java.util.Arrays.asList(p1, p2);
    }
}
