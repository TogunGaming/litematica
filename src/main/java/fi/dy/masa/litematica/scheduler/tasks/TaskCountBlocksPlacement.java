package fi.dy.masa.litematica.scheduler.tasks;

import java.util.Collection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.IMaterialList;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.SelectionBox;
import fi.dy.masa.litematica.util.BlockInfoListType;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;

public class TaskCountBlocksPlacement extends TaskCountBlocksMaterialList
{
    protected final SchematicPlacement schematicPlacement;
    protected final WorldSchematic worldSchematic;
    protected final boolean ignoreState;

    public TaskCountBlocksPlacement(SchematicPlacement schematicPlacement, IMaterialList materialList)
    {
        super(materialList, "litematica.gui.label.task_name.material_list");

        this.ignoreState = Configs.Generic.MATERIAL_LIST_IGNORE_BLOCK_STATE.getBooleanValue();
        this.worldSchematic = SchematicWorldHandler.getSchematicWorld();
        this.schematicPlacement = schematicPlacement;

        Collection<SelectionBox> boxes = schematicPlacement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED).values();

        // Filter/clamp the boxes to intersect with the render layer
        if (materialList.getMaterialListType() == BlockInfoListType.RENDER_LAYERS)
        {
            this.addPerChunkBoxes(boxes, DataManager.getRenderLayerRange());
        }
        else
        {
            this.addPerChunkBoxes(boxes);
        }

        this.updateInfoHudLinesMissingChunks(this.requiredChunks);
    }

    @Override
    public boolean canExecute()
    {
        return super.canExecute() && this.worldSchematic != null;
    }

    @Override
    protected void countAtPosition(BlockPos pos)
    {
        IBlockState stateSchematic = this.worldSchematic.getBlockState(pos).getActualState(this.worldSchematic, pos);

        if (stateSchematic.getBlock() != Blocks.AIR)
        {
            IBlockState stateClient = this.worldClient.getBlockState(pos).getActualState(this.worldClient, pos);

            this.countsTotal.addTo(stateSchematic, 1);

            if (stateClient.getBlock() == Blocks.AIR)
            {
                this.countsMissing.addTo(stateSchematic, 1);
            }
            else if (this.ignoreState ? stateClient.getBlock() != stateSchematic.getBlock() : stateClient != stateSchematic)
            {
                this.countsMissing.addTo(stateSchematic, 1);
                this.countsMismatch.addTo(stateSchematic, 1);
            }
        }
    }
}
