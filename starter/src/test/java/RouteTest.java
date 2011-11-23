import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Test
public class RouteTest {

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

    private Ants getAnts(String map) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(map));
        int rows = 0, cols = 0;
        String row;
        Map<Tile, Ilk> layout = new HashMap<Tile, Ilk>();
        while ((row = reader.readLine()) != null) {
            cols = row.length();
            for (int col = 0; col < cols; col++) {
                char c = row.charAt(col);
                Ilk ilk;
                switch (c) {
                    case 'A' :
                        ilk = Ilk.MY_ANT;
                        break;
                    case 'F' :
                        ilk = Ilk.FOOD;
                        break;
                    case 'W' :
                        ilk = Ilk.WATER;
                        break;
                    default :
                        ilk = Ilk.LAND;
                }
                layout.put(new Tile(rows, col), ilk);
            }
            rows++;
        }
        Ants a = new Ants(0, 0, rows, cols, 0, 50, 0, 0);
        for (Map.Entry<Tile, Ilk> e : layout.entrySet()) {
            a.update(e.getValue(), e.getKey());
        }
        return a;
    }

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
        Ants a = getAnts(map);
        long start = System.currentTimeMillis();
        for (Tile ant : a.getMyAnts()) {
            for (Tile food : a.getFoodTiles()) {
                AStarRoute r = new AStarRoute(a, ant, food);
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