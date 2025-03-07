package fi.dy.masa.litematica.selection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;
import fi.dy.masa.malilib.overlay.message.MessageOutput;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import fi.dy.masa.malilib.util.position.Coordinate;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.render.infohud.StatusInfoRenderer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.PositionUtils.Corner;

public class AreaSelection
{
    protected final Map<String, SelectionBox> subRegionBoxes = new HashMap<>();
    protected String name = "Unnamed";
    protected boolean originSelected;
    protected BlockPos calculatedOrigin = BlockPos.ORIGIN;
    protected boolean calculatedOriginDirty = true;
    @Nullable protected BlockPos explicitOrigin = null;
    @Nullable protected String currentBox;

    public static AreaSelection fromPlacement(SchematicPlacement placement)
    {
        ImmutableMap<String, SelectionBox> boxes = placement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED);
        BlockPos origin = placement.getOrigin();

        AreaSelection selection = new AreaSelection();
        selection.setExplicitOrigin(origin);
        selection.name = placement.getName();
        selection.subRegionBoxes.putAll(boxes);

        return selection;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    protected void markDirty()
    {
        this.calculatedOriginDirty = true;

        if (Configs.Visuals.AREA_SELECTION_RENDERING.getBooleanValue() == false)
        {
            StatusInfoRenderer.getInstance().startOverrideDelay();
        }
    }

    @Nullable
    public String getCurrentSubRegionBoxName()
    {
        return this.currentBox;
    }

    public boolean setSelectedSubRegionBox(@Nullable String name)
    {
        if (name == null || this.subRegionBoxes.containsKey(name))
        {
            this.currentBox = name;
            return true;
        }

        return false;
    }

    public boolean isOriginSelected()
    {
        return this.originSelected;
    }

    public void setOriginSelected(boolean selected)
    {
        this.originSelected = selected;
    }

    public boolean hasManualOrigin()
    {
        return this.explicitOrigin != null;
    }

    /**
     * @return Returns the effective origin point. This is the explicit origin point if one has been set,
     *         otherwise it's an automatically calculated origin point, located at the minimum corner of all the boxes.
     */
    public BlockPos getEffectiveOrigin()
    {
        if (this.explicitOrigin != null)
        {
            return this.explicitOrigin;
        }
        else
        {
            if (this.calculatedOriginDirty)
            {
                this.updateCalculatedOrigin();
            }

            return this.calculatedOrigin;
        }
    }

    /**
     * @return the explicitly defined origin point, if any.
     */
    @Nullable
    public BlockPos getExplicitOrigin()
    {
        return this.explicitOrigin;
    }

    public void setExplicitOrigin(@Nullable BlockPos origin)
    {
        this.explicitOrigin = origin;

        if (origin == null)
        {
            this.originSelected = false;
        }
    }

    protected void updateCalculatedOrigin()
    {
        Pair<BlockPos, BlockPos> pair = PositionUtils.getEnclosingAreaCorners(this.subRegionBoxes.values());

        if (pair != null)
        {
            this.calculatedOrigin = pair.getLeft();
        }
        else
        {
            this.calculatedOrigin = BlockPos.ORIGIN;
        }

        this.calculatedOriginDirty = false;
    }

    @Nullable
    public SelectionBox getSubRegionBox(String name)
    {
        return this.subRegionBoxes.get(name);
    }

    @Nullable
    public SelectionBox getSelectedSubRegionBox()
    {
        return this.currentBox != null ? this.subRegionBoxes.get(this.currentBox) : null;
    }

    public List<String> getAllSubRegionNames()
    {
        List<String> list = new ArrayList<>(this.subRegionBoxes.keySet());
        list.sort(Comparator.naturalOrder());
        return list;
    }

    public List<SelectionBox> getAllSubRegionBoxes()
    {
        return ImmutableList.copyOf(this.subRegionBoxes.values());
    }

    public ImmutableMap<String, SelectionBox> getAllSubRegions()
    {
        ImmutableMap.Builder<String, SelectionBox> builder = ImmutableMap.builder();
        builder.putAll(this.subRegionBoxes);
        return builder.build();
    }

