package fi.dy.masa.litematica.render.schematic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import org.lwjgl.opengl.GL11;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ReportedException;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.GameUtils;
import fi.dy.masa.malilib.util.position.LayerRange;
import fi.dy.masa.malilib.util.position.SubChunkPos;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.mixin.IMixinBlockRendererDispatcher;
import fi.dy.masa.litematica.mixin.IMixinViewFrustum;
import fi.dy.masa.litematica.render.schematic.RenderChunkSchematicVbo.OverlayRenderType;
import fi.dy.masa.litematica.util.IGenericEventListener;

public class RenderGlobalSchematic extends RenderGlobal implements IGenericEventListener
{
    private final Minecraft mc;
    private final RenderManager renderManager;
    private final BlockModelShapes blockModelShapes;
    private final BlockModelRendererSchematic blockModelRenderer;
    private final BlockFluidRenderer fluidRenderer;
    private final Set<TileEntity> setTileEntities = new HashSet<>();
    private final List<RenderChunkSchematicVbo> renderInfos = new ArrayList<>(1024);
    private final List<SubChunkPos> subChunksWithinRenderRange = new ArrayList<>();
    private Set<RenderChunkSchematicVbo> chunksToUpdate = new LinkedHashSet<>();
    private WorldClient world;
    private ViewFrustum viewFrustum;
    private double frustumUpdatePosX = Double.MIN_VALUE;
    private double frustumUpdatePosY = Double.MIN_VALUE;
    private double frustumUpdatePosZ = Double.MIN_VALUE;
    private int frustumUpdatePosChunkX = Integer.MIN_VALUE;
    private int frustumUpdatePosChunkY = Integer.MIN_VALUE;
    private int frustumUpdatePosChunkZ = Integer.MIN_VALUE;
    private double lastViewEntityX = Double.MIN_VALUE;
    private double lastViewEntityY = Double.MIN_VALUE;
    private double lastViewEntityZ = Double.MIN_VALUE;
    private float lastViewEntityPitch = Float.MIN_VALUE;
    private float lastViewEntityYaw = Float.MIN_VALUE;
    private ChunkRenderDispatcherLitematica renderDispatcher;
    private ChunkRenderContainerSchematic renderContainer;
    private IRenderChunkFactory renderChunkFactory;
    private final BlockPos.MutableBlockPos viewPosSubChunk = new BlockPos.MutableBlockPos();
    private BlockPos lastSubChunkUpdatePos;
    //private ShaderGroup entityOutlineShader;
    //private boolean entityOutlinesRendered;

    private int renderDistanceChunks = -1;
    private int renderEntitiesStartupCounter = 2;
    private int countEntitiesTotal;
    private int countEntitiesRendered;
    private int countEntitiesHidden;

    private boolean vboEnabled;
    private double prevRenderSortX;
    private double prevRenderSortY;
    private double prevRenderSortZ;
    private boolean displayListEntitiesDirty = true;

    public RenderGlobalSchematic(Minecraft mc)
    {
        super(mc);

        this.mc = mc;
        this.renderManager = mc.getRenderManager();

        this.vboEnabled = OpenGlHelper.useVbo();

        if (this.vboEnabled)
        {
            this.renderContainer = new VboRenderListSchematic();
            this.renderChunkFactory = new RenderChunkFactoryVbo();
        }
        else
        {
            this.renderContainer = new RenderListSchematic();
            this.renderChunkFactory = new RenderChunkFactoryList();
        }

        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        this.blockModelShapes = dispatcher.getBlockModelShapes();
        this.blockModelRenderer = new BlockModelRendererSchematic(mc.getBlockColors());
        this.fluidRenderer = ((IMixinBlockRendererDispatcher) dispatcher).getFluidRenderer();

        DataManager.getSchematicPlacementManager().addRebuildListener(this);
    }

    public void markNeedsUpdate()
    {
        this.displayListEntitiesDirty = true;
    }

