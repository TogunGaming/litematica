package fi.dy.masa.litematica.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;
import fi.dy.masa.malilib.util.EntityUtils;
import fi.dy.masa.malilib.util.position.Coordinate;
import fi.dy.masa.malilib.util.position.IntBoundingBox;
import fi.dy.masa.malilib.util.position.LayerRange;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.ISchematicRegion;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.selection.SelectionBox;
import fi.dy.masa.litematica.selection.SelectionManager;

public class PositionUtils
{
    public static final BlockPosComparator BLOCK_POS_COMPARATOR = new BlockPosComparator();
    public static final ChunkPosComparator CHUNK_POS_COMPARATOR = new ChunkPosComparator();

    public static final EnumFacing.Axis[] AXES_ALL = new EnumFacing.Axis[] { EnumFacing.Axis.X, EnumFacing.Axis.Y, EnumFacing.Axis.Z };
    public static final EnumFacing[] FACING_ALL = new EnumFacing[] { EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST };
    public static final EnumFacing[] ADJACENT_SIDES_ZY = new EnumFacing[] { EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH };
    public static final EnumFacing[] ADJACENT_SIDES_XY = new EnumFacing[] { EnumFacing.DOWN, EnumFacing.UP, EnumFacing.EAST, EnumFacing.WEST };
    public static final EnumFacing[] ADJACENT_SIDES_XZ = new EnumFacing[] { EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST };

    public static final ICoordinateAccessor BLOCKPOS_X_ACCESSOR = new ICoordinateAccessor()
    {
        @Override public BlockPos setValue(BlockPos vec, int newValue) { return new BlockPos(newValue, vec.getY(), vec.getZ()); }
        @Override public int getValue(Vec3i vec) { return vec.getX(); }
    };

    public static final ICoordinateAccessor BLOCKPOS_Y_ACCESSOR = new ICoordinateAccessor()
    {
        @Override public BlockPos setValue(BlockPos vec, int newValue) { return new BlockPos(vec.getX(), newValue, vec.getZ()); }
        @Override public int getValue(Vec3i vec) { return vec.getY(); }
    };

    public static final ICoordinateAccessor BLOCKPOS_Z_ACCESSOR = new ICoordinateAccessor()
    {
        @Override public BlockPos setValue(BlockPos vec, int newValue) { return new BlockPos(vec.getX(), vec.getY(), newValue); }
        @Override public int getValue(Vec3i vec) { return vec.getZ(); }
    };

    public static final IIntBoundingBoxAccessor INT_BOUNDING_BOX_MIN_X_ACCESSOR = new IIntBoundingBoxAccessor()
    {
        @Override public IntBoundingBox setValue(IntBoundingBox box, int newValue) { return new IntBoundingBox(newValue, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ); }
        @Override public int getValue(IntBoundingBox box) { return box.minX; }
    };

    public static final IIntBoundingBoxAccessor INT_BOUNDING_BOX_MIN_Y_ACCESSOR = new IIntBoundingBoxAccessor()
    {
        @Override public IntBoundingBox setValue(IntBoundingBox box, int newValue) { return new IntBoundingBox(box.minX, newValue, box.minZ, box.maxX, box.maxY, box.maxZ); }
        @Override public int getValue(IntBoundingBox box) { return box.minY; }
    };

    public static final IIntBoundingBoxAccessor INT_BOUNDING_BOX_MIN_Z_ACCESSOR = new IIntBoundingBoxAccessor()
    {
        @Override public IntBoundingBox setValue(IntBoundingBox box, int newValue) { return new IntBoundingBox(box.minX, box.minY, newValue, box.maxX, box.maxY, box.maxZ); }
        @Override public int getValue(IntBoundingBox box) { return box.minZ; }
    };

    public static final IIntBoundingBoxAccessor INT_BOUNDING_BOX_MAX_X_ACCESSOR = new IIntBoundingBoxAccessor()
    {
        @Override public IntBoundingBox setValue(IntBoundingBox box, int newValue) { return new IntBoundingBox(box.minX, box.minY, box.minZ, newValue, box.maxY, box.maxZ); }
        @Override public int getValue(IntBoundingBox box) { return box.maxX; }
    };

    public static final IIntBoundingBoxAccessor INT_BOUNDING_BOX_MAX_Y_ACCESSOR = new IIntBoundingBoxAccessor()
    {
        @Override public IntBoundingBox setValue(IntBoundingBox box, int newValue) { return new IntBoundingBox(box.minX, box.minY, box.minZ, box.maxX, newValue, box.maxZ); }
        @Override public int getValue(IntBoundingBox box) { return box.maxY; }
    };