    @Nullable
    public String createNewSubRegionBox(BlockPos pos1, final String nameIn)
    {
        this.clearCurrentSelectedCorner();
        this.setOriginSelected(false);

        String name = nameIn;
        int i = 1;

        while (this.subRegionBoxes.containsKey(name))
        {
            name = nameIn + " " + i;
            i++;
        }

        SelectionBox box = new SelectionBox();
        box.setName(name);
        box.setSelectedCorner(Corner.CORNER_1);
        this.currentBox = name;
        this.subRegionBoxes.put(name, box);
        this.setSubRegionCornerPos(box, Corner.CORNER_1, pos1);
        this.setSubRegionCornerPos(box, Corner.CORNER_2, pos1);

        return name;
    }

    public void clearCurrentSelectedCorner()
    {
        this.setCurrentSelectedCorner(Corner.NONE);
    }

    public void setCurrentSelectedCorner(Corner corner)
    {
        SelectionBox box = this.getSelectedSubRegionBox();

        if (box != null)
        {
            box.setSelectedCorner(corner);
        }
    }

    /**
     * Adds the given SelectionBox, if either replace is true, or there isn't yet a box by the same name.
     * @param box
     * @param replace
     * @return true if the box was successfully added, false if replace was false and there was already a box with the same name
     */
    public boolean addSubRegionBox(SelectionBox box, boolean replace)
    {
        if (replace || this.subRegionBoxes.containsKey(box.getName()) == false)
        {
            this.subRegionBoxes.put(box.getName(), box);
            this.markDirty();
            return true;
        }

        return false;
    }

    public void removeAllSubRegionBoxes()
    {
        this.subRegionBoxes.clear();
        this.markDirty();
    }

    public boolean removeSubRegionBox(String name)
    {
        boolean success = this.subRegionBoxes.remove(name) != null;
        this.markDirty();

        if (success && name.equals(this.currentBox))
        {
            this.currentBox = null;
        }

        return success;
    }

    public boolean removeSelectedSubRegionBox()
    {
        boolean success = this.currentBox != null && this.subRegionBoxes.remove(this.currentBox) != null;
        this.currentBox = null;
        this.markDirty();
        return success;
    }

    public boolean renameSubRegionBox(String oldName, String newName)
    {
        return this.renameSubRegionBox(oldName, newName, MessageOutput.NONE);
    }

    public boolean renameSubRegionBox(String oldName, String newName, MessageOutput output)
    {
        SelectionBox box = this.subRegionBoxes.get(oldName);

        if (box != null)
        {
            if (this.subRegionBoxes.containsKey(newName))
            {
                MessageDispatcher.error().type(output)
                        .translate("litematica.error.area_editor.rename_sub_region.exists", newName);

                return false;
            }

            this.subRegionBoxes.remove(oldName);
            box.setName(newName);
            this.subRegionBoxes.put(newName, box);

            if (this.currentBox != null && this.currentBox.equals(oldName))
            {
                this.currentBox = newName;
            }

            return true;
        }

        return false;
    }

    public void moveEntireSelectionTo(BlockPos newOrigin, boolean printMessage)
    {
        BlockPos old = this.getEffectiveOrigin();
        BlockPos diff = newOrigin.subtract(old);

        for (SelectionBox box : this.subRegionBoxes.values())
        {
            if (box.getPos1() != null)
            {
                this.setSubRegionCornerPos(box, Corner.CORNER_1, box.getPos1().add(diff));
            }

            if (box.getPos2() != null)
            {
                this.setSubRegionCornerPos(box, Corner.CORNER_2, box.getPos2().add(diff));
            }
        }

        if (this.getExplicitOrigin() != null)
        {
            this.setExplicitOrigin(newOrigin);
        }

        if (printMessage)
        {
            String oldStr = String.format("x: %d, y: %d, z: %d", old.getX(), old.getY(), old.getZ());
            String newStr = String.format("x: %d, y: %d, z: %d", newOrigin.getX(), newOrigin.getY(), newOrigin.getZ());
            // TODO malilib refactor - this used to be showGuiOrActionBarMessage
            MessageDispatcher.success().customHotbar().translate("litematica.message.moved_selection", oldStr, newStr);
        }
    }

