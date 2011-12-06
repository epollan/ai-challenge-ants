import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class RouteTest extends BaseTest {

    private static final String WATER_MAP =
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "..W.................\n" +
            "..W.................\n" +
            "..W.................\n" +
            "..W.......W.........\n" +
            "..W...A...W...F.....\n" +
            "..W.......W.........\n" +
            "..W.......W.........\n" +
            "..W.......W.........\n" +
            "..W.................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................";

    private static final String GAP_MAP =
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "..W.................\n" +
            "..W.................\n" +
            "..W.................\n" +
            "..W.......W.........\n" +
            "..W......AW.........\n" +
            "..W.......W.........\n" +
            "..W.......WF........\n" +
            "..W.................\n" +
            "..W.......W.........\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................";

    private static final String SIMPLE_MAP =
            "....................\n" +
            "....................\n" +
            "............F.......\n" +
            "....................\n" +
            "......A.............\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................\n" +
            "....................";

    public void testSimpleAStarRoute() throws Exception {
        test(SIMPLE_MAP, 8);
    }

    public void testWaterAStarRoute() throws Exception {
        test(WATER_MAP, 12);
    }

    public void testGapRoute() throws Exception {
        test(GAP_MAP, 6);
    }

    private void test(String map, int expectedRouteLength) throws Exception {
        buildState(map);
        long start = System.currentTimeMillis();
        for (Tile ant : Ants.Instance.getMyAnts()) {
            for (Tile food : Ants.Instance.getFoodTiles()) {
                AStarRoute r = new AStarRoute(ant, food);
                System.out.format("Route determined in %d ms\n", (System.currentTimeMillis()-start));
                printRoute(r);
                Assert.assertEquals(r.getDistance(), expectedRouteLength);
                Tile next = r.nextTile();
                Assert.assertNotNull(next);
                Assert.assertTrue((Math.abs(ant.getCol()-next.getCol()) == 0 &&
                                   Math.abs(ant.getRow()-next.getRow()) == 1) ||
                                  (Math.abs(ant.getCol()-next.getCol()) == 1 &&
                                   Math.abs(ant.getRow()-next.getRow()) == 0));
            }
        }
    }

    private void printRoute(AStarRoute r) {
        for (Tile t : r.routeTiles()) {
            System.out.format("-> [%s] ", t);
        }
        System.out.println();
    }
}