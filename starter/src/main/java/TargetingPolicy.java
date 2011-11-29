import java.util.HashMap;
import java.util.Map;

/**
 * TODO -- probably need some multiplier/ratio that caps the number of ants
 * that will be targeted (i.e. above and beyond the "per-target assignment limit")
 * Author: evan.pollan
 * Date: 11/22/11
 * Time: 8:03 PM
 */
public class TargetingPolicy {

    public interface TargetingHandler {
        boolean move(Tile ant, Tile nextTile, Tile finalDestination, TargetingPolicy.Type type);
    }

    public static enum Type {
        Food,
        EnemyHill,
        EnemyAnt,
        UnseenTile
    }

    private static Map<Type, TargetingPolicy> _policies = new HashMap<Type, TargetingPolicy>();

    public static void clearAssignments() {
        for (TargetingPolicy p : _policies.values()) {
            for (ReferenceInt assignment : p._assignments.values()) {
                assignment.Value = 0;
            }
            p._totalAssignments = 0;
            p._totalAssignmentsLimit = null;
        }
    }

    public static void add(Type type, int perTargetAssignmentLimit, Integer perAntRouteLimit, Integer antLimit) {
        _policies.put(type, new TargetingPolicy(type, perTargetAssignmentLimit, perAntRouteLimit, antLimit));
    }

    public static TargetingPolicy get(Type type) {
        return _policies.get(type);
    }

    private Integer _perAntRouteLimit;
    private int _perTargetAssignmentLimit;
    private int _totalAssignments = 0;
    private Integer _totalAssignmentsLimit = null;
    private Integer _antLimit = null;
    private Map<Tile, ReferenceInt> _assignments;
    private Type _type;

    // Map-storable reference to a mutable int.  Integer wraps an immutable value (relative
    // to what's stored in the Integer instance itself)
    private static class ReferenceInt {
        public int Value = 0;
    }

    /**
     * Constructor
     *
     * @param type                     policy type
     * @param perTargetAssignmentLimit maximum number of ants that can be 'tasked' to a particular
     *                                 target
     * @param perAntRouteLimit         maximum number of routes that should be considered per ant
     *                                 to limit computational complexity when there are large numbers
     *                                 of ants and/or large numbers of targets
     * @param antLimit                 max number of ants that should be targeted
     */
    private TargetingPolicy(Type type, int perTargetAssignmentLimit, Integer perAntRouteLimit, Integer antLimit) {
        _type = type;
        _assignments = new HashMap<Tile, ReferenceInt>();
        _perTargetAssignmentLimit = perTargetAssignmentLimit;
        _perAntRouteLimit = perAntRouteLimit;
        _antLimit = antLimit;
    }

    public int getPerTargetAssignmentLimit() {
        return _perTargetAssignmentLimit;
    }

    public Integer getPerAntRouteLimit() {
        return _perAntRouteLimit;
    }

    public Integer getAntLimit() {
        return _antLimit;
    }

    public void assign(Tile ant, Tile target) {
        ReferenceInt assignmentCount = getAssignmentCount(target);
        if (assignmentCount.Value < _perTargetAssignmentLimit) {
            assignmentCount.Value++;
        } else {
            throw new RuntimeException(String.format("Target tile [R=%d,C=%d] over-assigned",
                                                     target.getRow(), target.getCol()));
        }
        ++_totalAssignments;
    }

    public boolean canAssign(Tile ant, Tile target) {
        return getAssignmentCount(target).Value < _perTargetAssignmentLimit;
    }

    public boolean totalAssignmentsLimitReached(int targetCount) {
        if (_totalAssignmentsLimit == null) {
            _totalAssignmentsLimit = new Integer(targetCount * _perTargetAssignmentLimit);
        }
        return _totalAssignments >= _totalAssignmentsLimit.intValue() ||
                (_antLimit != null && _totalAssignments >= _antLimit);
    }

    public int getTotalAssignmentsLimit(int targetCount) {
        if (_totalAssignmentsLimit == null) {
            _totalAssignmentsLimit = new Integer(targetCount * _perTargetAssignmentLimit);
        }
        return _totalAssignmentsLimit.intValue();
    }

    public Type getType() {
        return _type;
    }

    @Override
    public String toString() {
        return _type.toString() + "Policy";
    }

    private ReferenceInt getAssignmentCount(Tile target) {
        ReferenceInt assignmentCount = _assignments.get(target);
        if (assignmentCount == null) {
            assignmentCount = new ReferenceInt();
            _assignments.put(target, assignmentCount);
        }
        return assignmentCount;
    }
}
