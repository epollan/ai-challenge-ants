/**
 * Author: evan.pollan
 */
public class X {

    public interface Function<R, T> {
        R eval(T... args);
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

    public static class ReferenceAim {
        public Aim Value;
        public ReferenceAim() {}
        public ReferenceAim(Aim v) {Value = v;}
    }
}
