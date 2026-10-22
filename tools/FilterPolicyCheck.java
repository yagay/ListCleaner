import com.yagay.ListCleaner.domain.FilterPolicy;

public final class FilterPolicyCheck {
    public static void main(String[] args) {
        int checks = 0;
        for (String kind : new String[]{"SHARE", "SHARE_MULTIPLE", "OPEN", "BROWSER", "PROCESS_TEXT"}) {
            for (int before : new int[]{0, 1, 9}) {
                for (int after : new int[]{0, 1, 9}) {
                    boolean expected = false;
                    if (FilterPolicy.restoreEmpty(kind, before, after) != expected) throw new AssertionError(kind);
                    checks++;
                }
            }
        }
        int[][] pairs = {{-1,-1},{10001,10001},{10001,1010001},{10001,10002},{-1,10001}};
        for (int[] pair : pairs) {
            if (FilterPolicy.sameCaller(pair[0], pair[1]) != (pair[0] >= 0 && pair[0] == pair[1])) {
                throw new AssertionError("caller/profile safety");
            }
            checks++;
        }
        for (int[] pair : pairs) {
            for (boolean exported : new boolean[]{false, true}) {
                boolean expected = !exported && !(pair[0] >= 0 && pair[0] == pair[1]);
                if (FilterPolicy.catalogRestricted(exported, pair[1], pair[0]) != expected) {
                    throw new AssertionError("catalog export/owner policy");
                }
                checks++;
            }
        }
        System.out.println("PASS: " + checks + " filter policy cases");
    }
}
