package fi.dy.masa.litematica.util;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import com.google.common.base.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import fi.dy.masa.malilib.util.data.Constants;
import fi.dy.masa.malilib.util.inventory.InventoryUtils;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.RenderUtils;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;

public class EntityUtils
{
    public static final Predicate<Entity> NOT_PLAYER = new Predicate<Entity>()
    {
        @Override
        public boolean apply(@Nullable Entity entity)
        {
            return (entity instanceof EntityPlayer) == false;
        }
    };

    public static boolean hasToolItem(EntityLivingBase entity)
    {
        return hasToolItemInHand(entity, EnumHand.MAIN_HAND) ||
               hasToolItemInHand(entity, EnumHand.OFF_HAND);
    }

    public static boolean hasToolItemInHand(EntityLivingBase entity, EnumHand hand)
    {
        // If the configured tool item has NBT data, then the NBT is compared, otherwise it's ignored

        ItemStack toolItem = DataManager.getToolItem();

        if (toolItem.isEmpty())
        {
            return entity.getHeldItemMainhand().isEmpty();
        }

        ItemStack stackHand = entity.getHeldItem(hand);

        if (ItemStack.areItemsEqualIgnoreDurability(toolItem, stackHand))
        {
            return toolItem.hasTagCompound() == false || ItemStack.areItemStackTagsEqual(toolItem, stackHand);
        }

        return false;
    }

    /**
     * Checks if the requested item is currently in the player's hand such that it would be used for using/placing.
     * This means, that it must either be in the main hand, or the main hand must be empty and the item is in the offhand.
     * @param player
     * @param stack
     * @param lenient if true, then NBT tags and also damage of damageable items are ignored
     * @return
     */
    @Nullable
    public static EnumHand getUsedHandForItem(EntityPlayer player, ItemStack stack, boolean lenient)
    {
        EnumHand hand = null;

        if (lenient)
        {
            if (ItemStack.areItemsEqualIgnoreDurability(player.getHeldItemMainhand(), stack))
            {
                hand = EnumHand.MAIN_HAND;
            }
            else if (player.getHeldItemMainhand().isEmpty() && ItemStack.areItemsEqualIgnoreDurability(player.getHeldItemOffhand(), stack))
            {
                hand = EnumHand.OFF_HAND;
            }
        }
        else
        {
            if (InventoryUtils.areStacksEqual(player.getHeldItemMainhand(), stack))
            {
                hand = EnumHand.MAIN_HAND;
            }
            else if (player.getHeldItemMainhand().isEmpty() && InventoryUtils.areStacksEqual(player.getHeldItemOffhand(), stack))
            {
                hand = EnumHand.OFF_HAND;
            }
        }

        return hand;
    }

    public static boolean areStacksEqualIgnoreDurability(ItemStack stack1, ItemStack stack2)
    {
        return ItemStack.areItemsEqualIgnoreDurability(stack1, stack2) && ItemStack.areItemStackTagsEqual(stack1, stack2);
    }

    public static EnumFacing getHorizontalLookingDirection(Entity entity)
    {
        return EnumFacing.fromAngle(entity.rotationYaw);
    }

    public static EnumFacing getVerticalLookingDirection(Entity entity)
    {
        return entity.rotationPitch > 0 ? EnumFacing.DOWN : EnumFacing.UP;
    }

    public static EnumFacing getClosestLookingDirection(Entity entity)
    {
        if (entity.rotationPitch > 60.0f)
        {
            return EnumFacing.DOWN;
        }
        else if (-entity.rotationPitch > 60.0f)
        {
            return EnumFacing.UP;
        }

        return getHorizontalLookingDirection(entity);
    }

    public static boolean setFakedSneakingState(Minecraft mc, boolean sneaking)
    {
        if (mc.player != null && mc.player.isSneaking() != sneaking)
        {
            CPacketEntityAction.Action action = sneaking ? CPacketEntityAction.Action.START_SNEAKING : CPacketEntityAction.Action.STOP_SNEAKING;
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, action));
            mc.player.movementInput.sneak = sneaking;
            return true;
        }

        return false;
    }

    @Nullable
    public static <T extends Entity> T findEntityByUUID(List<T> list, UUID uuid)
    {
        if (uuid == null)
        {
            return null;
        }

        for (T entity : list)
        {
            if (entity.getUniqueID().equals(uuid))
            {
                return entity;
            }
        }

        return null;
    }

    @Nullable
    private static Entity createEntityFromNBTSingle(NBTTagCompound nbt, World world)
    {
        try
        {
            Entity entity = EntityList.createEntityFromNBT(nbt, world);

            if (entity != null)
            {
                entity.setUniqueId(UUID.randomUUID());
            }

            return entity;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Note: This does NOT spawn any of the entities in the world!
     * @param nbt
     * @param world
     * @return
     */
    @Nullable
    public static Entity createEntityAndPassengersFromNBT(NBTTagCompound nbt, World world)
    {
        Entity entity = createEntityFromNBTSingle(nbt, world);

        if (entity == null)
        {
            return null;
        }
        else
        {
            if (nbt.hasKey("Passengers", Constants.NBT.TAG_LIST))
            {
                NBTTagList taglist = nbt.getTagList("Passengers", Constants.NBT.TAG_COMPOUND);

                for (int i = 0; i < taglist.tagCount(); ++i)
                {
                    Entity passenger = createEntityAndPassengersFromNBT(taglist.getCompoundTagAt(i), world);

                    if (passenger != null)
                    {
                        passenger.startRiding(entity, true);
                    }
                }
            }

            return entity;
        }
    }

    public static void spawnEntityAndPassengersInWorld(Entity entity, World world)
    {
        if (world.spawnEntity(entity) && entity.isBeingRidden())
        {
            for (Entity passenger : entity.getPassengers())
            {
                passenger.setPosition(entity.posX, entity.posY + entity.getMountedYOffset() + passenger.getYOffset(), entity.posZ);
                spawnEntityAndPassengersInWorld(passenger, world);
            }
        }
    }

    public static List<Entity> getEntitiesWithinSubRegion(World world, BlockPos origin, BlockPos regionPos, BlockPos regionSize,
            SchematicPlacement schematicPlacement, SubRegionPlacement placement)
    {
        // These are the untransformed relative positions
        BlockPos regionPosRelTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        BlockPos posEndAbs = PositionUtils.getTransformedPlacementPosition(regionSize.add(-1, -1, -1), schematicPlacement, placement).add(regionPosRelTransformed).add(origin);
        BlockPos regionPosAbs = regionPosRelTransformed.add(origin);
        AxisAlignedBB bb = PositionUtils.createEnclosingAABB(regionPosAbs, posEndAbs);

        return world.getEntitiesInAABBexcluding(null, bb, null);
    }

    public static boolean shouldPickBlock(EntityPlayer player)
    {
        return Configs.Generic.PICK_BLOCK_ENABLED.getBooleanValue() &&
                RenderUtils.areSchematicBlocksCurrentlyRendered() &&
                (Configs.Generic.TOOL_ITEM_ENABLED.getBooleanValue() == false || hasToolItem(player) == false);
    }
}