    public void moveSelectedElement(EnumFacing direction, int amount)
    {
        SelectionBox box = this.getSelectedSubRegionBox();

        if (this.isOriginSelected())
        {
            if (this.getExplicitOrigin() != null)
            {
                this.setExplicitOrigin(this.getExplicitOrigin().offset(direction, amount));
            }
        }
        else if (box != null)
        {
            Corner corner = box.getSelectedCorner();

            if ((corner == Corner.NONE || corner == Corner.CORNER_1) && box.getPos1() != null)
            {
                BlockPos pos = this.getSubRegionCornerPos(box, Corner.CORNER_1).offset(direction, amount);
                this.setSubRegionCornerPos(box, Corner.CORNER_1, pos);
            }

            if ((corner == Corner.NONE || corner == Corner.CORNER_2) && box.getPos2() != null)
            {
                BlockPos pos = this.getSubRegionCornerPos(box, Corner.CORNER_2).offset(direction, amount);
                this.setSubRegionCornerPos(box, Corner.CORNER_2, pos);
            }
        }
        else
        {
            BlockPos newOrigin = this.getEffectiveOrigin().offset(direction, amount);
            this.moveEntireSelectionTo(newOrigin, false);
        }
    }

    public void setSelectedSubRegionCornerPos(BlockPos pos, Corner corner)
    {
        SelectionBox box = this.getSelectedSubRegionBox();

        if (box != null)
        {
            this.setSubRegionCornerPos(box, corner, pos);
        }
    }

    public void setSubRegionCornerPos(Box box, Corner corner, BlockPos pos)
    {
        if (corner == Corner.CORNER_1)
        {
            box.setPos1(pos);
            this.markDirty();
        }
        else if (corner == Corner.CORNER_2)
        {
            box.setPos2(pos);
            this.markDirty();
        }
    }

    public void setCoordinate(@Nullable Box box, Corner corner, Coordinate coordinate, int value)
    {
        if (box != null && corner != null && corner != Corner.NONE)
        {
            box.setCoordinate(value, corner, coordinate);
            this.markDirty();
        }
        else if (this.explicitOrigin != null)
        {
            this.setExplicitOrigin(coordinate.modifyBlockPos(value, this.explicitOrigin));
        }
    }

    public BlockPos getSubRegionCornerPos(Box box, Corner corner)
    {
        return corner == Corner.CORNER_2 ? box.getPos2() : box.getPos1();
    }

    public AreaSelection copy()
    {
        return fromJson(this.toJson());
    }

    public static AreaSelection fromJson(JsonObject obj)
    {
        AreaSelection area = new AreaSelection();

        if (JsonUtils.hasArray(obj, "boxes"))
        {
            JsonArray arr = obj.get("boxes").getAsJsonArray();
            final int size = arr.size();

            for (int i = 0; i < size; i++)
            {
                JsonElement el = arr.get(i);

                if (el.isJsonObject())
                {
                    SelectionBox box = SelectionBox.fromJson(el.getAsJsonObject());

                    if (box != null)
                    {
                        area.subRegionBoxes.put(box.getName(), box);
                    }
                }
            }
        }

        if (JsonUtils.hasString(obj, "name"))
        {
            area.name = obj.get("name").getAsString();
        }

        if (JsonUtils.hasString(obj, "current"))
        {
            area.currentBox = obj.get("current").getAsString();
        }

        BlockPos pos = JsonUtils.blockPosFromJson(obj, "origin");

        if (pos != null)
        {
            area.setExplicitOrigin(pos);
        }
        else
        {
            area.updateCalculatedOrigin();
        }

        return area;
    }

    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();

        for (SelectionBox box : this.subRegionBoxes.values())
        {
            JsonObject o = box.toJson();

            if (o != null)
            {
                arr.add(o);
            }
        }

        obj.add("name", new JsonPrimitive(this.name));

        if (arr.size() > 0)
        {
            if (this.currentBox != null)
            {
                obj.add("current", new JsonPrimitive(this.currentBox));
            }

            obj.add("boxes", arr);
        }

        if (this.getExplicitOrigin() != null)
        {
            obj.add("origin", JsonUtils.blockPosToJson(this.getExplicitOrigin()));
        }

        return obj;
    }
}
