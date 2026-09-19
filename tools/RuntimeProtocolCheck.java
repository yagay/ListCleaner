import com.yagay.ListCleaner.domain.RuntimeProtocol;

public final class RuntimeProtocolCheck {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) {
        check(RuntimeProtocol.hookCompatible("UP_TO_DATE", 19, 19, 19));
        check(RuntimeProtocol.hookCompatible("STALE", 19, 19, 20));
        check(RuntimeProtocol.hookCompatible("STALE", 20, 19, 20));
        check(!RuntimeProtocol.hookCompatible("STALE", 18, 19, 20));
        check(!RuntimeProtocol.hookCompatible("STALE", 21, 19, 20));
        check(!RuntimeProtocol.hookCompatible("RELOADING", 19, 19, 20));
        check(!RuntimeProtocol.hookCompatible("FAILED", 19, 19, 20));
        check(!RuntimeProtocol.hookCompatible(null, 19, 19, 20));
        check(RuntimeProtocol.supportsSafetyPause("STALE", 19, 19, 20));
        check(!RuntimeProtocol.supportsSafetyPause("STALE", 18, 19, 20));
        check(RuntimeProtocol.digest("").equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        check(RuntimeProtocol.digest("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        check(RuntimeProtocol.digest("规则").length() == 64);
        check(!RuntimeProtocol.digest("{}").equals(RuntimeProtocol.digest("{ }")));
        check(RuntimeProtocol.digest("{\"rules\":[]}").equals(RuntimeProtocol.digest("{\"rules\":[]}")));
        System.out.println("RuntimeProtocolCheck: " + checks + " passed");
    }
}
