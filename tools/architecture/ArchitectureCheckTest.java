import java.nio.file.*;
import java.util.*;

/** JDK-only regression fixtures; does not launch Minecraft. */
public final class ArchitectureCheckTest {
    static int count;
    public static void main(String[] args) throws Exception {
        check(Map.of("api/A.java", "package org.bytechen.rottenruins.api; import org.bytechen.rottenruins.content.Bad; interface A {}"), "API implementation dependency");
        check(Map.of("api/A.java", "package org.bytechen.rottenruins.api; interface A { net.minecraft.client.Minecraft value(); }"), "API implementation dependency");
        check(Map.of("gameplay/A.java", "package org.bytechen.rottenruins.gameplay; import org.bytechen.rottenruins.utils.*; class A { Helper h; }",
                "utils/Helper.java", "package org.bytechen.rottenruins.utils; class Helper { Other h; }",
                "utils/Other.java", "package org.bytechen.rottenruins.utils; class Other { void run() { net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(null); } }"), "utils/Other.java -> net.minecraftforge.common.MinecraftForge");
        check(Map.of("gameplay/A.java", "package org.bytechen.rottenruins.gameplay; import static org.bytechen.rottenruins.utils.Helper.run; class A { void go() { run(); } }",
                "utils/Helper.java", "package org.bytechen.rottenruins.utils; class Helper { static void run() { entity.getPersistentData(); } }"), "Gameplay persistence extension");
        check(Map.of("network/A.java", "package org.bytechen.rottenruins.network; class A { void go() { org.bytechen.rottenruins.client.Handler.run(); } }"), "Unguarded common network");
        check(Map.of("network/A.java", "package org.bytechen.rottenruins.network; import net.minecraft.client.Minecraft; class A {}"), "must not import clients");
        check(Map.of("network/A.java", "package org.bytechen.rottenruins.network; class A { void go() { net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> org.bytechen.rottenruins.client.Handler.run()); } }"), null);
        check(Map.of("api/A.java", "package org.bytechen.rottenruins.api; /* net.minecraft.client.Minecraft */ interface A { String S = \"org.bytechen.rottenruins.content.Bad\"; }"), null);
        check(Map.of("mystery/A.java", "package org.bytechen.rottenruins.mystery; class A {}"), "Unclassified source");
        // A new file using an existing client Forge type still produces a distinct debt edge.
        var c = check(Map.of("client/New.java", "package org.bytechen.rottenruins.client; import net.minecraftforge.common.MinecraftForge; class New {}"), null);
        if (!c.debt.contains("client-forge | client/New.java -> net.minecraftforge.common.MinecraftForge")) throw new AssertionError(c.debt);
        c.validateLedger(Set.of());
        if (c.errors.stream().noneMatch(s -> s.contains("Unreviewed"))) throw new AssertionError("New coupling accepted");
        c.errors.clear();
        var baseline = new HashSet<>(c.debt);
        baseline.add("client-forge | client/Deleted.java -> net.minecraftforge.common.MinecraftForge");
        c.validateLedger(baseline);
        if (c.errors.stream().noneMatch(s -> s.contains("stale"))) throw new AssertionError("Stale entry accepted");
        System.out.println("Architecture regression fixtures passed: " + count);
    }
    static ArchitectureCheck check(Map<String, String> files, String expected) throws Exception {
        Path root = Files.createTempDirectory("architecture-fixture-");
        try {
            for (var entry : files.entrySet()) {
                Path target = root.resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue());
            }
            var checker = new ArchitectureCheck();
            checker.read(root);
            checker.inspect();
            if (expected == null ? !checker.errors.isEmpty() : checker.errors.stream().noneMatch(s -> s.contains(expected)))
                throw new AssertionError("Expected " + expected + ": " + checker.errors);
            count++;
            return checker;
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
