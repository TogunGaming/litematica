package fi.dy.masa.litematica.render.schematic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.render.schematic.RenderChunkSchematicVbo.OverlayRenderType;

public class ChunkRenderWorkerLitematica implements Runnable
{
    private static final Logger LOGGER = Litematica.logger;

    private final ChunkRenderDispatcherLitematica chunkRenderDispatcher;
    @Nullable private final BufferBuilderCache bufferCache;
    private boolean shouldRun;

    public ChunkRenderWorkerLitematica(ChunkRenderDispatcherLitematica chunkRenderDispatcherIn)
    {
        this(chunkRenderDispatcherIn, null);
    }

    public ChunkRenderWorkerLitematica(ChunkRenderDispatcherLitematica chunkRenderDispatcherIn, @Nullable BufferBuilderCache bufferCache)
    {
        this.shouldRun = true;
        this.chunkRenderDispatcher = chunkRenderDispatcherIn;
        this.bufferCache = bufferCache;
    }

    @Override
    public void run()
    {
        while (this.shouldRun)
        {
            try
            {
                this.processTask(this.chunkRenderDispatcher.getNextChunkUpdate());
            }
            catch (InterruptedException e)
            {
                LOGGER.debug("Stopping chunk worker due to interrupt");
                return;
            }
            catch (Throwable throwable)
            {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Batching chunks");
                Minecraft.getMinecraft().crashed(Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(crashreport));
                return;
            }
        }
    }

