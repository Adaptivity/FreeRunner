package balkondeuralpha.freerunner;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class BlockHayStack extends Block {
	public BlockHayStack(int i) {
		super(i, FRCommonProxy.materialHay);
		this.setCreativeTab(CreativeTabs.tabDecorations);
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int i, int j, int k) {
		return null;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public void onEntityCollidedWithBlock(World world, int i, int j, int k, Entity entity) {
		double d = 0.001D;
		entity.fallDistance = 0F;
		if (entity.motionY <= -0.4D) {
			entity.addVelocity(0D, 0.4D, 0D);
		}
		entity.motionX *= d;
		entity.motionZ *= d;
	}
}
