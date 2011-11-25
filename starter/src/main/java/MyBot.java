import com.sun.corba.se.impl.oa.poa.Policies;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.corba.se.spi.activation._LocatorImplBase;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Starter bot implementation.
 */
public class MyBot extends Bot {
    /**
     * Main method executed by the game engine for starting the bot.
     *
     * @param args command line arguments
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args)
            throws IOException {
        _log.info("[[GAME START]]");
        new MyBot().readSystemInput();
    }

    private Set<Tile> _destinations = new HashSet<Tile>();
    private Set<Tile> _toMove = new HashSet<Tile>();
    private Set<Tile> _unseenTiles = null;
    private TargetingPolicy _foodPolicy = new TargetingPolicy("FoodPolicy", 1, 5);
    private TargetingPolicy _hillPolicy = new TargetingPolicy("HillPolicy", 5, 5);
    private TargetingPolicy _unseenPolicy = new TargetingPolicy("UnseenTilePolicy", 1, 5);
    private TreeSet<Tile> _sortedAnts = new TreeSet<Tile>();
    private int _turn = 0;
    private static Logger _log = Logger.getLogger(MyBot.class);

    /**
     * For every ant check every direction in fixed order (N, E, S, W) and move it if the tile is
     * passable.
     */
    @Override
    public void doTurn() {
        _log.info(String.format("[[...turn %d...]]", _turn++));
        try {
            long start = System.currentTimeMillis();
            Ants ants = getAnts();
            _sortedAnts.addAll(ants.getMyAnts());

            if (_unseenTiles == null) {
                _unseenTiles = new HashSet<Tile>();
                for (int row = 0; row < ants.getRows(); row++) {
                    for (int col = 0; col < ants.getCols(); col++) {
                        _unseenTiles.add(new Tile(row, col));
                    }
                }
            }
            // remove any tiles that can be seen, run each turn
            for (Iterator<Tile> locIter = _unseenTiles.iterator(); locIter.hasNext(); ) {
                Tile next = locIter.next();
                if (ants.isVisible(next)) {
                    locIter.remove();
                }
            }

            avoidHills();
            seekFood();
            attackHills();
            exploreUnseenTiles();
            unblockHills();

            _destinations.clear();
            _toMove.clear();
            _sortedAnts.clear();
            TargetingPolicy.clearAssignments();
            long finish = System.currentTimeMillis();
            _log.info(String.format("[[ # turn %d processing took %d ms, allowed %d.  Overall remaining: %d # ]]",
                                    _turn-1, finish-start, ants.getTurnTime(), ants.getTimeRemaining()));
        } catch (Throwable t) {
            _log.error("Unexpected turn processing error", t);
            throw new RuntimeException("Unexpected", t);
        }
    }

    private void avoidHills() {
        for (Tile myHill : getAnts().getMyHills()) {
            _destinations.add(myHill);
        }
    }

    private void seekFood() {
        // find close food
        targetTiles(new TreeSet<Tile>(getAnts().getFoodTiles()), _foodPolicy);
    }

    private void attackHills() {
        // attack hills
        targetTiles(new TreeSet<Tile>(getAnts().getEnemyHills()), _hillPolicy);
    }

    private void exploreUnseenTiles() {
        // TODO: algorithm that seems to make sense -- take any untasked ants and run the
        // radius of "unseen" relative to those ants, using A* routing to figure out the
        // best way forward.  Thought:  can a hill-unblocking policy work here, where the
        // 'center' of repulsion is the relative center of visibility?
        targetTiles(_unseenTiles, _unseenPolicy);
    }

    private void unblockHills() {
        // TODO -- this doesn't work when there's a critical mass of ants around the hills,
        // because the only ants being moved are those directly on the hill, and those
        // on the periphery can block their egress.  What we really want is a 'sphere' of
        // repulsion that triggers route calculations that move away from that sphere.
        Ants ants = getAnts();
        for (Tile myHill : ants.getMyHills()) {
            if (ants.getMyAnts().contains(myHill) && !_toMove.contains(myHill)) {
                for (Aim direction : Aim.values()) {
                    if (moveInDirection(myHill, direction)) {
                        break;
                    }
                }
            }
        }
    }

    private void targetTiles(Collection<Tile> targets, TargetingPolicy policy) {
        List<Route> routes = new LinkedList<Route>();
        int candidateAnts = 0;
        Ants ants = getAnts();
        for (Tile antLoc : _sortedAnts) {
            if (!_toMove.contains(antLoc)) {
                candidateAnts++;
                int routesRemaining = (policy.getPerAntRouteLimit() != null) ?
                                      policy.getPerAntRouteLimit().intValue() : Integer.MAX_VALUE;
                for (Tile target : targets) {
                    try {
                        Route route = new AStarRoute(ants, antLoc, target);
                        routes.add(route);
                        if (--routesRemaining == 0) {
                            if (_log.isDebugEnabled()) {
                                _log.debug(String.format("Capping routes under %s consideration, %d ms remaining...",
                                                         policy, ants.getTimeRemaining()));
                            }
                            break;
                        }
                    } catch (NoRouteException ex) {
                        //_log.error(String.format("Cannot route from [%s] to [%s]", antLoc, target), ex);
                    }
                }
            }
        }
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Weighing %,d routes using %s, given %d candidate ants and %,d targets",
                                     routes.size(), policy, candidateAnts, targets.size()));
        }
        if (routes.isEmpty()) {
            return;
        }
        Collections.sort(routes);
        int allowedAssignments = targets.size() * policy.getAssignmentLimit();
        int assignments = 0;
        for (Route route : routes) {
            if (_toMove.contains(route.getStart())) {
                // More optimial route already assigned movement to this route's ant
                continue;
            }
            if (policy.canAssign(route.getStart(), route.getEnd()) && move(route)) {
                policy.assign(route.getStart(), route.getEnd());
                if (_log.isDebugEnabled()) {
                    _log.debug(String.format("Move successful -- route assigned using %s", policy));
                }
                assignments++;
                if (assignments >= allowedAssignments) {
                    if (_log.isDebugEnabled()) {
                        _log.debug(String.format("Assigned the maximum number of %s routes (%s)",
                                                 policy, assignments));
                    }
                    break;
                }
            }
        }
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("%s targeting complete, %d ms remaining in turn...",
                                     policy, getAnts().getTimeRemaining()));
        }
    }

    private boolean moveInDirection(Tile antLoc, Aim direction) {
        Ants ants = getAnts();
        // Track all moves, prevent collisions
        Tile newLoc = ants.getTile(antLoc, direction);
        if (ants.getIlk(newLoc).isUnoccupied() && !_destinations.contains(newLoc)) {
            ants.issueOrder(antLoc, direction);
            _destinations.add(newLoc);
            _toMove.add(antLoc);
            if (_log.isDebugEnabled()) {
                _log.debug(String.format("Moving ant at [%s] %s to [%s]", antLoc, direction, newLoc));
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean moveToLocation(Tile antLoc, Tile destLoc) {
        Ants ants = getAnts();
        List<Aim> directions = ants.getDirections(antLoc, destLoc);
        for (Aim direction : directions) {
            if (moveInDirection(antLoc, direction)) {
                return true;
            }
        }
        return false;
    }

    private boolean move(Route r) {
        if (r instanceof AStarRoute) {
            return moveToLocation(r.getStart(), ((AStarRoute) r).nextTile());
        } else {
            return moveToLocation(r.getStart(), r.getEnd());
        }
    }
}