    protected void processTask(final ChunkCompileTaskGeneratorSchematic generator) throws InterruptedException
    {
        generator.getLock().lock();

        try
        {
            if (generator.getStatus() != ChunkCompileTaskGeneratorSchematic.Status.PENDING)
            {
                if (generator.isFinished() == false)
                {
                    LOGGER.warn("Chunk render task was {} when I expected it to be pending; ignoring task", (Object)generator.getStatus());
                }

                return;
            }

            generator.setStatus(ChunkCompileTaskGeneratorSchematic.Status.COMPILING);
        }
        finally
        {
            generator.getLock().unlock();
        }

        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();

        if (entity == null)
        {
            generator.finish();
        }
        else
        {
            generator.setRegionRenderCacheBuilder(this.getRegionRenderCacheBuilder());

            ChunkCompileTaskGeneratorSchematic.Type generatorType = generator.getType();
            float x = (float) entity.posX;
            float y = (float) entity.posY + entity.getEyeHeight();
            float z = (float) entity.posZ;

            if (generatorType == ChunkCompileTaskGeneratorSchematic.Type.REBUILD_CHUNK)
            {
                generator.getRenderChunk().rebuildChunk(x, y, z, generator);
            }
            else if (generatorType == ChunkCompileTaskGeneratorSchematic.Type.RESORT_TRANSPARENCY)
            {
                generator.getRenderChunk().resortTransparency(x, y, z, generator);
            }

            generator.getLock().lock();

            try
            {
                if (generator.getStatus() != ChunkCompileTaskGeneratorSchematic.Status.COMPILING)
                {
                    if (generator.isFinished() == false)
                    {
                        LOGGER.warn("Chunk render task was {} when I expected it to be compiling; aborting task", (Object)generator.getStatus());
                    }

                    this.freeRenderBuilder(generator);
                    return;
                }

                generator.setStatus(ChunkCompileTaskGeneratorSchematic.Status.UPLOADING);
            }
            finally
            {
                generator.getLock().unlock();
            }

            final CompiledChunkSchematic compiledChunk = (CompiledChunkSchematic) generator.getCompiledChunk();
            ArrayList<ListenableFuture<Object>> futuresList = Lists.newArrayList();
            BufferBuilderCache buffers = generator.getBufferCache();
            RenderChunkSchematicVbo renderChunk = (RenderChunkSchematicVbo) generator.getRenderChunk();

            if (generatorType == ChunkCompileTaskGeneratorSchematic.Type.REBUILD_CHUNK)
            {
                //if (GuiBase.isCtrlDown()) System.out.printf("pre uploadChunk()\n");
                for (BlockRenderLayer layer : BlockRenderLayer.values())
                {
                    if (compiledChunk.isLayerEmpty(layer) == false)
                    {
                        //if (GuiBase.isCtrlDown()) System.out.printf("REBUILD_CHUNK pre uploadChunkBlocks()\n");
                        BufferBuilder buffer = buffers.getWorldRendererByLayer(layer);
                        futuresList.add(this.chunkRenderDispatcher.uploadChunkBlocks(layer, buffer, renderChunk, compiledChunk, generator.getDistanceSq()));
                    }
                }

                for (OverlayRenderType type : OverlayRenderType.values())
                {
                    if (compiledChunk.isOverlayTypeEmpty(type) == false)
                    {
                        //if (GuiBase.isCtrlDown()) System.out.printf("REBUILD_CHUNK pre uploadChunkOverlay()\n");
                        BufferBuilder buffer = buffers.getOverlayBuffer(type);
                        futuresList.add(this.chunkRenderDispatcher.uploadChunkOverlay(type, buffer, renderChunk, compiledChunk, generator.getDistanceSq()));
                    }
                }
            }
            else if (generatorType == ChunkCompileTaskGeneratorSchematic.Type.RESORT_TRANSPARENCY)
            {
                BufferBuilder buffer = buffers.getWorldRendererByLayer(BlockRenderLayer.TRANSLUCENT);
                futuresList.add(this.chunkRenderDispatcher.uploadChunkBlocks(BlockRenderLayer.TRANSLUCENT, buffer, renderChunk, compiledChunk, generator.getDistanceSq()));

                if (compiledChunk.isOverlayTypeEmpty(OverlayRenderType.QUAD) == false)
                {
                    //if (GuiBase.isCtrlDown()) System.out.printf("RESORT_TRANSPARENCY pre uploadChunkOverlay()\n");
                    buffer = buffers.getOverlayBuffer(OverlayRenderType.QUAD);
                    futuresList.add(this.chunkRenderDispatcher.uploadChunkOverlay(OverlayRenderType.QUAD, buffer, renderChunk, compiledChunk, generator.getDistanceSq()));
                }
            }

            final ListenableFuture<List<Object>> listenablefuture = Futures.allAsList(futuresList);

            generator.addFinishRunnable(new Runnable()
            {
                @Override
                public void run()
                {
                    listenablefuture.cancel(false);
                }
            });

            Futures.addCallback(listenablefuture, new FutureCallback<List<Object>>()
            {
                @Override
                public void onSuccess(@Nullable List<Object> list)
                {
                    ChunkRenderWorkerLitematica.this.freeRenderBuilder(generator);

                    generator.getLock().lock();

                    label49:
                    {
                        try
                        {
                            if (generator.getStatus() == ChunkCompileTaskGeneratorSchematic.Status.UPLOADING)
                            {
                                generator.setStatus(ChunkCompileTaskGeneratorSchematic.Status.DONE);
                                break label49;
                            }

                            if (generator.isFinished() == false)
                            {
                                ChunkRenderWorkerLitematica.LOGGER.warn("Chunk render task was {} when I expected it to be uploading; aborting task", (Object)generator.getStatus());
                            }
                        }
                        finally
                        {
                            generator.getLock().unlock();
                        }

                        return;
                    }

                    generator.getRenderChunk().setChunkRenderData(compiledChunk);
                }

                @Override
                public void onFailure(Throwable throwable)
                {
                    ChunkRenderWorkerLitematica.this.freeRenderBuilder(generator);

                    if ((throwable instanceof CancellationException) == false && (throwable instanceof InterruptedException) == false)
                    {
                        Minecraft.getMinecraft().crashed(CrashReport.makeCrashReport(throwable, "Rendering Litematica chunk"));
                    }
                }
            });
        }
    }

    private BufferBuilderCache getRegionRenderCacheBuilder() throws InterruptedException
    {
        return this.bufferCache != null ? this.bufferCache : this.chunkRenderDispatcher.allocateRenderBuilder();
    }

    private void freeRenderBuilder(ChunkCompileTaskGeneratorSchematic generator)
    {
        if (this.bufferCache == null)
        {
            this.chunkRenderDispatcher.freeRenderBuilder(generator.getBufferCache());
        }
    }

    public void notifyToStop()
    {
        this.shouldRun = false;
    }
}
