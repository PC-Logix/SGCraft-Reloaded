package lc.common.base;

import lc.api.defs.ILanteaCraftRenderer;
import lc.client.DefaultBlockRenderer;
import lc.common.impl.registry.DefinitionRegistry;
import lc.common.impl.registry.DefinitionRegistry.RendererType;
import lc.core.LCRuntime;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Block rendering hook.
 * 
 * @author AfterLifeLochie
 * 
 */
public class LCBlockRenderHook implements ISimpleBlockRenderingHandler {

	private final int renderIdx;
	private final DefinitionRegistry registry;
	private final DefaultBlockRenderer defaultBlockRenderer;

	/**
	 * Create a new rendering hook.
	 * @param renderIdx The renderer ID of this hook
	 */
	public LCBlockRenderHook(int renderIdx) {
		this.renderIdx = renderIdx;
		this.registry = (DefinitionRegistry) LCRuntime.runtime.registries().definitions();
		this.defaultBlockRenderer = new DefaultBlockRenderer();
	}

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
		boolean flag = true;
		ILanteaCraftRenderer worker = registry.getRendererFor(RendererType.BLOCK, block.getClass());
		if (worker == null || !(worker instanceof LCBlockRenderer)) {
			flag = false;
		} else {
			LCBlockRenderer blockRenderer = (LCBlockRenderer) worker;
			while (blockRenderer != null && !blockRenderer.renderInventoryBlock(block, renderer, metadata)) {
				worker = registry.getRenderer(RendererType.BLOCK, blockRenderer.getParent());
				if (worker == null || !(worker instanceof LCBlockRenderer)) {
					flag = false;
					break;
				}
				blockRenderer = (LCBlockRenderer) worker;
			}
		}
		if (!flag)
			defaultBlockRenderer.renderInventoryBlock(block, renderer, metadata);
	}

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
			RenderBlocks renderer) {
		boolean flag = true;
		ILanteaCraftRenderer worker = registry.getRendererFor(RendererType.BLOCK, block.getClass());
		if (worker == null || !(worker instanceof LCBlockRenderer)) {
			flag = false;
		} else {
			LCBlockRenderer blockRenderer = (LCBlockRenderer) worker;
			while (blockRenderer != null && !blockRenderer.renderWorldBlock(block, renderer, world, x, y, z)) {
				worker = registry.getRenderer(RendererType.BLOCK, blockRenderer.getParent());
				if (worker == null || !(worker instanceof LCBlockRenderer)) {
					flag = false;
					break;
				}
				blockRenderer = (LCBlockRenderer) worker;
			}
		}

		if (!flag)
			flag = defaultBlockRenderer.renderWorldBlock(block, renderer, world, x, y, z);
		return flag;
	}

	@Override
	public boolean shouldRender3DInInventory(int modelId) {
		return true;
	}

	@Override
	public int getRenderId() {
		return renderIdx;
	}

}