    public void onEvent()
    {
        this.lastSubChunkUpdatePos = null;
    }

    @Override
    public String getDebugInfoRenders()
    {
        int rcTotal = this.viewFrustum != null ? this.viewFrustum.renderChunks.length : 0;
        int rcRendered = this.viewFrustum != null ? this.getRenderedChunks() : 0;
        return String.format("C: %d/%d %sD: %d, L: %d, %s", rcRendered, rcTotal, this.mc.renderChunksMany ? "(s) " : "", this.renderDistanceChunks, 0, this.renderDispatcher == null ? "null" : this.renderDispatcher.getDebugInfo());
    }

    @Override
    public String getDebugInfoEntities()
    {
        return "E: " + this.countEntitiesRendered + "/" + this.countEntitiesTotal + ", B: " + this.countEntitiesHidden;
    }

    @Override
    protected int getRenderedChunks()
    {
        int count = 0;

        for (RenderChunk renderChunk : this.renderInfos)
        {
            CompiledChunk compiledchunk = renderChunk.compiledChunk;

            if (compiledchunk != CompiledChunk.DUMMY && compiledchunk.isEmpty() == false)
            {
                ++count;
            }
        }

        return count;
    }

    @Override
    public void setWorldAndLoadRenderers(@Nullable WorldClient worldClientIn)
    {
        if (this.world != null)
        {
            this.world.removeEventListener(this);
        }

        this.frustumUpdatePosX = Double.MIN_VALUE;
        this.frustumUpdatePosY = Double.MIN_VALUE;
        this.frustumUpdatePosZ = Double.MIN_VALUE;
        this.frustumUpdatePosChunkX = Integer.MIN_VALUE;
        this.frustumUpdatePosChunkY = Integer.MIN_VALUE;
        this.frustumUpdatePosChunkZ = Integer.MIN_VALUE;
        //this.renderManager.setWorld(worldClientIn);
        this.world = worldClientIn;

        if (worldClientIn != null)
        {
            worldClientIn.addEventListener(this);
            this.loadRenderers();
        }
        else
        {
            this.chunksToUpdate.clear();
            this.renderInfos.clear();

            if (this.viewFrustum != null)
            {
                this.viewFrustum.deleteGlResources();
                this.viewFrustum = null;
            }

            if (this.renderDispatcher != null)
            {
                this.renderDispatcher.stopWorkerThreads();
            }

            this.renderDispatcher = null;
        }
    }

    @Override
    public void loadRenderers()
    {
        World world = this.world;

        if (world != null)
        {
            if (this.renderDispatcher == null)
            {
                this.renderDispatcher = new ChunkRenderDispatcherLitematica();
            }

            this.displayListEntitiesDirty = true;
            this.renderDistanceChunks = GameUtils.getRenderDistanceChunks();

            boolean vboEnabledPrevious = this.vboEnabled;
            this.vboEnabled = OpenGlHelper.useVbo();

            if (this.vboEnabled == false && vboEnabledPrevious)
            {
                this.renderContainer = new RenderListSchematic();
                this.renderChunkFactory = new RenderChunkFactoryList();
            }
            else if (this.vboEnabled && vboEnabledPrevious == false)
            {
                this.renderContainer = new VboRenderListSchematic();
                this.renderChunkFactory = new RenderChunkFactoryVbo();
            }

            if (this.viewFrustum != null)
            {
                this.viewFrustum.deleteGlResources();
            }

            this.stopChunkUpdates();

            synchronized (this.setTileEntities)
            {
                this.setTileEntities.clear();
            }

            this.viewFrustum = new ViewFrustum(world, GameUtils.getRenderDistanceChunks(), this, this.renderChunkFactory);

            Entity entity = this.mc.getRenderViewEntity();

            if (entity != null)
            {
                this.viewFrustum.updateChunkPositions(entity.posX, entity.posZ);
            }

            this.renderEntitiesStartupCounter = 2;
        }
    }

