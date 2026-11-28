package org.bytechen.rottenruins.gameplay.combat.slash;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.combat.slash.SlashContext;
import org.bytechen.rottenruins.core.combat.slash.SweptSlashVolume;
import java.util.Comparator;
import java.util.List;

public final class SlashHitResolver {
    private SlashHitResolver() {}
    public static List<LivingEntity> collect(SlashContext context) {
        var p = context.player(); var spec = context.spec();
        Vec3 origin = p.getEyePosition();
        var volume = new SweptSlashVolume(vector(context.forward()), vector(context.cutDirection()),
                spec.reach(), spec.innerReach(), spec.arcDegrees(), spec.halfThickness());
        AABB broad = new AABB(origin, origin).inflate(spec.reach() + spec.halfThickness());
        return p.level().getEntitiesOfClass(LivingEntity.class, broad, target -> {
            if (target == p || !target.isAlive() || target.isSpectator() || !target.isPickable()
                    || target.isPassengerOfSameVehicle(p)) return false;
            if (!spec.hitAllies() && (p.isAlliedTo(target) || target.isAlliedTo(p)
                    || target instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(p))) return false;
            if (target instanceof Player other && (!p.canHarmPlayer(other) || !p.server.isPvpAllowed())) return false;
            if (spec.requireLineOfSight() && !p.hasLineOfSight(target)) return false;
            AABB box = target.getBoundingBox().move(origin.scale(-1));
            return volume.intersects(new SweptSlashVolume.Box(vector(box.getCenter()),
                    new SweptSlashVolume.Vec(box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2)));
        }).stream().sorted(Comparator.comparingDouble(p::distanceToSqr)).toList();
    }
    private static SweptSlashVolume.Vec vector(Vec3 v) { return new SweptSlashVolume.Vec(v.x, v.y, v.z); }
}
