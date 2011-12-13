/**
 * Author: evan.pollan
 */
public class Comparisons {

    private Comparisons() {}

    public static int compare(int first, int second) {
        return (first < second) ? -1 : (first > second) ? 1 : 0;
    }

    public static int compare(float first, float second) {
        return (first < second) ? -1 : (first > second) ? 1 : 0;
    }
}