    @Override
    protected void stopChunkUpdates()
    {
        this.chunksToUpdate.clear();
        this.renderDispatcher.stopChunkUpdates();
    }

    @Override
    public void setupTerrain(Entity viewEntity, double partialTicks, ICamera camera, int frameCount, boolean playerSpectator)
    {
        World world = this.world;
        world.profiler.startSection("setup_terrain");

        if (this.viewFrustum == null || GameUtils.getRenderDistanceChunks() != this.renderDistanceChunks)
        {
            this.loadRenderers();
        }

        world.profiler.startSection("camera");

        double diffX = viewEntity.posX - this.frustumUpdatePosX;
        double diffY = viewEntity.posY - this.frustumUpdatePosY;
        double diffZ = viewEntity.posZ - this.frustumUpdatePosZ;

        if (this.frustumUpdatePosChunkX != viewEntity.chunkCoordX ||
            this.frustumUpdatePosChunkY != viewEntity.chunkCoordY ||
            this.frustumUpdatePosChunkZ != viewEntity.chunkCoordZ ||
            diffX * diffX + diffY * diffY + diffZ * diffZ > 16.0D)
        {
            this.frustumUpdatePosX = viewEntity.posX;
            this.frustumUpdatePosY = viewEntity.posY;
            this.frustumUpdatePosZ = viewEntity.posZ;
            this.frustumUpdatePosChunkX = viewEntity.chunkCoordX;
            this.frustumUpdatePosChunkY = viewEntity.chunkCoordY;
            this.frustumUpdatePosChunkZ = viewEntity.chunkCoordZ;
            this.viewFrustum.updateChunkPositions(viewEntity.posX, viewEntity.posZ);
        }

        world.profiler.endStartSection("renderlist_camera");
        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;
        this.renderContainer.initialize(x, y, z);
        y = y + (double) viewEntity.getEyeHeight();

        world.profiler.endStartSection("culling");
        final int centerChunkX = ((int) MathHelper.floor(x)) >> 4;
        final int centerChunkY = ((int) MathHelper.floor(y)) >> 4;
        final int centerChunkZ = ((int) MathHelper.floor(z)) >> 4;
        final int renderDistance = GameUtils.getRenderDistanceChunks();
        SubChunkPos viewSubChunk = new SubChunkPos(centerChunkX, centerChunkY, centerChunkZ);
        this.viewPosSubChunk.setPos(centerChunkX << 4, centerChunkY << 4, centerChunkZ << 4);

        this.displayListEntitiesDirty = this.displayListEntitiesDirty || this.chunksToUpdate.isEmpty() == false ||
                viewEntity.posX != this.lastViewEntityX ||
                viewEntity.posY != this.lastViewEntityY ||
                viewEntity.posZ != this.lastViewEntityZ ||
                viewEntity.rotationPitch != this.lastViewEntityPitch ||
                viewEntity.rotationYaw != this.lastViewEntityYaw;
        this.lastViewEntityX = viewEntity.posX;
        this.lastViewEntityY = viewEntity.posY;
        this.lastViewEntityZ = viewEntity.posZ;
        this.lastViewEntityPitch = viewEntity.rotationPitch;
        this.lastViewEntityYaw = viewEntity.rotationYaw;

        world.profiler.endStartSection("update");

        if (this.displayListEntitiesDirty)
        {
            world.profiler.startSection("fetch");

            this.displayListEntitiesDirty = false;
            this.renderInfos.clear();

            Entity.setRenderDistanceWeight(MathHelper.clamp((double) renderDistance / 8.0D, 1.0D, 2.5D));

            if (this.lastSubChunkUpdatePos == null ||
                Math.abs(this.viewPosSubChunk.getX() - this.lastSubChunkUpdatePos.getX()) > 32 ||
                Math.abs(this.viewPosSubChunk.getZ() - this.lastSubChunkUpdatePos.getZ()) > 32)
            {
                Set<SubChunkPos> set = DataManager.getSchematicPlacementManager().getAllTouchedSubChunks();
                int maxChunkDist = renderDistance + 2;

                this.subChunksWithinRenderRange.clear();

                for (SubChunkPos p : set)
                {
                    if (Math.abs(p.getX() - centerChunkX) <= maxChunkDist &&
                        Math.abs(p.getZ() - centerChunkZ) <= maxChunkDist)
                    {
                        this.subChunksWithinRenderRange.add(p);
                    }
                }

                Collections.sort(this.subChunksWithinRenderRange, new SubChunkPos.DistanceComparator(viewSubChunk));
                this.lastSubChunkUpdatePos = this.viewPosSubChunk.toImmutable();
            }

            //if (GuiBase.isCtrlDown()) System.out.printf("sorted positions: %d\n", positions.size());

            world.profiler.endStartSection("iteration");

            //while (queuePositions.isEmpty() == false)
            for (int i = 0; i < this.subChunksWithinRenderRange.size(); ++i)
            {
                //SubChunkPos subChunk = queuePositions.poll();
                SubChunkPos subChunk = this.subChunksWithinRenderRange.get(i);

                // Only render sub-chunks that are within the client's render distance, and that
                // have been already properly loaded on the client
                if (Math.abs(subChunk.getX() - centerChunkX) <= renderDistance &&
                    Math.abs(subChunk.getZ() - centerChunkZ) <= renderDistance &&
                    world.getChunkProvider().isChunkGeneratedAt(subChunk.getX(), subChunk.getZ()))
                {
                    BlockPos subChunkCornerPos = new BlockPos(subChunk.getX() << 4, subChunk.getY() << 4, subChunk.getZ() << 4);
                    RenderChunkSchematicVbo renderChunk = (RenderChunkSchematicVbo) ((IMixinViewFrustum) this.viewFrustum).invokeGetRenderChunk(subChunkCornerPos);

                    if (renderChunk != null)
                    {
                        if (renderChunk.setFrameIndex(frameCount) && camera.isBoundingBoxInFrustum(renderChunk.boundingBox))
                        {
                            //if (GuiBase.isCtrlDown()) System.out.printf("add @ %s\n", subChunk);
                            if (renderChunk.needsUpdate() && subChunkCornerPos.equals(this.viewPosSubChunk))
                            {
                                renderChunk.setNeedsUpdate(true);
                            }

                            this.renderInfos.add(renderChunk);
                        }
                    }
                }
            }

            world.profiler.endSection();
        }

        world.profiler.endStartSection("rebuild_near");
        Set<RenderChunkSchematicVbo> set = this.chunksToUpdate;
        this.chunksToUpdate = new LinkedHashSet<>();

        for (RenderChunkSchematicVbo renderChunkTmp : this.renderInfos)
        {
            if (renderChunkTmp.needsUpdate() || set.contains(renderChunkTmp))
            {
                this.displayListEntitiesDirty = true;
                BlockPos pos = renderChunkTmp.getPosition().add(8, 8, 8);
                boolean isNear = pos.distanceSq(x, y, z) < 1024.0D;

                if (renderChunkTmp.needsImmediateUpdate() == false && isNear == false)
                {
                    this.chunksToUpdate.add(renderChunkTmp);
                }
                else
                {
                    //if (GuiBase.isCtrlDown()) System.out.printf("====== update now\n");
                    world.profiler.startSection("build_near");

                    this.renderDispatcher.updateChunkNow(renderChunkTmp);
                    renderChunkTmp.clearNeedsUpdate();

                    world.profiler.endSection();
                }
            }
        }

        this.chunksToUpdate.addAll(set);

        world.profiler.endSection();
        world.profiler.endSection();
    }

