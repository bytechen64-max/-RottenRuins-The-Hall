import org.bytechen.rottenruins.core.story.StoryRule;
import java.util.List;
import java.util.Set;

public final class StoryRuleTest {
    public static void main(String[] args) {
        var conditions = new StoryRule.Conditions(2, 5, 0, 10, 3600, "fall", "minecraft:overworld", Set.of("test:ready"), Set.of("test:done"));
        check(conditions.matches(new StoryRule.Context(2, 1, 3600, "fall", "minecraft:overworld", Set.of("test:ready"))), "inclusive condition bounds");
        check(!conditions.matches(new StoryRule.Context(1, 1, 20, "fall", "minecraft:overworld", Set.of("test:ready"))), "death minimum");
        check(!conditions.matches(new StoryRule.Context(2, 1, 3601, "fall", "minecraft:overworld", Set.of("test:ready"))), "interval maximum");
        check(!conditions.matches(new StoryRule.Context(2, 1, 20, "drown", "minecraft:overworld", Set.of("test:ready"))), "cause");
        check(!conditions.matches(new StoryRule.Context(2, 1, 20, "fall", "minecraft:the_nether", Set.of("test:ready"))), "dimension");
        check(!conditions.matches(new StoryRule.Context(2, 1, 20, "fall", "minecraft:overworld", Set.of())), "required flag");
        check(!conditions.matches(new StoryRule.Context(2, 1, 20, "fall", "minecraft:overworld", Set.of("test:ready", "test:done"))), "forbidden flag");
        var choices = List.of(new StoryRule.Choice("test:a", 3), new StoryRule.Choice("test:b", 1), new StoryRule.Choice("test:zero", 0));
        var rule = new StoryRule("test:r", "test", 0, 0, 0, "", "test:g", true, conditions, choices);
        for (int roll = 0; roll < 4; roll++) {
            int sample = roll;
            check(rule.choose("", total -> sample).equals(roll < 3 ? "test:a" : "test:b"), "exact weighted intervals");
        }
        check(rule.choose("test:a", total -> { check(total == 1, "exclude prior weight"); return 0; }).equals("test:b"), "avoid repeat");
        var only = new StoryRule("test:r", "test", 0, 0, 0, "", "test:g", true, conditions, List.of(new StoryRule.Choice("test:a", 1)));
        check(only.choose("test:a", total -> 0).equals("test:a"), "single choice remains playable");
        System.out.println("PASS: condition bounds, flags, dimensions, weighted intervals and repeat avoidance");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
