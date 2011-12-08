import java.util.HashMap;
import java.util.Map;

/**
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
        UnseenTile,
        Unmanaged,
        DefensiveStation
    }

    private static Map<Type, TargetingPolicy> _policies = new HashMap<Type, TargetingPolicy>();

    public static void clearAssignments() {
        for (TargetingPolicy p : _policies.values()) {
            for (X.ReferenceInt assignment : p._assignments.values()) {
                assignment.Value = 0;
            }
            p._totalAssignments = 0;
            p._totalAssignmentsLimit = null;
        }
    }

    public static void add(Type type,
                           int perTargetAssignmentLimit,
                           Integer perAntRouteLimit,
                           Integer antLimit,
                           Integer perTargetAssignmentFloor) {
        _policies.put(type, new TargetingPolicy(type, perTargetAssignmentLimit,
                                                perAntRouteLimit, antLimit, perTargetAssignmentFloor));
    }

    private static TargetingPolicy UNMANAGED =
            new TargetingPolicy(Type.Unmanaged, Integer.MAX_VALUE, null, null, null);

    static {
        _policies.put(Type.Unmanaged, UNMANAGED);
    }

    public static TargetingPolicy get(Type type) {
        return _policies.get(type);
    }

    private Integer _perAntRouteLimit;
    private int _perTargetAssignmentLimit;
    private int _totalAssignments = 0;
    private Integer _totalAssignmentsLimit = null;
    private Integer _antLimit = null;
    private Integer _perTargetAssignmentFloor = null;
    private Map<Tile, X.ReferenceInt> _assignments;
    private Type _type;

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
     * @param perTargetAssignmentFloor optional, minimum number of ants that should be targeted before
     *                                 considering a different target
     */
    private TargetingPolicy(Type type,
                            int perTargetAssignmentLimit,
                            Integer perAntRouteLimit,
                            Integer antLimit,
                            Integer perTargetAssignmentFloor) {
        _type = type;
        _assignments = new HashMap<Tile, X.ReferenceInt>();
        _perTargetAssignmentLimit = perTargetAssignmentLimit;
        _perTargetAssignmentFloor = perTargetAssignmentFloor;
        _perAntRouteLimit = perAntRouteLimit;
        _antLimit = antLimit;
    }

    public int getPerTargetAssignmentLimit() {
        return _perTargetAssignmentLimit;
    }

    public Integer getPerTargetAssignmentFloor() {
        return _perTargetAssignmentFloor;
    }

    public Integer getPerAntRouteLimit() {
        return _perAntRouteLimit;
    }

    public Integer getAntLimit() {
        return _antLimit;
    }

    public void assign(Tile ant, Tile target) {
        X.ReferenceInt assignmentCount = getAssignmentCount(target);
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

    public boolean needsMoreAssignments(Tile target) {
        return _perTargetAssignmentFloor == null ||
               getAssignmentCount(target).Value < _perTargetAssignmentFloor;
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

    private X.ReferenceInt getAssignmentCount(Tile target) {
        X.ReferenceInt assignmentCount = _assignments.get(target);
        if (assignmentCount == null) {
            assignmentCount = new X.ReferenceInt();
            _assignments.put(target, assignmentCount);
        }
        return assignmentCount;
    }
}