    @Override
    public void updateChunks(long finishTimeNano)
    {
        this.displayListEntitiesDirty |= this.renderDispatcher.runChunkUploads(finishTimeNano);

        if (this.chunksToUpdate.isEmpty() == false)
        {
            Iterator<RenderChunkSchematicVbo> iterator = this.chunksToUpdate.iterator();

            while (iterator.hasNext())
            {
                RenderChunkSchematicVbo renderChunk = iterator.next();
                boolean flag;

                if (renderChunk.needsImmediateUpdate())
                {
                    flag = this.renderDispatcher.updateChunkNow(renderChunk);
                }
                else
                {
                    flag = this.renderDispatcher.updateChunkLater(renderChunk);
                }

                if (!flag)
                {
                    break;
                }

                renderChunk.clearNeedsUpdate();
                iterator.remove();
                long i = finishTimeNano - System.nanoTime();

                if (i < 0L)
                {
                    break;
                }
            }
        }
    }

    public int renderBlockLayer(BlockRenderLayer blockLayerIn, double partialTicks, Entity entityIn)
    {
        this.world.profiler.startSection("render_block_layer_" + blockLayerIn);

        RenderUtils.disableItemLighting();

        if (blockLayerIn == BlockRenderLayer.TRANSLUCENT)
        {
            this.world.profiler.startSection("translucent_sort");
            double diffX = entityIn.posX - this.prevRenderSortX;
            double diffY = entityIn.posY - this.prevRenderSortY;
            double diffZ = entityIn.posZ - this.prevRenderSortZ;

            if (diffX * diffX + diffY * diffY + diffZ * diffZ > 1.0D)
            {
                this.prevRenderSortX = entityIn.posX;
                this.prevRenderSortY = entityIn.posY;
                this.prevRenderSortZ = entityIn.posZ;
                int i = 0;

                for (RenderChunkSchematicVbo renderChunk : this.renderInfos)
                {
                    if ((renderChunk.getChunkRenderData().isLayerStarted(blockLayerIn) ||
                        (renderChunk.getChunkRenderData() != CompiledChunk.DUMMY && renderChunk.hasOverlay())) && i++ < 15)
                    {
                        this.renderDispatcher.updateTransparencyLater(renderChunk);
                    }
                }
            }

            this.world.profiler.endSection();
        }

        this.world.profiler.startSection("filter_empty");
        boolean reverse = blockLayerIn == BlockRenderLayer.TRANSLUCENT;
        int startIndex = reverse ? this.renderInfos.size() - 1 : 0;
        int stopIndex = reverse ? -1 : this.renderInfos.size();
        int increment = reverse ? -1 : 1;
        int count = 0;

        for (int i = startIndex; i != stopIndex; i += increment)
        {
            RenderChunkSchematicVbo renderchunk = this.renderInfos.get(i);

            if (renderchunk.getChunkRenderData().isLayerEmpty(blockLayerIn) == false)
            {
                ++count;
                this.renderContainer.addRenderChunk(renderchunk, blockLayerIn);
            }
        }

        this.world.profiler.endStartSection("render");

        this.renderBlockLayer(blockLayerIn);

        this.world.profiler.endSection();
        this.world.profiler.endSection();

        return count;
    }

