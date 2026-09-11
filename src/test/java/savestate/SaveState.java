package savestate;

/** Minimal test double used to exercise the reflection-only SaveState bridge. */
public class SaveState {

    public static int loaded = 0;

    public SaveState() {
    }

    public SaveState(String encoded) {
        if (!"test-checkpoint".equals(encoded)) {
            throw new IllegalArgumentException("unexpected test checkpoint");
        }
    }

    public String encode() {
        return "test-checkpoint";
    }

    public void loadState() {
        loaded++;
    }

    public Object jsonEncode() {
        return new Object() {
            @Override
            public String toString() {
                return "{\"floorNum\":5,\"rngState\":{\"seed\":123,\"shuffleRng\":7}}";
            }
        };
    }
}
