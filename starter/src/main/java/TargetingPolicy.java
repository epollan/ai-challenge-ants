import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * TODO
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
        }
    }

    private Integer _perAntRouteLimit;
    private int _assignmentLimit;
    private Map<Tile, ReferenceInt> _assignments;
    private String _name;

    private static class ReferenceInt {
        public int Value = 0;
    }

    public TargetingPolicy(String name, int assignmentLimit, Integer perAntRouteLimit) {
        _name = name;
        _assignments = new HashMap<Tile, ReferenceInt>();
        _assignmentLimit = assignmentLimit;
        _perAntRouteLimit = perAntRouteLimit;
        _policies.add(this);
    }

    public int getAssignmentLimit() {
        return _assignmentLimit;
    }

    public Integer getPerAntRouteLimit() {
        return _perAntRouteLimit;
    }

    public void assign(Tile ant, Tile target) {
        ReferenceInt assignmentCount = getAssignmentCount(target);
        if (assignmentCount.Value < _assignmentLimit) {
            assignmentCount.Value++;
        } else {
            throw new RuntimeException(String.format("Target tile [R=%d,C=%d] over-assigned",
                                                     target.getRow(), target.getCol()));
        }
    }

    public boolean canAssign(Tile ant, Tile target) {
        return getAssignmentCount(target).Value < _assignmentLimit;
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