    private void renderBlockLayer(BlockRenderLayer layer)
    {
        this.mc.entityRenderer.enableLightmap();

        if (OpenGlHelper.useVbo())
        {
            GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        }

        this.renderContainer.renderChunkLayer(layer);

        if (OpenGlHelper.useVbo())
        {
            for (VertexFormatElement vertexformatelement : DefaultVertexFormats.BLOCK.getElements())
            {
                VertexFormatElement.EnumUsage vertexformatelement$enumusage = vertexformatelement.getUsage();
                int index = vertexformatelement.getIndex();

                switch (vertexformatelement$enumusage)
                {
                    case POSITION:
                        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                        break;
                    case UV:
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + index);
                        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                        break;
                    case COLOR:
                        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
                        GlStateManager.resetColor();
                    default:
                }
            }
        }

        this.mc.entityRenderer.disableLightmap();
    }

    public void renderBlockOverlays()
    {
        this.renderBlockOverlay(OverlayRenderType.OUTLINE);
        this.renderBlockOverlay(OverlayRenderType.QUAD);
    }

    private void renderBlockOverlay(OverlayRenderType type)
    {
        this.world.profiler.startSection("overlay_" + type.name());
        this.world.profiler.startSection("filter_empty");

        for (int i = this.renderInfos.size() - 1; i >= 0; --i)
        {
            RenderChunkSchematicVbo renderChunk = this.renderInfos.get(i);

            if (renderChunk.getChunkRenderData() != CompiledChunk.DUMMY && renderChunk.hasOverlay())
            {
                CompiledChunkSchematic compiledChunk = renderChunk.getChunkRenderData();

                if (compiledChunk.isOverlayTypeEmpty(type) == false)
                {
                    this.renderContainer.addOverlayChunk(renderChunk);
                }
            }
        }

        this.world.profiler.endStartSection("render");

        this.renderBlockOverlayBuffers(type);

        this.world.profiler.endSection();
        this.world.profiler.endSection();
    }

    private void renderBlockOverlayBuffers(OverlayRenderType type)
    {
        this.mc.entityRenderer.enableLightmap();

        if (OpenGlHelper.useVbo())
        {
            GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        }

        this.renderContainer.renderBlockOverlays(type);

        if (OpenGlHelper.useVbo())
        {
            for (VertexFormatElement element : DefaultVertexFormats.POSITION_COLOR.getElements())
            {
                VertexFormatElement.EnumUsage usage = element.getUsage();

                switch (usage)
                {
                    case POSITION:
                        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                        break;
                    case UV:
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + element.getIndex());
                        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                        break;
                    case COLOR:
                        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
                        GlStateManager.resetColor();
                    default:
                }
            }
        }

        this.mc.entityRenderer.disableLightmap();
    }

    public boolean renderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilderIn)
    {
        try
        {
            EnumBlockRenderType renderType = state.getRenderType();

            if (renderType == EnumBlockRenderType.INVISIBLE)
            {
                return false;
            }
            else
            {
                switch (renderType)
                {
                    case MODEL:
                        return this.blockModelRenderer.renderModel(blockAccess, this.getModelForState(state), state, pos, bufferBuilderIn);
                    case ENTITYBLOCK_ANIMATED:
                        return false;
                    case LIQUID:
                        return this.fluidRenderer.renderFluid(blockAccess, state, pos, bufferBuilderIn);
                    default:
                        return false;
                }
            }
        }
        catch (Throwable throwable)
        {
            CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Tesselating block in world");
            CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being tesselated");
            CrashReportCategory.addBlockInfo(crashreportcategory, pos, state.getBlock(), state.getBlock().getMetaFromState(state));
            throw new ReportedException(crashreport);
        }
    }

    public IBakedModel getModelForState(IBlockState state)
    {
        return this.blockModelShapes.getModelForState(state);
    }

    @Override
    public void renderEntities(Entity renderViewEntity, ICamera camera, float partialTicks)
    {
        if (this.renderEntitiesStartupCounter > 0)
        {
            --this.renderEntitiesStartupCounter;
        }
        else
        {
            double renderX = renderViewEntity.prevPosX + (renderViewEntity.posX - renderViewEntity.prevPosX) * (double)partialTicks;
            double renderY = renderViewEntity.prevPosY + (renderViewEntity.posY - renderViewEntity.prevPosY) * (double)partialTicks;
            double renderZ = renderViewEntity.prevPosZ + (renderViewEntity.posZ - renderViewEntity.prevPosZ) * (double)partialTicks;
            this.world.profiler.startSection("prepare");
            TileEntityRendererDispatcher.instance.prepare(this.world, this.mc.getTextureManager(), this.mc.fontRenderer, this.mc.getRenderViewEntity(), this.mc.objectMouseOver, partialTicks);
            this.renderManager.cacheActiveRenderInfo(this.world, this.mc.fontRenderer, this.mc.getRenderViewEntity(), this.mc.pointedEntity, this.mc.gameSettings, partialTicks);
            this.countEntitiesTotal = 0;
            this.countEntitiesRendered = 0;
            this.countEntitiesHidden = 0;

            Entity entity = this.mc.getRenderViewEntity();
            double entityX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double)partialTicks;
            double entityY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double)partialTicks;
            double entityZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double)partialTicks;
            TileEntityRendererDispatcher.staticPlayerX = entityX;
            TileEntityRendererDispatcher.staticPlayerY = entityY;
            TileEntityRendererDispatcher.staticPlayerZ = entityZ;
            this.renderManager.setRenderPosition(entityX, entityY, entityZ);
            this.mc.entityRenderer.enableLightmap();

            this.countEntitiesTotal = this.world.getLoadedEntityList().size();

            this.world.profiler.endStartSection("regular_entities");
            List<Entity> entitiesOutlined = Lists.<Entity>newArrayList();
            List<Entity> entitiesMultipass = Lists.<Entity>newArrayList();
            BlockPos.PooledMutableBlockPos posMutable = BlockPos.PooledMutableBlockPos.retain();
            LayerRange layerRange = DataManager.getRenderLayerRange();

            for (RenderChunk renderChunk : this.renderInfos)
            {
                Chunk chunk = this.world.getChunk(renderChunk.getPosition());
                ClassInheritanceMultiMap<Entity> classinheritancemultimap = chunk.getEntityLists()[renderChunk.getPosition().getY() / 16];

                if (classinheritancemultimap.isEmpty() == false)
                {
                    for (Entity entityTmp : classinheritancemultimap)
                    {
                        if (layerRange.isPositionWithinRange((int) entityTmp.posX, (int) entityTmp.posY, (int) entityTmp.posZ) == false)
                        {
                            continue;
                        }

                        boolean shouldRender = this.renderManager.shouldRender(entityTmp, camera, renderX, renderY, renderZ) || entityTmp.isRidingOrBeingRiddenBy(this.mc.player);

                        if (shouldRender)
                        {
                            boolean sleeping = this.mc.getRenderViewEntity() instanceof EntityLivingBase ? ((EntityLivingBase)this.mc.getRenderViewEntity()).isPlayerSleeping() : false;

                            if ((entityTmp != this.mc.getRenderViewEntity() || this.mc.gameSettings.thirdPersonView != 0 || sleeping) &&
                                (entityTmp.posY < 0.0D || entityTmp.posY >= 256.0D || this.world.isBlockLoaded(posMutable.setPos(entityTmp))))
                            {
                                ++this.countEntitiesRendered;
                                this.renderManager.renderEntityStatic(entityTmp, 0f, false);

                                if (this.isOutlineActive(entityTmp, entity, camera))
                                {
                                    entitiesOutlined.add(entityTmp);
                                }

                                if (this.renderManager.isRenderMultipass(entityTmp))
                                {
                                    entitiesMultipass.add(entityTmp);
                                }
                            }
                        }
                    }
                }
            }

            posMutable.release();

            if (entitiesMultipass.isEmpty() == false)
            {
                for (Entity entityTmp : entitiesMultipass)
                {
                    this.renderManager.renderMultipass(entityTmp, partialTicks);
                }
            }

            /*
            if (this.isRenderEntityOutlines() && (entitiesOutlined.isEmpty() == false || this.entityOutlinesRendered))
            {
                this.world.profiler.endStartSection("entityOutlines");
                this.entityOutlineFramebuffer.framebufferClear();
                this.entityOutlinesRendered = entitiesOutlined.isEmpty() == false;

                if (!entitiesOutlined.isEmpty())
                {
                    GlStateManager.depthFunc(519);
                    GlStateManager.disableFog();
                    this.entityOutlineFramebuffer.bindFramebuffer(false);
                    RenderUtils.disableItemLighting();
                    this.renderManager.setRenderOutlines(true);

                    for (int i = 0; i < entitiesOutlined.size(); ++i)
                    {
                        this.renderManager.renderEntityStatic(entitiesOutlined.get(i), partialTicks, false);
                    }

                    this.renderManager.setRenderOutlines(false);
                    RenderUtils.enableItemLighting();
                    GlStateManager.depthMask(false);

                    this.entityOutlineShader.render(partialTicks);

                    GlStateManager.enableLighting();
                    GlStateManager.depthMask(true);
                    GlStateManager.enableFog();
                    GlStateManager.enableBlend();
                    GlStateManager.enableColorMaterial();
                    GlStateManager.depthFunc(515);
                    GlStateManager.enableDepth();
                    GlStateManager.enableAlpha();
                }

                this.mc.getFramebuffer().bindFramebuffer(false);
            }
            */

            this.world.profiler.endStartSection("block_entities");
            fi.dy.masa.malilib.render.RenderUtils.enableItemLighting();

            for (RenderChunkSchematicVbo renderChunk : this.renderInfos)
            {
                List<TileEntity> tiles = renderChunk.getChunkRenderData().getTileEntities();

                if (tiles.isEmpty() == false)
                {
                    for (TileEntity te : tiles)
                    {
                        TileEntityRendererDispatcher.instance.render(te, partialTicks, -1);
                    }
                }
            }

            synchronized (this.setTileEntities)
            {
                for (TileEntity te : this.setTileEntities)
                {
                    TileEntityRendererDispatcher.instance.render(te, partialTicks, -1);
                }
            }

            this.mc.entityRenderer.disableLightmap();
            this.world.profiler.endSection();
        }
    }

    private boolean isOutlineActive(Entity entityIn, Entity viewer, ICamera camera)
    {
        boolean sleeping = viewer instanceof EntityLivingBase && ((EntityLivingBase)viewer).isPlayerSleeping();

        if (entityIn == viewer && this.mc.gameSettings.thirdPersonView == 0 && sleeping == false)
        {
            return false;
        }
        else if (entityIn.isGlowing())
        {
            return true;
        }
        else if (this.mc.player.isSpectator() && this.mc.gameSettings.keyBindSpectatorOutlines.isKeyDown() && entityIn instanceof EntityPlayer)
        {
            return entityIn.ignoreFrustumCheck || camera.isBoundingBoxInFrustum(entityIn.getEntityBoundingBox()) || entityIn.isRidingOrBeingRiddenBy(this.mc.player);
        }
        else
        {
            return false;
        }
    }

    @Override
    public void notifyBlockUpdate(World worldIn, BlockPos pos, IBlockState oldState, IBlockState newState, int flags)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        this.markBlocksForUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, (flags & 8) != 0);
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2)
    {
        this.markBlocksForUpdate(x1 - 1, y1 - 1, z1 - 1, x2 + 1, y2 + 1, z2 + 1, false);
    }

    private void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean updateImmediately)
    {
        if (this.viewFrustum != null)
        {
            this.viewFrustum.markBlocksForUpdate(minX, minY, minZ, maxX, maxY, maxZ, updateImmediately);
        }
    }

    @Override public void notifyLightSet(BlockPos pos) {}
    @Override public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent soundIn, SoundCategory category, double x, double y, double z, float volume, float pitch) {}
    @Override public void playRecord(SoundEvent soundIn, BlockPos pos) {}
    @Override public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {}
    @Override public void spawnParticle(int id, boolean ignoreRange, boolean p_190570_3_, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {}
    @Override public void onEntityAdded(Entity entityIn) {}
    @Override public void onEntityRemoved(Entity entityIn) {}
    @Override public void broadcastSound(int soundID, BlockPos pos, int data) {}
    @Override public void playEvent(EntityPlayer player, int type, BlockPos blockPosIn, int data) {}
    @Override public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {}
}
