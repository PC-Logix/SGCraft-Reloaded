package lc.common.base;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class LCItemBlock extends ItemBlock {
	
	public LCItemBlock(Block block) {
		this((LCBlock) block);
	}

	public LCItemBlock(LCBlock block) {
		super(block);
		setHasSubtypes(block.typed);
	}

	@Override
	public int getMetadata(int data) {
		return (this.hasSubtypes) ? data : 0;
	}

	@Override
	public String getUnlocalizedName(ItemStack is) {
		return field_150939_a.getUnlocalizedName();
	}

}
