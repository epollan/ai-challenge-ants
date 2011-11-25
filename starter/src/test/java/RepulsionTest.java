import org.apache.log4j.Logger;
import org.ietf.jgss.Oid;
import org.testng.annotations.Test;

import javax.sound.midi.VoiceStatus;

/**
 * TODO
 * Author: evan.pollan
 * Date: 11/25/11
 * Time: 12:10 AM
 */
@Test
public class RepulsionTest extends BaseTest {

    public void testEgressRouteCount() throws Exception {
        final String map =
                "....................\n" +
                "....................\n" +
                "........WWW.........\n" +
                "....................\n" +
                "......A...A.WWW.....\n" +
                "............WW......\n" +
                ".........A..WW......\n" +
                "............W.......\n" +
                "....................\n" +
                "....................";
        // 4,8
        Ants a = getAnts(map);
        RepulsionPolicy policy = new RepulsionPolicy(a, new Tile(4, 8), 4);
        policy.evacuate(a.getMyAnts(), new RepulsionPolicy.HandleRepulsion() {
            @Override
            public void repulse(Tile ant, Tile destination) {
                System.out.format("Evacuating [%s] by way of [%s]\n", ant, destination);
            }
        });
    }

}
