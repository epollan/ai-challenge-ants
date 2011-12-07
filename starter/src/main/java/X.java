/**
 * Author: evan.pollan
 */
public class X {

    public interface Function<T> {
        boolean eval(T... args);
    }

    public interface Action<T> {
        void go(T... args);
    }

    // Map-storable reference to a mutable int.  Integer wraps an immutable value (relative
    // to what's stored in the Integer instance itself)
    public static class ReferenceInt {
        public ReferenceInt() {}
        public ReferenceInt(int val) {Value = val;}
        public int Value = 0;
    }
}
