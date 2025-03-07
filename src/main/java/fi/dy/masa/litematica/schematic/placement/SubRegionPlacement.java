package fi.dy.masa.litematica.schematic.placement;

import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import fi.dy.masa.malilib.util.position.Coordinate;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.util.PositionUtils;

public class SubRegionPlacement
{
    private final String name;
    private final BlockPos defaultPos;
    private BlockPos pos;
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private boolean enabled = true;
    private boolean ignoreEntities;
    private int coordinateLockMask;

    public SubRegionPlacement(BlockPos pos, String name)
    {
        this(pos, pos, name);
    }

    public SubRegionPlacement(BlockPos pos, BlockPos defaultPos, String name)
    {
        this.pos = pos;
        this.defaultPos = defaultPos;
        this.name = name;
    }

    public SubRegionPlacement copy()
    {
        SubRegionPlacement copy = new SubRegionPlacement(this.pos, this.defaultPos, this.name);

        copy.pos = this.pos;
        copy.rotation = this.rotation;
        copy.mirror = this.mirror;
        copy.enabled = this.enabled;
        copy.ignoreEntities = this.ignoreEntities;
        copy.coordinateLockMask = this.coordinateLockMask;

        return copy;
    }

    public boolean isEnabled()
    {
        return this.enabled;
    }

    public boolean ignoreEntities()
    {
        return this.ignoreEntities;
    }

    public void setCoordinateLocked(Coordinate coordinate, boolean locked)
    {
        int mask = 0x1 << coordinate.ordinal();

        if (locked)
        {
            this.coordinateLockMask |= mask;
        }
        else
        {
            this.coordinateLockMask &= ~mask;
        }
    }

    public boolean isCoordinateLocked(Coordinate coordinate)
    {
        int mask = 0x1 << coordinate.ordinal();
        return (this.coordinateLockMask & mask) != 0;
    }

    public boolean matchesRequirement(RequiredEnabled required)
    {
        return required == RequiredEnabled.ANY || this.isEnabled();
    }

    public String getName()
    {
        return this.name;
    }

    public BlockPos getPos()
    {
        return this.pos;
    }

    public Rotation getRotation()
    {
        return this.rotation;
    }

    public Mirror getMirror()
    {
        return this.mirror;
    }

    void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    void toggleEnabled()
    {
        this.setEnabled(! this.isEnabled());
    }

    void toggleIgnoreEntities()
    {
        this.ignoreEntities = ! this.ignoreEntities;
    }

    void setPos(BlockPos pos)
    {
        BlockPos newPos = PositionUtils.getModifiedPartiallyLockedPosition(this.pos, pos, this.coordinateLockMask);

        if (newPos.equals(this.pos) == false)
        {
            this.pos = newPos;
        }
        else if (pos.equals(this.pos) == false && this.coordinateLockMask != 0)
        {
            MessageDispatcher.error(2000).translate("litematica.error.schematic_placements.coordinate_locked");
        }
    }

    void setRotation(Rotation rotation)
    {
        this.rotation = rotation;
    }

    void setMirror(Mirror mirror)
    {
        this.mirror = mirror;
    }

    void resetToOriginalValues()
    {
        this.pos = this.defaultPos;
        this.rotation = Rotation.NONE;
        this.mirror = Mirror.NONE;
        this.enabled = true;
        this.ignoreEntities = false;
    }

    public boolean isRegionPlacementModifiedFromDefault()
    {
        return this.isRegionPlacementModified(this.defaultPos);
    }

    public boolean isRegionPlacementModified(BlockPos originalPosition)
    {
        return this.isEnabled() == false ||
               this.ignoreEntities() ||
               this.getMirror() != Mirror.NONE ||
               this.getRotation() != Rotation.NONE ||
               this.getPos().equals(originalPosition) == false;
    }

    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();

        obj.add("pos", JsonUtils.blockPosToJson(this.pos));
        obj.add("default_pos", JsonUtils.blockPosToJson(this.defaultPos));
        obj.add("name", new JsonPrimitive(this.getName()));
        obj.add("rotation", new JsonPrimitive(this.rotation.name()));
        obj.add("mirror", new JsonPrimitive(this.mirror.name()));
        obj.add("locked_coords", new JsonPrimitive(this.coordinateLockMask));
        obj.add("enabled", new JsonPrimitive(this.enabled));
        obj.add("ignore_entities", new JsonPrimitive(this.ignoreEntities));

        return obj;
    }

    @Nullable
    public static SubRegionPlacement fromJson(JsonObject obj)
    {
        if (JsonUtils.hasArray(obj, "pos") &&
            JsonUtils.hasString(obj, "name") &&
            JsonUtils.hasString(obj, "rotation") &&
            JsonUtils.hasString(obj, "mirror"))
        {
            BlockPos pos = JsonUtils.blockPosFromJson(obj, "pos");

            if (pos == null)
            {
                Litematica.logger.warn("Placement.fromJson(): Failed to load a placement from JSON, invalid position data");
                return null;
            }

            BlockPos defaultPos = JsonUtils.blockPosFromJson(obj, "default_pos");

            if (defaultPos == null)
            {
                defaultPos = pos;
            }

            SubRegionPlacement placement = new SubRegionPlacement(pos, defaultPos, obj.get("name").getAsString());
            placement.setEnabled(JsonUtils.getBoolean(obj, "enabled"));
            placement.ignoreEntities = JsonUtils.getBoolean(obj, "ignore_entities");
            placement.coordinateLockMask = JsonUtils.getInteger(obj, "locked_coords");

            try
            {
                Rotation rotation = Rotation.valueOf(obj.get("rotation").getAsString());
                Mirror mirror = Mirror.valueOf(obj.get("mirror").getAsString());

                placement.setRotation(rotation);
                placement.setMirror(mirror);
            }
            catch (Exception e)
            {
                Litematica.logger.warn("Placement.fromJson(): Invalid rotation or mirror value for a placement");
            }

            return placement;
        }

        return null;
    }

    public enum RequiredEnabled
    {
        ANY,
        PLACEMENT_ENABLED;
    }
}
