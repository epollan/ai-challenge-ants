import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Author: evan.pollan
 * Date: 11/28/11
 * Time: 8:04 AM
 */
public class TargetingHistory {

    private Map<Tile, Breadcrumb> _map = new HashMap<Tile, Breadcrumb>();
    private int _turn;
    private Ants _ants;
    private static final Logger _log = Logger.getLogger(TargetingHistory.class);

    public TargetingHistory(Ants ants) {
        _ants = ants;
    }

    public void syncState(int turn) {
        _turn = turn;
        for (Map.Entry<Tile, Breadcrumb> entry : new ArrayList<Map.Entry<Tile, Breadcrumb>>(_map.entrySet())) {
            boolean present = true;
            switch (entry.getValue().Type) {
                case EnemyAnt:
                    present = _ants.getEnemyAnts().contains(entry.getValue().Destination);
                    break;
                case EnemyHill:
                    present = _ants.getEnemyHills().contains(entry.getValue().Destination);
                    break;
                case Food:
                    present = _ants.getFoodTiles().contains(entry.getValue().Destination);
                    break;
                default:
            }
            if (!present) {
                _map.remove(entry.getKey());
            }
        }
    }

    public void create(Tile current,
                       Tile destination,
                       TargetingPolicy.Type type,
                       AStarRoute route) {
        for (Tile nextHop : route.routeTiles()) {
            if (nextHop.equals(destination)) {
                break;
            }
            RoutedBreadcrumb breadcrumb = new RoutedBreadcrumb();
            breadcrumb.Destination = destination;
            breadcrumb.Type = type;
            breadcrumb.TurnCreated = _turn;
            breadcrumb.Next = nextHop;
            breadcrumb.UsageCount = 0;
            for (Tile neighbor : getBreadcrumbNeighborhood(current, type)) {
                Breadcrumb preexisting = _map.get(neighbor);
                if (preexisting == null) {
                    UnroutedBreadcrumb hint = new UnroutedBreadcrumb();
                    hint.Destination = destination;
                    hint.Type = type;
                    hint.TurnCreated = _turn;
                    hint.RouteInfluencer = current;
                    _map.put(neighbor, hint);
                }
            }
            _map.put(current, breadcrumb);
            current = nextHop;
        }
    }

    public void followBreadcrumb(Tile ant, TargetingPolicy.TargetingHandler handler) {
        Breadcrumb crumb = _map.get(ant);
        if (crumb instanceof UnroutedBreadcrumb) {
            Tile influencer = ((UnroutedBreadcrumb) crumb).RouteInfluencer;
            RoutedBreadcrumb influence = (RoutedBreadcrumb) _map.get(influencer);
            if (influence == null) {
                _log.info(String.format("Removing unrouted breadcrumb at [%s] because its influencer at [%s] is missing",
                                        ant, influencer));
                _map.remove(ant);
            } else {
                // We need to calculate our own route from this tile
                try {
                    AStarRoute route = new AStarRoute(_ants, ant, influence.Destination);
                    if (handler.move(ant, route.nextTile(), route.getEnd(), influence.Type)) {
                        if (_log.isDebugEnabled()) {
                            _log.debug(String.format("Picked up %s targeting for [%s] based on route influence",
                                                     influence.Type, influence.Destination));
                        }
                        create(ant, influence.Destination, influence.Type, route);
                    }
                } catch (NoRouteException ex) {
                    _log.info(String.format("Cannot route from [%s] to breadcrumb-influenced destination [%s]",
                                            ant, influence.Destination));
                }
            }
        } else if (crumb instanceof RoutedBreadcrumb) {
            RoutedBreadcrumb routed = (RoutedBreadcrumb) crumb;
            if (handler.move(ant, routed.Next, routed.Destination, routed.Type)) {
                routed.UsageCount++;
                if (_log.isDebugEnabled()) {
                    _log.debug(String.format("Leveraging previously computed route for %s at [%s]",
                                             routed.Type, routed.Destination));
                }
            } else {
                if (_log.isDebugEnabled()) {
                    _log.debug(String.format("Cannot follow routed breadcrumb from [%s] to [%s]", ant, routed.Next));
                }
            }
        }
    }

    private Iterable<Tile> getBreadcrumbNeighborhood(Tile center, TargetingPolicy.Type type) {
        // TODO
        final ArrayList<Tile> empty = new ArrayList<Tile>(0);
        return empty;
    }

    private static class Breadcrumb {
        public Tile Destination;
        public TargetingPolicy.Type Type;
        public int TurnCreated;
    }

    private static class UnroutedBreadcrumb extends Breadcrumb {
        public Tile RouteInfluencer;
    }

    private static class RoutedBreadcrumb extends Breadcrumb {
        public Tile Next;
        public int UsageCount;
    }
}
