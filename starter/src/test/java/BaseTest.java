import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;



/**
 * Author: evan.pollan
 * Date: 11/25/11
 * Time: 12:10 AM
 */
public class BaseTest {

    protected TimeManager _dummyManager;

    @BeforeClass
    public void setup() {
        LogFacade.setTestConfig();
        _dummyManager = new TimeManager(10000);
        _dummyManager.nextStep();
        _dummyManager.nextStep();
        _dummyManager.nextStep();
        _dummyManager.nextStep();
        _dummyManager.turnDone();
        _dummyManager.nextStep();
    }

    protected void buildState(String map)
            throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(map));
        int rows = 0, cols = -1;
        String row;
        Map<Tile, Ilk> layout = new HashMap<Tile, Ilk>();
        while ((row = reader.readLine()) != null) {
            if (cols == -1) {
                cols = row.length();
            } else if (row.length() != cols) {
                throw new RuntimeException("Uneven map row length");
            }
            for (int col = 0; col < cols; col++) {
                char c = row.charAt(col);
                Ilk ilk;
                switch (c) {
                    case 'A':
                        ilk = Ilk.MY_ANT;
                        break;
                    case 'F':
                        ilk = Ilk.FOOD;
                        break;
                    case 'W':
                        ilk = Ilk.WATER;
                        break;
                    default:
                        ilk = Ilk.LAND;
                }
                layout.put(new Tile(rows, col), ilk);
            }
            rows++;
        }
        Ants.initialize(0, 0, rows, cols, 0, 50, 0, 0);
        for (Map.Entry<Tile, Ilk> e : layout.entrySet()) {
            Ants.Instance.update(e.getValue(), e.getKey());
        }
    }
}
