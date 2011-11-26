import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * TODO -- probably need some multiplier/ratio that caps the number of ants
 * that will be targeted (i.e. above and beyond the "per-target assignment limit")
 * Author: evan.pollan
 * Date: 11/22/11
 * Time: 8:03 PM
 */
public class TargetingPolicy {

    private static List<TargetingPolicy> _policies = new LinkedList<TargetingPolicy>();

    public static void clearAssignments() {
        for (TargetingPolicy p : _policies) {
            for (ReferenceInt assignment : p._assignments.values()) {
                assignment.Value = 0;
            }
            p._totalAssignments = 0;
            p._totalAssignmentsLimit = null;
        }
    }

    private Integer _perAntRouteLimit;
    private int _perTargetAssignmentLimit;
    private int _totalAssignments = 0;
    private Integer _totalAssignmentsLimit = null;
    private Map<Tile, ReferenceInt> _assignments;
    private String _name;

    // Map-storable reference to a mutable int.  Integer wraps an immutable value (relative
    // to what's stored in the Integer instance itself)
    private static class ReferenceInt {
        public int Value = 0;
    }

    /**
     * Constructor
     * @param name policy name, used for descriptive logging purposes
     * @param perTargetAssignmentLimit maximum number of ants that can be 'tasked' to a particular
     *                        target
     * @param perAntRouteLimit maximum number of routes that should be considered per ant
     *                         to limit computational complexity when there are large numbers
     *                         of ants and/or large numbers of targets
     */
    public TargetingPolicy(String name, int perTargetAssignmentLimit, Integer perAntRouteLimit) {
        _name = name;
        _assignments = new HashMap<Tile, ReferenceInt>();
        _perTargetAssignmentLimit = perTargetAssignmentLimit;
        _perAntRouteLimit = perAntRouteLimit;
        _policies.add(this);
    }

    public int getPerTargetAssignmentLimit() {
        return _perTargetAssignmentLimit;
    }

    public Integer getPerAntRouteLimit() {
        return _perAntRouteLimit;
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
        return _totalAssignments >= _totalAssignmentsLimit.intValue();
    }

    public int getTotalAssignmentsLimit(int targetCount) {
        if (_totalAssignmentsLimit == null) {
            _totalAssignmentsLimit = new Integer(targetCount * _perTargetAssignmentLimit);
        }
        return _totalAssignmentsLimit.intValue();
    }



    @Override
    public String toString() {
        return _name;
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
