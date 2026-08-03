package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class RegisterParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HallMod.MODID);

    // Your particles here
    // public static final RegistryObject<SimpleParticleType> EXAMPLE_PARTICLE =
    //         PARTICLE_TYPES.register("example_particle", () -> new SimpleParticleType(false));
}
