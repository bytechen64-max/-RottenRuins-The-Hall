package org.bytechen.rottenruins.api.infection.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.Event;

/**
 * 方块扩散事件。
 * <p>
 * 在方块被扩散系统转换前后触发，允许其他模组监听并响应。
 *
 * <h3>Pre（可取消）</h3>
 * 在扩散执行前触发。取消事件可阻止本次扩散。
 *
 * <h3>Post（不可取消）</h3>
 * 在扩散成功执行后触发。用于添加自定义粒子、音效等后续处理。
 *
 * <p>
 * 所有子事件共享相同的事件总线，使用 {@link #isPre()} / {@link #isPost()} 区分。
 */
public abstract class BlockSpreadEvent extends Event {

    private final Level level;
    private final BlockPos pos;
    private final BlockState oldState;
    private final BlockState newState;
    private final ResourceLocation spreadType;

    protected BlockSpreadEvent(Level level, BlockPos pos, BlockState oldState,
                                BlockState newState, ResourceLocation spreadType) {
        this.level = level;
        this.pos = pos;
        this.oldState = oldState;
        this.newState = newState;
        this.spreadType = spreadType;
    }

    public Level getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getOldState() { return oldState; }
    public BlockState getNewState() { return newState; }
    public ResourceLocation getSpreadType() { return spreadType; }

    public boolean isPre() { return this instanceof Pre; }
    public boolean isPost() { return this instanceof Post; }

    // ==================== Pre ====================

    /**
     * 方块扩散前触发。取消此事件可阻止扩散。
     * <p>
     * 此事件在 {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} 上触发。
     */
    public static class Pre extends BlockSpreadEvent {
        public Pre(Level level, BlockPos pos, BlockState oldState,
                    BlockState newState, ResourceLocation spreadType) {
            super(level, pos, oldState, newState, spreadType);
        }

        @Override
        public boolean isCancelable() { return true; }
    }

    // ==================== Post ====================

    /**
     * 方块扩散成功后触发。不可取消。
     * <p>
     * 此事件在 {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} 上触发。
     */
    public static class Post extends BlockSpreadEvent {
        public Post(Level level, BlockPos pos, BlockState oldState,
                     BlockState newState, ResourceLocation spreadType) {
            super(level, pos, oldState, newState, spreadType);
        }

        @Override
        public boolean isCancelable() { return false; }
    }
}
