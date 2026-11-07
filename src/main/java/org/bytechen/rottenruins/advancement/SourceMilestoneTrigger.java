package org.bytechen.rottenruins.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import org.bytechen.rottenruins.HallMod;

import java.util.Locale;

/** 源荷生产线中需要实际勘测或操作、无法单凭持有物品判定的里程碑。 */
public final class SourceMilestoneTrigger extends SimpleCriterionTrigger<SourceMilestoneTrigger.Instance> {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath(HallMod.MODID,"source_milestone");
    public static final SourceMilestoneTrigger INSTANCE=new SourceMilestoneTrigger();

    public enum Milestone { HIGH_POTENTIAL,TUNED_FILTER,VOID_TUNING,POWERED_CIRCUIT }

    private SourceMilestoneTrigger() {}
    @Override public ResourceLocation getId() { return ID; }

    @Override
    protected Instance createInstance(JsonObject json,ContextAwarePredicate player,DeserializationContext context) {
        return new Instance(player,Milestone.valueOf(GsonHelper.getAsString(json,"milestone").toUpperCase(Locale.ROOT)));
    }

    public static Instance criterion(Milestone milestone) { return new Instance(ContextAwarePredicate.ANY,milestone); }
    public void trigger(ServerPlayer player,Milestone milestone) { trigger(player,instance->instance.milestone==milestone); }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final Milestone milestone;
        private Instance(ContextAwarePredicate player,Milestone milestone) { super(ID,player);this.milestone=milestone; }
        @Override public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json=super.serializeToJson(context);
            json.addProperty("milestone",milestone.name().toLowerCase(Locale.ROOT));
            return json;
        }
    }
}
