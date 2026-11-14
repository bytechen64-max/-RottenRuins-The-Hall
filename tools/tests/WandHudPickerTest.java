import org.bytechen.rottenruins.client.gui.WandHudPicker;

public final class WandHudPickerTest {
    public static void main(String[] args) {
        int[] loaded = {0, 2, 3, 5, 7};
        for (float open : new float[]{0.6F, 0.8F, 1, 1.04F}) {
            for (int index : loaded) {
                var p = WandHudPicker.position(index, open);
                check(WandHudPicker.hit(p.x(), p.y(), loaded, open) == index,
                        "animated icons retain their original wheel direction");
            }
        }
        var missing = WandHudPicker.position(1, 1);
        check(WandHudPicker.hit(missing.x(), missing.y(), loaded, 1) == -1, "empty slots are not clickable");
        check(WandHudPicker.hit(0, 0, loaded, 1) == -1, "center does not select an outer spell");
        var top = WandHudPicker.position(0, 0.4F);
        check(WandHudPicker.hit(top.x(), top.y(), loaded, 0.4F) == -1, "collapsed icons cannot be selected");
        check(WandHudPicker.hit(0, -58, new int[0], 1) == -1, "empty pages have no selection");
        for (float scale : new float[]{0.7F, 0.85F, 1, 2}) {
            for (float edge : new float[]{0, 640}) {
                float cx = WandHudPicker.safeCenter(edge, 640, scale, 1);
                float cy = WandHudPicker.safeCenter(edge, 360, scale, 1);
                for (int i = 0; i < 8; i++) {
                    var p = WandHudPicker.position(i, 1.04F);
                    float x = cx + p.x() * scale, y = cy + p.y() * scale;
                    check(x >= 13 * scale && x <= 640 - 13 * scale, "horizontal edge clearance");
                    check(y >= 13 * scale && y <= 360 - 13 * scale, "vertical edge clearance");
                }
            }
        }
        check(WandHudPicker.safeCenter(20, 640, 1, 0) == 20, "closed HUD retains its saved position");
        check(Float.isFinite(WandHudPicker.safeCenter(1, 30, 2, 1)), "tiny windows remain finite");
        System.out.println("PASS: sparse loadouts, animated hit regions, closed/empty menus and all screen edges");
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
