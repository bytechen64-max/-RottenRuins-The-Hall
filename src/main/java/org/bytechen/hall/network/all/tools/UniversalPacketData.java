package org.bytechen.hall.network.all.tools;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class UniversalPacketData<T> {
    private final DataType type;
    private final T value;

    private UniversalPacketData(DataType type, T value) { this.type = type; this.value = value; }

    public DataType getType() { return type; }
    public T getValue() { return value; }

    public enum DataType { INT, LONG, FLOAT, DOUBLE, BOOLEAN, STRING, BLOCK_POS, VEC3, RESOURCE_LOCATION, NBT, EMPTY }

    public static UniversalPacketData<Integer> ofInt(int v) { return new UniversalPacketData<>(DataType.INT, v); }
    public static UniversalPacketData<Long> ofLong(long v) { return new UniversalPacketData<>(DataType.LONG, v); }
    public static UniversalPacketData<Float> ofFloat(float v) { return new UniversalPacketData<>(DataType.FLOAT, v); }
    public static UniversalPacketData<Double> ofDouble(double v) { return new UniversalPacketData<>(DataType.DOUBLE, v); }
    public static UniversalPacketData<Boolean> ofBoolean(boolean v) { return new UniversalPacketData<>(DataType.BOOLEAN, v); }
    public static UniversalPacketData<String> ofString(String v) { return new UniversalPacketData<>(DataType.STRING, v); }
    public static UniversalPacketData<BlockPos> ofBlockPos(BlockPos v) { return new UniversalPacketData<>(DataType.BLOCK_POS, v); }
    public static UniversalPacketData<Vec3> ofVec3(Vec3 v) { return new UniversalPacketData<>(DataType.VEC3, v); }
    public static UniversalPacketData<ResourceLocation> ofResourceLocation(ResourceLocation v) { return new UniversalPacketData<>(DataType.RESOURCE_LOCATION, v); }
    public static UniversalPacketData<CompoundTag> ofNbt(CompoundTag v) { return new UniversalPacketData<>(DataType.NBT, v); }
    public static UniversalPacketData<Void> empty() { return new UniversalPacketData<>(DataType.EMPTY, null); }

    @SuppressWarnings("unchecked")
    public static UniversalPacketData<?> read(FriendlyByteBuf buf) {
        return switch (buf.readEnum(DataType.class)) {
            case INT -> ofInt(buf.readInt());
            case LONG -> ofLong(buf.readLong());
            case FLOAT -> ofFloat(buf.readFloat());
            case DOUBLE -> ofDouble(buf.readDouble());
            case BOOLEAN -> ofBoolean(buf.readBoolean());
            case STRING -> ofString(buf.readUtf());
            case BLOCK_POS -> ofBlockPos(buf.readBlockPos());
            case VEC3 -> ofVec3(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
            case RESOURCE_LOCATION -> ofResourceLocation(buf.readResourceLocation());
            case NBT -> ofNbt(buf.readNbt());
            case EMPTY -> empty();
        };
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        switch (type) {
            case INT -> buf.writeInt((Integer) value);
            case LONG -> buf.writeLong((Long) value);
            case FLOAT -> buf.writeFloat((Float) value);
            case DOUBLE -> buf.writeDouble((Double) value);
            case BOOLEAN -> buf.writeBoolean((Boolean) value);
            case STRING -> buf.writeUtf((String) value);
            case BLOCK_POS -> buf.writeBlockPos((BlockPos) value);
            case VEC3 -> { buf.writeDouble(((Vec3) value).x); buf.writeDouble(((Vec3) value).y); buf.writeDouble(((Vec3) value).z); }
            case RESOURCE_LOCATION -> buf.writeResourceLocation((ResourceLocation) value);
            case NBT -> buf.writeNbt((CompoundTag) value);
            case EMPTY -> {}
        }
    }
}