    public static final IIntBoundingBoxAccessor INT_BOUNDING_BOX_MAX_Z_ACCESSOR = new IIntBoundingBoxAccessor()
    {
        @Override public IntBoundingBox setValue(IntBoundingBox box, int newValue) { return new IntBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, newValue); }
        @Override public int getValue(IntBoundingBox box) { return box.maxZ; }
    };

    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XN_ZN = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i(-1,  0,  0), new Vec3i( 0,  0, -1), new Vec3i(-1,  0, -1) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XP_ZN = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 1,  0,  0), new Vec3i( 0,  0, -1), new Vec3i( 1,  0, -1) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XN_ZP = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i(-1,  0,  0), new Vec3i( 0,  0,  1), new Vec3i(-1,  0,  1) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XP_ZP = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 1,  0,  0), new Vec3i( 0,  0,  1), new Vec3i( 1,  0,  1) };
    private static final Vec3i[][] EDGE_NEIGHBOR_OFFSETS_Y = new Vec3i[][] { EDGE_NEIGHBOR_OFFSETS_XN_ZN, EDGE_NEIGHBOR_OFFSETS_XP_ZN, EDGE_NEIGHBOR_OFFSETS_XN_ZP, EDGE_NEIGHBOR_OFFSETS_XP_ZP };

    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XN_YN = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i(-1,  0,  0), new Vec3i( 0, -1,  0), new Vec3i(-1, -1,  0) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XP_YN = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 1,  0,  0), new Vec3i( 0, -1,  0), new Vec3i( 1, -1,  0) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XN_YP = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i(-1,  0,  0), new Vec3i( 0,  1,  0), new Vec3i(-1,  1,  0) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_XP_YP = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 1,  0,  0), new Vec3i( 0,  1,  0), new Vec3i( 1,  1,  0) };
    private static final Vec3i[][] EDGE_NEIGHBOR_OFFSETS_Z = new Vec3i[][] { EDGE_NEIGHBOR_OFFSETS_XN_YN, EDGE_NEIGHBOR_OFFSETS_XP_YN, EDGE_NEIGHBOR_OFFSETS_XN_YP, EDGE_NEIGHBOR_OFFSETS_XP_YP };

    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_YN_ZN = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 0, -1,  0), new Vec3i( 0,  0, -1), new Vec3i( 0, -1, -1) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_YP_ZN = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 0,  1,  0), new Vec3i( 0,  0, -1), new Vec3i( 0,  1, -1) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_YN_ZP = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 0, -1,  0), new Vec3i( 0,  0,  1), new Vec3i( 0, -1,  1) };
    private static final Vec3i[] EDGE_NEIGHBOR_OFFSETS_YP_ZP = new Vec3i[] { new Vec3i( 0,  0,  0), new Vec3i( 0,  1,  0), new Vec3i( 0,  0,  1), new Vec3i( 0,  1,  1) };
    private static final Vec3i[][] EDGE_NEIGHBOR_OFFSETS_X = new Vec3i[][] { EDGE_NEIGHBOR_OFFSETS_YN_ZN, EDGE_NEIGHBOR_OFFSETS_YP_ZN, EDGE_NEIGHBOR_OFFSETS_YN_ZP, EDGE_NEIGHBOR_OFFSETS_YP_ZP };

    public static Vec3i[] getEdgeNeighborOffsets(EnumFacing.Axis axis, int cornerIndex)
    {
        switch (axis)
        {
            case X: return EDGE_NEIGHBOR_OFFSETS_X[cornerIndex];
            case Y: return EDGE_NEIGHBOR_OFFSETS_Y[cornerIndex];
            case Z: return EDGE_NEIGHBOR_OFFSETS_Z[cornerIndex];
        }

        return null;
    }

    public static boolean areAllCoordinatesAtLeast(Vec3i vec, int threshold)
    {
        return vec.getX() >= threshold && vec.getY() >= threshold && vec.getZ() >= threshold;
    }

    public static BlockPos getTransformedPlacementPosition(BlockPos posWithinSub, SchematicPlacement schematicPlacement, SubRegionPlacement placement)
    {
        BlockPos pos = posWithinSub;
        pos = getTransformedBlockPos(pos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        pos = getTransformedBlockPos(pos, placement.getMirror(), placement.getRotation());
        return pos;
    }

    public static boolean arePositionsWithinWorld(World world, BlockPos pos1, BlockPos pos2)
    {
        if (pos1.getY() >= LayerRange.WORLD_VERTICAL_SIZE_MIN && pos1.getY() <= LayerRange.WORLD_VERTICAL_SIZE_MAX &&
            pos2.getY() >= LayerRange.WORLD_VERTICAL_SIZE_MIN && pos2.getY() <= LayerRange.WORLD_VERTICAL_SIZE_MAX)
        {
            WorldBorder border = world.getWorldBorder();
            return border.contains(pos1) && border.contains(pos2);
        }

        return false;
    }

    public static boolean isBoxWithinWorld(World world, Box box)
    {
        if (box.getPos1() != null && box.getPos2() != null)
        {
            return arePositionsWithinWorld(world, box.getPos1(), box.getPos2());
        }

        return false;
    }

    public static boolean isPlacementWithinWorld(World world, SchematicPlacement schematicPlacement, boolean respectRenderRange)
    {
        LayerRange range = DataManager.getRenderLayerRange();
        BlockPos.MutableBlockPos posMutable1 = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos posMutable2 = new BlockPos.MutableBlockPos();

        for (Box box : schematicPlacement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED).values())
        {
            if (respectRenderRange)
            {
                if (range.intersectsBox(box.getPos1(), box.getPos2()))
                {
                    IntBoundingBox bb = range.getClampedArea(box.getPos1(), box.getPos2());

                    if (bb != null)
                    {
                        posMutable1.setPos(bb.minX, bb.minY, bb.minZ);
                        posMutable2.setPos(bb.maxX, bb.maxY, bb.maxZ);

                        if (arePositionsWithinWorld(world, posMutable1, posMutable2) == false)
                        {
                            return false;
                        }
                    }
                }
            }
            else if (isBoxWithinWorld(world, box) == false)
            {
                return false;
            }
        }

        return true;
    }

    public static Vec3i getAreaSizeFromRelativeEndPosition(Vec3i posEndRelative)
    {
        int x = posEndRelative.getX();
        int y = posEndRelative.getY();
        int z = posEndRelative.getZ();

        x = x >= 0 ? x + 1 : x - 1;
        y = y >= 0 ? y + 1 : y - 1;
        z = z >= 0 ? z + 1 : z - 1;

        return new Vec3i(x, y, z);
    }

    public static Vec3i getAreaSizeFromRelativeEndPositionAbs(Vec3i posEndRelative)
    {
        int x = posEndRelative.getX();
        int y = posEndRelative.getY();
        int z = posEndRelative.getZ();

        x = x >= 0 ? x + 1 : x - 1;
        y = y >= 0 ? y + 1 : y - 1;
        z = z >= 0 ? z + 1 : z - 1;

        return new Vec3i(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    public static Vec3i getRelativeEndPositionFromAreaSize(Vec3i size)
    {
        int x = size.getX();
        int y = size.getY();
        int z = size.getZ();

        x = x >= 0 ? x - 1 : x + 1;
        y = y >= 0 ? y - 1 : y + 1;
        z = z >= 0 ? z - 1 : z + 1;

        return new Vec3i(x, y, z);
    }

    public static long getAreaVolume(Vec3i size)
    {
        return (long) size.getX() * (long) size.getY() * (long) size.getZ();
    }

    public static List<SelectionBox> getValidBoxes(AreaSelection area)
    {
        List<SelectionBox> boxes = new ArrayList<>();
        Collection<SelectionBox> originalBoxes = area.getAllSubRegionBoxes();

        for (SelectionBox box : originalBoxes)
        {
            if (isBoxValid(box))
            {
                boxes.add(box);
            }
        }

        return boxes;
    }

    public static boolean isBoxValid(Box box)
    {
        return box.getPos1() != null && box.getPos2() != null;
    }

    public static Vec3i getAbsoluteAreaSize(Vec3i size)
    {
        return new Vec3i(Math.abs(size.getX()), Math.abs(size.getY()), Math.abs(size.getZ()));
    }

    public static BlockPos getEnclosingAreaSize(AreaSelection area)
    {
        return getEnclosingAreaSize(area.getAllSubRegionBoxes());
    }

    public static BlockPos getEnclosingAreaSize(Collection<? extends Box> boxes)
    {
        Pair<BlockPos, BlockPos> pair = getEnclosingAreaCorners(boxes);
        return pair.getRight().subtract(pair.getLeft()).add(1, 1, 1);
    }

    /**
     * Returns the min and max corners of the enclosing box around the given collection of boxes.
     * The minimum corner is the left entry and the maximum corner is the right entry of the pair.
     * @param boxes
     * @return
     */
    @Nullable
    public static Pair<BlockPos, BlockPos> getEnclosingAreaCorners(Collection<? extends Box> boxes)
    {
        if (boxes.isEmpty())
        {
            return null;
        }

        BlockPos.MutableBlockPos posMin = new BlockPos.MutableBlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        BlockPos.MutableBlockPos posMax = new BlockPos.MutableBlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        for (Box box : boxes)
        {
            getMinMaxCoords(posMin, posMax, box.getPos1());
            getMinMaxCoords(posMin, posMax, box.getPos2());
        }

        return Pair.of(posMin.toImmutable(), posMax.toImmutable());
    }

    /**
     * Returns the min and max corners of the enclosing box around the given collection of boxes.
     * The minimum corner is the left entry and the maximum corner is the right entry of the pair.
     */
    @Nullable
    public static Pair<BlockPos, BlockPos> getEnclosingAreaCornersForRegions(Collection<ISchematicRegion> regions)
    {
        if (regions.isEmpty())
        {
            return null;
        }

        BlockPos.MutableBlockPos posMin = new BlockPos.MutableBlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        BlockPos.MutableBlockPos posMax = new BlockPos.MutableBlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        BlockPos.MutableBlockPos posEnd = new BlockPos.MutableBlockPos();

        for (ISchematicRegion region : regions)
        {
            BlockPos pos = region.getPosition();
            Vec3i endRel = getRelativeEndPositionFromAreaSize(region.getSize());
            posEnd.setPos(pos.getX() + endRel.getX(), pos.getY() + endRel.getY(), pos.getZ() + endRel.getZ());

            getMinMaxCoords(posMin, posMax, pos);
            getMinMaxCoords(posMin, posMax, posEnd);
        }

        return Pair.of(posMin.toImmutable(), posMax.toImmutable());
    }

    private static void getMinMaxCoords(BlockPos.MutableBlockPos posMin, BlockPos.MutableBlockPos posMax, @Nullable BlockPos posToCheck)
    {
        if (posToCheck != null)
        {
            posMin.setPos(  Math.min(posMin.getX(), posToCheck.getX()),
                            Math.min(posMin.getY(), posToCheck.getY()),
                            Math.min(posMin.getZ(), posToCheck.getZ()));

            posMax.setPos(  Math.max(posMax.getX(), posToCheck.getX()),
                            Math.max(posMax.getY(), posToCheck.getY()),
                            Math.max(posMax.getZ(), posToCheck.getZ()));
        }
    }

    public static int getTotalVolume(Collection<? extends Box> boxes)
    {
        if (boxes.isEmpty())
        {
            return 0;
        }

        int volume = 0;

        for (Box box : boxes)
        {
            if (isBoxValid(box))
            {
                BlockPos min = fi.dy.masa.malilib.util.PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
                BlockPos max = fi.dy.masa.malilib.util.PositionUtils.getMaxCorner(box.getPos1(), box.getPos2());
                volume += (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
            }
        }

        return volume;
    }

    public static ImmutableMap<String, IntBoundingBox> getBoxesWithinChunk(int chunkX, int chunkZ, ImmutableMap<String, SelectionBox> subRegions)
    {
        ImmutableMap.Builder<String, IntBoundingBox> builder = new ImmutableMap.Builder<>();

        for (Map.Entry<String, SelectionBox> entry : subRegions.entrySet())
        {
            SelectionBox box = entry.getValue();
            IntBoundingBox bb = box != null ? getBoundsWithinChunkForBox(box, chunkX, chunkZ) : null;

            if (bb != null)
            {
                builder.put(entry.getKey(), bb);
            }
        }

        return builder.build();
    }

    public static ImmutableList<IntBoundingBox> getBoxesWithinChunk(int chunkX, int chunkZ, Collection<? extends Box> boxes)
    {
        ImmutableList.Builder<IntBoundingBox> builder = new ImmutableList.Builder<>();

        for (Box box : boxes)
        {
            IntBoundingBox bb = getBoundsWithinChunkForBox(box, chunkX, chunkZ);

            if (bb != null)
            {
                builder.add(bb);
            }
        }

        return builder.build();
    }

    public static Set<ChunkPos> getTouchedChunks(ImmutableMap<String, SelectionBox> boxes)
    {
        return getTouchedChunksForBoxes(boxes.values());
    }

    public static Set<ChunkPos> getTouchedChunksForBoxes(Collection<? extends Box> boxes)
    {
        Set<ChunkPos> set = new HashSet<>();

        for (Box box : boxes)
        {
            final int boxXMin = Math.min(box.getPos1().getX(), box.getPos2().getX()) >> 4;
            final int boxZMin = Math.min(box.getPos1().getZ(), box.getPos2().getZ()) >> 4;
            final int boxXMax = Math.max(box.getPos1().getX(), box.getPos2().getX()) >> 4;
            final int boxZMax = Math.max(box.getPos1().getZ(), box.getPos2().getZ()) >> 4;

            for (int cz = boxZMin; cz <= boxZMax; ++cz)
            {
                for (int cx = boxXMin; cx <= boxXMax; ++cx)
                {
                    set.add(new ChunkPos(cx, cz));
                }
            }
        }

        return set;
    }

    @Nullable
    public static IntBoundingBox getBoundsWithinChunkForBox(Box box, int chunkX, int chunkZ)
    {
        final int chunkXMin = chunkX << 4;
        final int chunkZMin = chunkZ << 4;
        final int chunkXMax = chunkXMin + 15;
        final int chunkZMax = chunkZMin + 15;

        final int boxXMin = Math.min(box.getPos1().getX(), box.getPos2().getX());
        final int boxZMin = Math.min(box.getPos1().getZ(), box.getPos2().getZ());
        final int boxXMax = Math.max(box.getPos1().getX(), box.getPos2().getX());
        final int boxZMax = Math.max(box.getPos1().getZ(), box.getPos2().getZ());

        boolean notOverlapping = boxXMin > chunkXMax || boxZMin > chunkZMax || boxXMax < chunkXMin || boxZMax < chunkZMin;

        if (notOverlapping == false)
        {
            final int xMin = Math.max(chunkXMin, boxXMin);
            final int yMin = Math.min(box.getPos1().getY(), box.getPos2().getY());
            final int zMin = Math.max(chunkZMin, boxZMin);
            final int xMax = Math.min(chunkXMax, boxXMax);
            final int yMax = Math.max(box.getPos1().getY(), box.getPos2().getY());
            final int zMax = Math.min(chunkZMax, boxZMax);

            return new IntBoundingBox(xMin, yMin, zMin, xMax, yMax, zMax);
        }

        return null;
    }

    public static void getPerChunkBoxes(Collection<? extends Box> boxes, BiConsumer<ChunkPos, IntBoundingBox> consumer)
    {
        for (Box box : boxes)
        {
            BlockPos pos1 = box.getPos1();
            BlockPos pos2 = box.getPos2();
            final int boxMinX = Math.min(pos1.getX(), pos2.getX());
            final int boxMinY = Math.min(pos1.getY(), pos2.getY());
            final int boxMinZ = Math.min(pos1.getZ(), pos2.getZ());
            final int boxMaxX = Math.max(pos1.getX(), pos2.getX());
            final int boxMaxY = Math.max(pos1.getY(), pos2.getY());
            final int boxMaxZ = Math.max(pos1.getZ(), pos2.getZ());
            final int boxMinChunkX = boxMinX >> 4;
            final int boxMinChunkZ = boxMinZ >> 4;
            final int boxMaxChunkX = boxMaxX >> 4;
            final int boxMaxChunkZ = boxMaxZ >> 4;

            for (int cz = boxMinChunkZ; cz <= boxMaxChunkZ; ++cz)
            {
                for (int cx = boxMinChunkX; cx <= boxMaxChunkX; ++cx)
                {
                    final int chunkMinX = cx << 4;
                    final int chunkMinZ = cz << 4;
                    final int minX = Math.max(chunkMinX     , boxMinX);
                    final int minZ = Math.max(chunkMinZ     , boxMinZ);
                    final int maxX = Math.min(chunkMinX + 15, boxMaxX);
                    final int maxZ = Math.min(chunkMinZ + 15, boxMaxZ);

                    consumer.accept(new ChunkPos(cx, cz), new IntBoundingBox(minX, boxMinY, minZ, maxX, boxMaxY, maxZ));
                }
            }
        }
    }

    public static void getLayerRangeClampedPerChunkBoxes(Collection<? extends Box> boxes,
                                                         LayerRange range,
                                                         BiConsumer<ChunkPos, IntBoundingBox> consumer)
    {
        for (Box box : boxes)
        {
            final int rangeMin = range.getMinLayerBoundary();
            final int rangeMax = range.getMaxLayerBoundary();
            int boxMinX = Math.min(box.getPos1().getX(), box.getPos2().getX());
            int boxMinY = Math.min(box.getPos1().getY(), box.getPos2().getY());
            int boxMinZ = Math.min(box.getPos1().getZ(), box.getPos2().getZ());
            int boxMaxX = Math.max(box.getPos1().getX(), box.getPos2().getX());
            int boxMaxY = Math.max(box.getPos1().getY(), box.getPos2().getY());
            int boxMaxZ = Math.max(box.getPos1().getZ(), box.getPos2().getZ());

            switch (range.getAxis())
            {
                case X:
                    if (rangeMax < boxMinX || rangeMin > boxMaxX) { continue; }
                    boxMinX = Math.max(boxMinX, rangeMin);
                    boxMaxX = Math.min(boxMaxX, rangeMax);
                    break;
                case Y:
                    if (rangeMax < boxMinY || rangeMin > boxMaxY) { continue; }
                    boxMinY = Math.max(boxMinY, rangeMin);
                    boxMaxY = Math.min(boxMaxY, rangeMax);
                    break;
                case Z:
                    if (rangeMax < boxMinZ || rangeMin > boxMaxZ) { continue; }
                    boxMinZ = Math.max(boxMinZ, rangeMin);
                    boxMaxZ = Math.min(boxMaxZ, rangeMax);
                    break;
            }

            final int boxMinChunkX = boxMinX >> 4;
            final int boxMinChunkZ = boxMinZ >> 4;
            final int boxMaxChunkX = boxMaxX >> 4;
            final int boxMaxChunkZ = boxMaxZ >> 4;

            for (int cz = boxMinChunkZ; cz <= boxMaxChunkZ; ++cz)
            {
                for (int cx = boxMinChunkX; cx <= boxMaxChunkX; ++cx)
                {
                    final int chunkMinX = cx << 4;
                    final int chunkMinZ = cz << 4;
                    final int chunkMaxX = chunkMinX + 15;
                    final int chunkMaxZ = chunkMinZ + 15;
                    final int minX = Math.max(chunkMinX, boxMinX);
                    final int minZ = Math.max(chunkMinZ, boxMinZ);
                    final int maxX = Math.min(chunkMaxX, boxMaxX);
                    final int maxZ = Math.min(chunkMaxZ, boxMaxZ);

                    consumer.accept(new ChunkPos(cx, cz), new IntBoundingBox(minX, boxMinY, minZ, maxX, boxMaxY, maxZ));
                }
            }
        }
    }

    /**
     * Creates an enclosing AABB around the given positions. They will both be inside the box.
     */
    public static AxisAlignedBB createEnclosingAABB(BlockPos pos1, BlockPos pos2)
    {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

        return createAABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static AxisAlignedBB createAABBFrom(IntBoundingBox bb)
    {
        return createAABB(bb.minX, bb.minY, bb.minZ, bb.maxX + 1, bb.maxY + 1, bb.maxZ + 1);
    }

    /**
     * Creates an AABB for the given position
     */
    public static AxisAlignedBB createAABBForPosition(BlockPos pos)
    {
        return createAABBForPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Creates an AABB for the given position
     */
    public static AxisAlignedBB createAABBForPosition(long posLong)
    {
        int x = fi.dy.masa.malilib.util.PositionUtils.unpackX(posLong);
        int y = fi.dy.masa.malilib.util.PositionUtils.unpackY(posLong);
        int z = fi.dy.masa.malilib.util.PositionUtils.unpackZ(posLong);
        return createAABBForPosition(x, y, z);
    }

    /**
     * Creates an AABB for the given position
     */
    public static AxisAlignedBB createAABBForPosition(int x, int y, int z)
    {
        return createAABB(x, y, z, x + 1, y + 1, z + 1);
    }

    /**
     * Creates an AABB with the given bounds
     */
    public static AxisAlignedBB createAABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
    {
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static Box expandOrShrinkBox(Box box, int amount, EnumFacing side)
    {
        BlockPos pos1 = box.getPos1();
        BlockPos pos2 = box.getPos2();

        EnumFacing.Axis axis = side.getAxis();
        boolean positiveSide = side.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
        ICoordinateAccessor accessor = axis == EnumFacing.Axis.X ? BLOCKPOS_X_ACCESSOR : (axis == EnumFacing.Axis.Y ? BLOCKPOS_Y_ACCESSOR : BLOCKPOS_Z_ACCESSOR);
        int modifyAmount = positiveSide ? amount : -amount; // the amount is inversed when adjusting the negative axis sides

        // The corners are at the same position on the targeted axis
        if (accessor.getValue(pos1) == accessor.getValue(pos2))
        {
            // Only allow the box to grow from the one thick state
            if (amount > 0)
            {
                // corner 2 should be on the to-be-modified side of the box
                if (positiveSide)
                {
                    pos2 = accessor.setValue(pos2, accessor.getValue(pos2) + modifyAmount);
                }
                // corner 1 should be on the to-be-modified side of the box
                else
                {
                    pos1 = accessor.setValue(pos1, accessor.getValue(pos1) + modifyAmount);
                }
            }
            else
            {
                return box;
            }
        }
        else
        {
            // corner 1 is on the to-be-modified side of the box
            if (accessor.getValue(pos1) > accessor.getValue(pos2) == positiveSide)
            {
                pos1 = accessor.setValue(pos1, accessor.getValue(pos1) + modifyAmount);
            }
            // corner 2 is on the to-be-modified side of the box
            else
            {
                pos2 = accessor.setValue(pos2, accessor.getValue(pos2) + modifyAmount);
            }
        }

        Box boxNew = box.copy();
        boxNew.setPos1(pos1);
        boxNew.setPos2(pos2);

        return boxNew;
    }

    public static Box growOrShrinkBox(Box box, int amount)
    {
        BlockPos pos1 = box.getPos1();
        BlockPos pos2 = box.getPos2();

        if (pos1 == null || pos2 == null)
        {
            if (pos1 == null && pos2 == null)
            {
                return box;
            }
            else if (pos2 == null)
            {
                pos2 = pos1;
            }
            else
            {
                pos1 = pos2;
            }
        }

        Pair<Integer, Integer> x = growCoordinatePair(pos1.getX(), pos2.getX(), amount);
        Pair<Integer, Integer> y = growCoordinatePair(pos1.getY(), pos2.getY(), amount);
        Pair<Integer, Integer> z = growCoordinatePair(pos1.getZ(), pos2.getZ(), amount);

        Box boxNew = box.copy();
        boxNew.setPos1(new BlockPos(x.getLeft(), y.getLeft(), z.getLeft()));
        boxNew.setPos2(new BlockPos(x.getRight(), y.getRight(), z.getRight()));

        return boxNew;
    }

    private static Pair<Integer, Integer> growCoordinatePair(int v1, int v2, int amount)
    {
        if (v2 >= v1)
        {
            if (v2 + amount >= v1)
            {
                v2 += amount;
            }

            if (v1 - amount <= v2)
            {
                v1 -= amount;
            }
        }
        else if (v1 > v2)
        {
            if (v1 + amount >= v2)
            {
                v1 += amount;
            }

            if (v2 - amount <= v1)
            {
                v2 -= amount;
            }
        }

        return Pair.of(v1, v2);
    }

    public static void growOrShrinkCurrentSelection(boolean grow)
    {
        SelectionManager sm = DataManager.getSelectionManager();
        AreaSelection area = sm.getCurrentSelection();
        World world = Minecraft.getMinecraft().world;

        if (area == null || world == null)
        {
            MessageDispatcher.error("litematica.message.error.no_area_selected");
            return;
        }

        Box box = area.getSelectedSubRegionBox();

        if (box == null || (box.getPos1() == null && box.getPos2() == null))
        {
            MessageDispatcher.error("litematica.error.area_selection.grow.no_sub_region_selected");
            return;
        }

        if (box != null && (box.getPos1() != null || box.getPos2() != null))
        {
            int amount = 1;
            Box boxNew = box.copy();

            for (int i = 0; i < 256; ++i)
            {
                if (grow)
                {
                    boxNew = growOrShrinkBox(boxNew, amount);
                }

                BlockPos pos1 = boxNew.getPos1();
                BlockPos pos2 = boxNew.getPos2();
                int xMin = Math.min(pos1.getX(), pos2.getX());
                int yMin = Math.min(pos1.getY(), pos2.getY());
                int zMin = Math.min(pos1.getZ(), pos2.getZ());
                int xMax = Math.max(pos1.getX(), pos2.getX());
                int yMax = Math.max(pos1.getY(), pos2.getY());
                int zMax = Math.max(pos1.getZ(), pos2.getZ());
                int emptySides = 0;

                // Slices along the z axis
                if (WorldUtils.isSliceEmpty(world, EnumFacing.Axis.X, new BlockPos(xMin, yMin, zMin), new BlockPos(xMin, yMax, zMax)))
                {
                    xMin += amount;
                    ++emptySides;
                }

                if (WorldUtils.isSliceEmpty(world, EnumFacing.Axis.X, new BlockPos(xMax, yMin, zMin), new BlockPos(xMax, yMax, zMax)))
                {
                    xMax -= amount;
                    ++emptySides;
                }

                // Slices along the x/z plane
                if (WorldUtils.isSliceEmpty(world, EnumFacing.Axis.Y, new BlockPos(xMin, yMin, zMin), new BlockPos(xMax, yMin, zMax)))
                {
                    yMin += amount;
                    ++emptySides;
                }

                if (WorldUtils.isSliceEmpty(world, EnumFacing.Axis.Y, new BlockPos(xMin, yMax, zMin), new BlockPos(xMax, yMax, zMax)))
                {
                    yMax -= amount;
                    ++emptySides;
                }

                // Slices along the x axis
                if (WorldUtils.isSliceEmpty(world, EnumFacing.Axis.Z, new BlockPos(xMin, yMin, zMin), new BlockPos(xMax, yMax, zMin)))
                {
                    zMin += amount;
                    ++emptySides;
                }

                if (WorldUtils.isSliceEmpty(world, EnumFacing.Axis.Z, new BlockPos(xMin, yMin, zMax), new BlockPos(xMax, yMax, zMax)))
                {
                    zMax -= amount;
                    ++emptySides;
                }

                boxNew.setPos1(new BlockPos(xMin, yMin, zMin));
                boxNew.setPos2(new BlockPos(xMax, yMax, zMax));

                if (grow && emptySides >= 6)
                {
                    break;
                }
                else if (grow == false && emptySides == 0)
                {
                    break;
                }
            }

            area.setSelectedSubRegionCornerPos(boxNew.getPos1(), Corner.CORNER_1);
            area.setSelectedSubRegionCornerPos(boxNew.getPos2(), Corner.CORNER_2);
        }
    }

    public static BlockPos getPlacementPositionOffsetToInfrontOfPlayer(BlockPos origPos)
    {
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        SchematicPlacement placement = manager.getSelectedSchematicPlacement();

        return getPlacementPositionOffsetToInfrontOfPlayer(origPos, placement);
    }

    public static BlockPos getPlacementPositionOffsetToInfrontOfPlayer(BlockPos newOrigin, @Nullable SchematicPlacement placement)
    {
        if (Configs.Generic.PLACEMENTS_INFRONT.getBooleanValue())
        {
            Entity entity = EntityUtils.getCameraEntity();

            if (placement != null && entity != null)
            {
                SubRegionPlacement sub = placement.getSelectedSubRegionPlacement();
                Box box = null;

                if (sub != null)
                {
                    String regionName = placement.getSelectedSubRegionName();
                    ImmutableMap<String, SelectionBox> map = placement.getSubRegionBoxFor(regionName, RequiredEnabled.PLACEMENT_ENABLED);
                    box = map.get(regionName);
                }
                else
                {
                    box = placement.getEnclosingBox();
                }

                if (box != null)
                {
                    BlockPos originOffset = newOrigin.subtract(placement.getOrigin());
                    BlockPos corner1 = box.getPos1().add(originOffset);
                    BlockPos corner2 = box.getPos2().add(originOffset);
                    BlockPos entityPos = new BlockPos(entity);
                    EnumFacing entityFrontDirection = entity.getHorizontalFacing();
                    EnumFacing entitySideDirection = fi.dy.masa.malilib.util.PositionUtils.getClosestSideDirection(entity);
                    Vec3i alignmentFrontOffset = getOffsetToMoveBoxInfrontOfEntityPos(entityPos, entityFrontDirection, corner1, corner2);
                    Vec3i alignmentSideOffset = getOffsetToMoveBoxInfrontOfEntityPos(entityPos, entitySideDirection, corner1, corner2);

                    return newOrigin.add(alignmentFrontOffset).add(alignmentSideOffset);
                }
            }
        }

        return newOrigin;
    }

    public static Vec3i getOffsetToMoveBoxInfrontOfEntityPos(BlockPos entityPos, EnumFacing entityHorizontalFacing, BlockPos corner1, BlockPos corner2)
    {
        BlockPos minPos = fi.dy.masa.malilib.util.PositionUtils.getMinCorner(corner1, corner2);
        BlockPos maxPos = fi.dy.masa.malilib.util.PositionUtils.getMaxCorner(corner1, corner2);
        int offX = 0;
        int offZ = 0;

        switch (entityHorizontalFacing)
        {
            case EAST:  offX = entityPos.getX() - minPos.getX() + 1; break;
            case WEST:  offX = entityPos.getX() - maxPos.getX() - 1; break;
            case SOUTH: offZ = entityPos.getZ() - minPos.getZ() + 1; break;
            case NORTH: offZ = entityPos.getZ() - maxPos.getZ() - 1; break;
            default:
        }

        return new Vec3i(offX, 0, offZ);
    }

    /**
     * Mirrors and then rotates the given position around the origin
     */
    public static BlockPos getTransformedBlockPos(BlockPos pos, Mirror mirror, Rotation rotation)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        boolean isMirrored = true;

        switch (mirror)
        {
            // LEFT_RIGHT is essentially NORTH_SOUTH
            case LEFT_RIGHT:
                z = -z;
                break;
            // FRONT_BACK is essentially EAST_WEST
            case FRONT_BACK:
                x = -x;
                break;
            default:
                isMirrored = false;
        }

        switch (rotation)
        {
            case CLOCKWISE_90:
                return new BlockPos(-z, y,  x);
            case COUNTERCLOCKWISE_90:
                return new BlockPos( z, y, -x);
            case CLOCKWISE_180:
                return new BlockPos(-x, y, -z);
            default:
                return isMirrored ? new BlockPos(x, y, z) : pos;
        }
    }

    public static BlockPos getReverseTransformedBlockPos(BlockPos pos, Mirror mirror, Rotation rotation)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        boolean isRotated = true;
        int tmp = x;

        switch (rotation)
        {
            case CLOCKWISE_90:
                x = z;
                z = -tmp;
                break;
            case COUNTERCLOCKWISE_90:
                x = -z;
                z = tmp;
                break;
            case CLOCKWISE_180:
                x = -x;
                z = -z;
                break;
            default:
                isRotated = false;
        }

        switch (mirror)
        {
            // LEFT_RIGHT is essentially NORTH_SOUTH
            case LEFT_RIGHT:
                z = -z;
                break;
            // FRONT_BACK is essentially EAST_WEST
            case FRONT_BACK:
                x = -x;
                break;
            default:
                if (isRotated == false)
                {
                    return pos;
                }
        }

        return new BlockPos(x, y, z);
    }

    /**
     * Does the opposite transform from getTransformedBlockPos(), to return the original,
     * non-transformed position from the transformed position.
     */
    public static BlockPos getOriginalPositionFromTransformed(BlockPos pos, Mirror mirror, Rotation rotation)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int tmp;
        boolean noRotation = false;

        switch (rotation)
        {
            case CLOCKWISE_90:
                tmp = x;
                x = -z;
                z = tmp;
            case COUNTERCLOCKWISE_90:
                tmp = x;
                x = z;
                z = -tmp;
            case CLOCKWISE_180:
                x = -x;
                z = -z;
            default:
                noRotation = true;
        }

        switch (mirror)
        {
            case LEFT_RIGHT:
                z = -z;
                break;
            case FRONT_BACK:
                x = -x;
                break;
            default:
                if (noRotation)
                {
                    return pos;
                }
        }

        return new BlockPos(x, y, z);
    }

    public static Vec3d getTransformedPosition(Vec3d originalPos, Mirror mirror, Rotation rotation)
    {
        double x = originalPos.x;
        double y = originalPos.y;
        double z = originalPos.z;
        boolean transformed = true;

        switch (mirror)
        {
            case LEFT_RIGHT:
                z = 1.0D - z;
                break;
            case FRONT_BACK:
                x = 1.0D - x;
                break;
            default:
                transformed = false;
        }

        switch (rotation)
        {
            case COUNTERCLOCKWISE_90:
                return new Vec3d(z, y, 1.0D - x);
            case CLOCKWISE_90:
                return new Vec3d(1.0D - z, y, x);
            case CLOCKWISE_180:
                return new Vec3d(1.0D - x, y, 1.0D - z);
            default:
                return transformed ? new Vec3d(x, y, z) : originalPos;
        }
    }

    public static Rotation getReverseRotation(Rotation rotationIn)
    {
        switch (rotationIn)
        {
            case COUNTERCLOCKWISE_90:
                return Rotation.CLOCKWISE_90;
            case CLOCKWISE_90:
                return Rotation.COUNTERCLOCKWISE_90;
            case CLOCKWISE_180:
                return Rotation.CLOCKWISE_180;
            default:
                return rotationIn;
        }
    }

    public static BlockPos getModifiedPartiallyLockedPosition(BlockPos posOriginal, BlockPos posNew, int lockMask)
    {
        if (lockMask != 0)
        {
            int x = posNew.getX();
            int y = posNew.getY();
            int z = posNew.getZ();

            if ((lockMask & (0x1 << Coordinate.X.ordinal())) != 0)
            {
                x = posOriginal.getX();
            }

            if ((lockMask & (0x1 << Coordinate.Y.ordinal())) != 0)
            {
                y = posOriginal.getY();
            }

            if ((lockMask & (0x1 << Coordinate.Z.ordinal())) != 0)
            {
                z = posOriginal.getZ();
            }

            posNew = new BlockPos(x, y, z);
        }

        return posNew;
    }

    /**
     * Gets the "front" facing from the given positions,
     * so that pos1 is in the "front left" corner and pos2 is in the "back right" corner
     * of the area, when looking at the "front" face of the area.
     */
    public static EnumFacing getFacingFromPositions(BlockPos pos1, BlockPos pos2)
    {
        if (pos1 == null || pos2 == null)
        {
            return null;
        }

        return getFacingFromPositions(pos1.getX(), pos1.getZ(), pos2.getX(), pos2.getZ());
    }

    private static EnumFacing getFacingFromPositions(int x1, int z1, int x2, int z2)
    {
        if (x2 == x1)
        {
            return z2 > z1 ? EnumFacing.SOUTH : EnumFacing.NORTH;
        }

        if (z2 == z1)
        {
            return x2 > x1 ? EnumFacing.EAST : EnumFacing.WEST;
        }

        if (x2 > x1)
        {
            return z2 > z1 ? EnumFacing.EAST : EnumFacing.NORTH;
        }

        return z2 > z1 ? EnumFacing.SOUTH : EnumFacing.WEST;
    }

    public static String getRotationNameShort(Rotation rotation)
    {
        switch (rotation)
        {
            case CLOCKWISE_90:          return "CW_90";
            case CLOCKWISE_180:         return "CW_180";
            case COUNTERCLOCKWISE_90:   return "CCW_90";
            case NONE:
            default:                    return "NONE";
        }
    }

    public static String getMirrorName(Mirror mirror)
    {
        switch (mirror)
        {
            case FRONT_BACK:    return "FRONT_BACK";
            case LEFT_RIGHT:    return "LEFT_RIGHT";
            case NONE:
            default:            return "NONE";
        }
    }

    public static float getRotatedYaw(float yaw, Rotation rotation)
    {
        yaw = MathHelper.wrapDegrees(yaw);

        switch (rotation)
        {
            case CLOCKWISE_180:
                yaw += 180.0F;
                break;
            case COUNTERCLOCKWISE_90:
                yaw += 270.0F;
                break;
            case CLOCKWISE_90:
                yaw += 90.0F;
                break;
            default:
        }

        return yaw;
    }

    public static float getMirroredYaw(float yaw, Mirror mirror)
    {
        yaw = MathHelper.wrapDegrees(yaw);

        switch (mirror)
        {
            case LEFT_RIGHT:
                yaw = 180.0F - yaw;
                break;
            case FRONT_BACK:
                yaw = -yaw;
                break;
            default:
        }

        return yaw;
    }

    public static int getIntBoxValue(IntBoundingBox box, IntBoxCoordType type)
    {
        switch (type)
        {
            case MIN_X: return box.minX;
            case MIN_Y: return box.minY;
            case MIN_Z: return box.minZ;
            case MAX_X: return box.maxX;
            case MAX_Y: return box.maxY;
            case MAX_Z: return box.maxZ;
        }

        return 0;
    }

    public static IntBoundingBox setIntBoxValue(IntBoundingBox old, IntBoxCoordType type, int value)
    {
        int minX = old.minX;
        int minY = old.minY;
        int minZ = old.minZ;
        int maxX = old.maxX;
        int maxY = old.maxY;
        int maxZ = old.maxZ;

        switch (type)
        {
            case MIN_X: minX = value; break;
            case MIN_Y: minY = value; break;
            case MIN_Z: minZ = value; break;
            case MAX_X: maxX = value; break;
            case MAX_Y: maxY = value; break;
            case MAX_Z: maxZ = value; break;
        }

        return new IntBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static class BlockPosComparator implements Comparator<BlockPos>
    {
        private BlockPos posReference = BlockPos.ORIGIN;
        private boolean closestFirst;

        public void setClosestFirst(boolean closestFirst)
        {
            this.closestFirst = closestFirst;
        }

        public void setReferencePosition(BlockPos pos)
        {
            this.posReference = pos;
        }

        @Override
        public int compare(BlockPos pos1, BlockPos pos2)
        {
            double dist1 = pos1.distanceSq(this.posReference);
            double dist2 = pos2.distanceSq(this.posReference);

            if (dist1 == dist2)
            {
                return 0;
            }

            return dist1 < dist2 == this.closestFirst ? -1 : 1;
        }
    }

    public static class ChunkPosComparator implements Comparator<ChunkPos>
    {
        private BlockPos posReference = BlockPos.ORIGIN;
        private boolean closestFirst;

        public ChunkPosComparator setClosestFirst(boolean closestFirst)
        {
            this.closestFirst = closestFirst;
            return this;
        }

        public ChunkPosComparator setReferencePosition(BlockPos pos)
        {
            this.posReference = pos;
            return this;
        }

        @Override
        public int compare(ChunkPos pos1, ChunkPos pos2)
        {
            double dist1 = this.distanceSq(pos1);
            double dist2 = this.distanceSq(pos2);

            if (dist1 == dist2)
            {
                return 0;
            }

            return dist1 < dist2 == this.closestFirst ? -1 : 1;
        }

        private double distanceSq(ChunkPos pos)
        {
            double dx = (double) (pos.x << 4) - this.posReference.getX();
            double dz = (double) (pos.z << 4) - this.posReference.getZ();

            return dx * dx + dz * dz;
        }
    }

    public enum Corner
    {
        NONE,
        CORNER_1,
        CORNER_2;
    }

    public enum IntBoxCoordType
    {
        MIN_X,
        MIN_Y,
        MIN_Z,
        MAX_X,
        MAX_Y,
        MAX_Z
    }

    public interface ICoordinateAccessor
    {
        int getValue(Vec3i vec);

        BlockPos setValue(BlockPos vec, int newValue);
    }

    public interface IIntBoundingBoxAccessor
    {
        int getValue(IntBoundingBox box);

        IntBoundingBox setValue(IntBoundingBox box, int newValue);
    }
}
