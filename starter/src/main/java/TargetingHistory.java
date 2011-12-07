import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Author: evan.pollan
 * Date: 11/28/11
 * Time: 8:04 AM
 */
public class TargetingHistory {

    private final Map<Tile, Breadcrumb> _map = new HashMap<Tile, Breadcrumb>();
    private int _turn;
    private final ArrayList<Tile> _neighborsBuffer = new ArrayList<Tile>(8);
    private static final LogFacade _log = LogFacade.get(TargetingHistory.class);

    public static final TargetingHistory Instance = new TargetingHistory();

    private TargetingHistory() {
    }

    public void syncState(int turn) {
        _turn = turn;
        for (Map.Entry<Tile, Breadcrumb> entry : new ArrayList<Map.Entry<Tile, Breadcrumb>>(_map.entrySet())) {
            if (entry.getValue().isExpired(_turn)) {
                _map.remove(entry.getKey());
                continue;
            }
            boolean present = true;
            switch (entry.getValue().Type) {
                case EnemyAnt:
                    // Let the expiration of the breadcrumb established when creating the
                    // route persist -- enemy ants move around a bunch and end up
                    // clobbering targeting histories unnecessarily
//                    present = Ants.Instance.getEnemyAnts().contains(entry.getValue().Destination);
                    break;
                case EnemyHill:
                    present = Ants.Instance.getEnemyHills().contains(entry.getValue().Destination);
                    break;
                case Food:
                    present = Ants.Instance.getFoodTiles().contains(entry.getValue().Destination);
                    break;
                case UnseenTile:
                    present = Ants.Instance.isVisible(entry.getValue().Destination);
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
                       AStarRoute route,
                       Integer expiresAfter,
                       boolean skipStart) {
        final Tile influencer = current;
        for (Tile nextHop : route.routeTiles()) {
            if (skipStart) {
                skipStart = false;
                continue;
            }
            if (nextHop.equals(destination)) {
                break;
            }
            RoutedBreadcrumb breadcrumb = new RoutedBreadcrumb();
            breadcrumb.Destination = destination;
            breadcrumb.Type = type;
            breadcrumb.TurnCreated = _turn;
            breadcrumb.Next = nextHop;
            breadcrumb.UsageCount = 0;
            breadcrumb.ExpiresAfter = expiresAfter;
            _map.put(current, breadcrumb);
            for (final Tile neighbor : getBreadcrumbNeighborhood(current, nextHop, type)) {
                Breadcrumb preexisting = _map.get(neighbor);
                if (preexisting == null && !neighbor.equals(destination)) {
                    UnroutedBreadcrumb hint = new UnroutedBreadcrumb();
                    hint.Destination = destination;
                    hint.Type = type;
                    hint.TurnCreated = _turn;
                    hint.RouteInfluencer = influencer;
                    hint.ExpiresAfter = expiresAfter;
                    _map.put(neighbor, hint);
                }
            }
            current = nextHop;
        }
    }

    public void followBreadcrumb(Tile ant, TargetingPolicy.TargetingHandler handler) {
        Breadcrumb crumb = _map.get(ant);
        if (crumb instanceof UnroutedBreadcrumb) {
            Tile influencer = ((UnroutedBreadcrumb) crumb).RouteInfluencer;
            Breadcrumb influence = _map.get(influencer);
            if (influence instanceof UnroutedBreadcrumb) {
                _log.info(String.format(
                        "Unexpected unrouted %s influencer created for destination=[%s] on turn %d.  Our destination=[%s]",
                        influence.Type, influence.Destination, influence.TurnCreated, crumb.Destination));
            } else if (influence == null) {
                _log.info(String.format(
                        "Removing unrouted %s breadcrumb at [%s] because its influencer at [%s] is missing",
                        crumb.Type, ant, influencer));
                _map.remove(ant);
            } else if (!ant.equals(influence.Destination)) {
                // We need to calculate our own route from this tile
                try {
                    AStarRoute route = new AStarRoute(ant, influence.Destination);
                    if (handler.move(ant, route.nextTile(), route.getEnd(), influence.Type)) {
                        _log.debug("Picked up %s targeting for [%s] based on route influence",
                                   influence.Type, influence.Destination);
                        create(ant, influence.Destination, influence.Type, route, influence.ExpiresAfter, false);
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
                _log.debug("Leveraging previously computed route for %s at [%s]",
                           routed.Type, routed.Destination);
            } else {
                _log.debug("Cannot follow routed breadcrumb from [%s] to [%s], removing...", ant, routed.Next);
                _map.remove(ant);
            }
        }
    }

    private Iterable<Tile> getBreadcrumbNeighborhood(final Tile center, final Tile next, TargetingPolicy.Type type) {
        final Aim[] traversal = new Aim[] {
                Aim.NORTH,
                Aim.EAST,
                Aim.SOUTH,
                Aim.SOUTH,
                Aim.WEST,
                Aim.WEST,
                Aim.NORTH,
                Aim.NORTH
        };
        _neighborsBuffer.clear();
        Tile lastNeighbor = center;
        for (Aim aim : traversal) {
            Tile neighbor = Ants.Instance.getTile(lastNeighbor, aim);
            if (!neighbor.equals(next)) {
                _neighborsBuffer.add(neighbor);
            }
            lastNeighbor = neighbor;
        }
        return _neighborsBuffer;
    }

    private static class Breadcrumb {
        public Tile Destination;
        public TargetingPolicy.Type Type;
        public int TurnCreated;
        public Integer ExpiresAfter = null;
        public boolean isExpired(int turn) {
            return (ExpiresAfter != null) ? (TurnCreated + ExpiresAfter.intValue()) < turn : false;
        }
    }

    private static class UnroutedBreadcrumb extends Breadcrumb {
        public Tile RouteInfluencer;
    }

    private static class RoutedBreadcrumb extends Breadcrumb {
        public Tile Next;
        public int UsageCount;
    }
}
