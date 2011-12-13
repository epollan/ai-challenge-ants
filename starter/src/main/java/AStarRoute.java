import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * TODO
 * Author: evan.pollan
 * Date: 11/20/11
 * Time: 4:13 PM
 */
public class AStarRoute extends Route {

    private List<Tile> _route;
    private Tile _nextTile;
    private ArrayList<Tile> _neighborsBuffer = new ArrayList<Tile>(4); // at most 4 neighbors

    public AStarRoute(Tile start, Tile end)
            throws NoRouteException {
        super(start, end);
        _route = calculateRoute();
        _distance = _route.size();
        if (_nextTile == null) {
            throw new RuntimeException(
                    String.format("Unexpected route of length %d from [%s] to [%s], but missing 'nextTile'",
                                  _distance, _start, _end));
        }
    }

    public Iterable<Tile> routeTiles() {
        return _route;
    }

    public Tile nextTile() {
        return _nextTile;
    }

    @Override
    public String toString() {
        return String.format("[%s]-->[%s] (d=%d, next=[%s])", _start, _end, _distance, _nextTile);
    }

    private List<Tile> calculateRoute()
            throws NoRouteException {
        Set<Tile> closedSet = new HashSet<Tile>();
        Set<Tile> openSet = new HashSet<Tile>();
        openSet.add(_start);
        Map<Tile, Tile> cameFrom = new HashMap<Tile, Tile>();

        Map<Tile, Double> gScoreMap = new HashMap<Tile, Double>();
        gScoreMap.put(_start, 0d);
        Map<Tile, Double> hScoreMap = new HashMap<Tile, Double>();
        hScoreMap.put(_start, heuristic(_start, _end));
        Map<Tile, Double> fScoreMap = new HashMap<Tile, Double>();
        fScoreMap.put(_start, hScoreMap.get(_start));

        while (openSet.size() > 0) {
            Tile x = null;
            double lowestFScore = Double.MAX_VALUE;
            for (Tile t : openSet) {
                double fScore = fScoreMap.get(t);
                if (fScore < lowestFScore) {
                    x = t;
                    lowestFScore = fScore;
                }
            }
            if (_end.equals(x)) {
                List<Tile> route = new LinkedList<Tile>();
                reconstructPath(cameFrom, _end, route);
                return route;
            }
            openSet.remove(x);
            closedSet.add(x);

            for (Tile y : neighbors(x)) {
                if (closedSet.contains(y)) {
                    continue;
                }
                double tentativeGScore = gScoreMap.get(x) + 1.0; // neighbors all 1 square away
                boolean tentativeIsBetter = false;

                if (!openSet.contains(y)) {
                    openSet.add(y);
                    tentativeIsBetter = true;
                } else if (tentativeGScore < gScoreMap.get(y)) {
                    tentativeIsBetter = true;
                }

                if (tentativeIsBetter) {
                    cameFrom.put(y, x);
                    gScoreMap.put(y, tentativeGScore);
                    double yHeuristic = heuristic(y, _end);
                    hScoreMap.put(y, yHeuristic);
                    fScoreMap.put(y, tentativeGScore + yHeuristic);
                }
            }
        }
        throw new NoRouteException();
    }

    // Best case distance heuristic, dX + dY
    private double heuristic(Tile start, Tile goal) {
        return Registry.Instance.getDistance(start, goal);
    }

    // Backtrack from the currentNode using the cameFrom map to construct
    // an ordered route of Tiles representing the path from _start to
    // _currentNode
    private void reconstructPath(Map<Tile, Tile> cameFrom,
                                 Tile currentNode,
                                 List<Tile> route) {
        Tile predecessor = cameFrom.get(currentNode);
        if (predecessor != null) {
            // Continuously update prececessor as we backtrack along the route
            _nextTile = currentNode;
            reconstructPath(cameFrom, predecessor, route);
            route.add(currentNode);
        }
    }

    private Iterable<Tile> neighbors(Tile t) {
        _neighborsBuffer.clear();
        for (Aim aim : Aim.values()) {
            Tile neighbor = Registry.Instance.getTile(t, aim);
            // We can travel to a neighboring tile if it's unoccupied, or if it's our
            // end goal
            //if (_ants.getIlk(neighbor).isUnoccupied() || neighbor.equals(_end)) {
            if (Registry.Instance.getIlk(neighbor).isPassable() || neighbor.equals(_end)) {
                _neighborsBuffer.add(neighbor);
            }
        }
        return _neighborsBuffer;
    }
}
