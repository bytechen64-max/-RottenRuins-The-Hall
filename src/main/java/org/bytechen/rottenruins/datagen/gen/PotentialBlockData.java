package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.GroundingStakeBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceWireBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceMachineBlock;

/** 引用手动维护的接地桩、设备模型，生成朝向、物品模型和源导线表现。 */
public final class PotentialBlockData extends BlockStateProvider {
    public PotentialBlockData(PackOutput output, ExistingFileHelper helper) {
        super(output, HallMod.MODID, helper);
    }

    @Override
    public String getName() {
        return "Potential Blocks: " + HallMod.MODID;
    }

    @Override
    protected void registerStatesAndModels() {
        machine(RegisterBlock.SOURCE_FILTER.get(),"source_filter");
        machine(RegisterBlock.SOURCE_COLLECTOR.get(),"source_collector");
        var stakeModel = models().getExistingFile(modLoc("block/grounding_stake"));
        getVariantBuilder(RegisterBlock.GROUNDING_STAKE.get()).forAllStates(state -> {
            Direction facing = state.getValue(GroundingStakeBlock.FACING);
            return ConfiguredModel.builder().modelFile(stakeModel)
                    .rotationX(facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90)
                    .rotationY(facing.getAxis().isHorizontal() ? ((int) facing.toYRot() + 180) % 360 : 0)
                    .build();
        });
        simpleBlockItem(RegisterBlock.GROUNDING_STAKE.get(), stakeModel);

        var wire = RegisterBlock.SOURCE_WIRE.get();
        var multipart = getMultipartBuilder(wire);
        for (boolean active : new boolean[]{false, true}) {
            Integer[] powers = active ? new Integer[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15} : new Integer[]{0};
            var dot = wireModel("dot", active);
            var side = wireModel("side", active);
            var up = wireModel("up", active);
            multipart.part().modelFile(dot).addModel().condition(SourceWireBlock.POWER, powers).end();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int rotation = ((int) direction.toYRot() + 180) % 360;
                var property = SourceWireBlock.SIDES.get(direction);
                multipart.part().modelFile(side).rotationY(rotation).addModel()
                        .condition(SourceWireBlock.POWER, powers)
                        .condition(property, RedstoneSide.SIDE, RedstoneSide.UP).end();
                multipart.part().modelFile(up).rotationY(rotation).addModel()
                        .condition(SourceWireBlock.POWER, powers).condition(property, RedstoneSide.UP).end();
            }
        }
        itemModels().withExistingParent("source_wire", mcLoc("item/generated"))
                .texture("layer0", mcLoc("block/redstone_dust_dot"));
    }

    private void machine(SourceMachineBlock block,String name) {
        // 模型位于 src/main/resources，供 Blockbench / JSON 手动修改，datagen 不写入模型本体。
        var model=models().getExistingFile(modLoc("block/"+name));
        getVariantBuilder(block).forAllStatesExcept(state->ConfiguredModel.builder().modelFile(model)
                .rotationY(((int)state.getValue(SourceMachineBlock.FACING).toYRot()+180)%360).build(),SourceMachineBlock.POWER);
        simpleBlockItem(block,model);
    }

    private BlockModelBuilder wireModel(String part, boolean active) {
        var model = models().getBuilder("source_wire_" + part + (active ? "_on" : "_off"))
                .ao(false).renderType("minecraft:cutout")
                .texture("particle", mcLoc("block/redstone_dust_dot"))
                .texture("line", mcLoc("block/redstone_dust_" + (part.equals("dot") ? "dot" : "line0")));
        int light = active ? 15 : 0;
        var element = model.element().shade(false);
        if (part.equals("up")) {
            element.from(0, 0, 0.25F).to(16, 16, 0.25F)
                    .face(Direction.SOUTH).texture("#line").tintindex(0).uvs(0, 0, 16, 16).emissivity(light, light).end()
                    .face(Direction.NORTH).texture("#line").tintindex(0).uvs(16, 0, 0, 16).emissivity(light, light).end();
        } else {
            int length = part.equals("dot") ? 16 : 8;
            element.from(0, 0.25F, 0).to(16, 0.25F, length)
                    .face(Direction.UP).texture("#line").tintindex(0).uvs(0, 0, 16, length).emissivity(light, light).end()
                    .face(Direction.DOWN).texture("#line").tintindex(0).uvs(0, length, 16, 0).emissivity(light, light).end();
        }
        element.end();
        return model;
    }
}
