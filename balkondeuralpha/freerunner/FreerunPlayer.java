package balkondeuralpha.freerunner;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import balkondeuralpha.freerunner.moves.Move;
import balkondeuralpha.freerunner.moves.MoveAroundEdge;
import balkondeuralpha.freerunner.moves.MoveWallrun;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class FreerunPlayer {
	public Move move;
	public Situation situation;
	public double startPosY, startPosX, startPosZ;
	private float startRollingYaw, startRollingPitch;
	public double horizontalSpeed;
	public boolean isClimbing, wallRunning, freeRunning;
	public float rollAnimation, prevRollAnimation;
	public EntityPlayer player;
	public static List<Integer> climbableBlocks, climbableInside;
	public static final int LOOK_WEST = 0, LOOK_NORTH = 1, LOOK_EAST = 2, LOOK_SOUTH = 3;

	public FreerunPlayer(EntityPlayer player) {
		this.player = player;
		climbableBlocks = new ArrayList<Integer>();
		climbableInside = new ArrayList<Integer>();
		setMove(null);
		Move.addAllMoves(this);
		freeRunning = false;
		horizontalSpeed = 0D;
		rollAnimation = 1F;
		situation = null;
		addAllClimableBlocks();
	}

	@SideOnly(Side.CLIENT)
	public void animateRoll(float f) {
		if (Minecraft.getMinecraft().gameSettings.thirdPersonView == 0) {
			float yaw = (startRollingYaw + 360F * f);
			float pitch = (startRollingPitch + 360F * f);
			//pitch = startRollingPitch;
			while (yaw >= 360F) {
				yaw -= 360F;
			}
			if (pitch >= 180F) {
				pitch -= 270F;
			}
			player.setRotation(yaw, pitch);
		}
	}

	public int canHopOver() {
		MovingObjectPosition movingobjectposition = getMovingObjectPositionFromPlayer(true);
		if (movingobjectposition != null && movingobjectposition.typeOfHit == EnumMovingObjectType.TILE) {
			if (isSelectedBlockClose(movingobjectposition, 2.0F) && isSelectedBlockOnLevel(movingobjectposition, 0)) {
				int b = getSelectedBlockId(movingobjectposition);
				Material m = getSelectedBlockMaterial(movingobjectposition);
				AxisAlignedBB boundingbox = Block.blocksList[b].getCollisionBoundingBoxFromPool(player.worldObj, movingobjectposition.blockX, movingobjectposition.blockY, movingobjectposition.blockZ);
				if (m.isSolid() && isBlockAboveAir(2, false, isClimbing)) {
					if (boundingbox != null && boundingbox.maxY - movingobjectposition.blockY > 1.0F) {
						return 2;
					} else if (Block.blocksList[b].getBlockBoundsMaxY() > player.stepHeight && Block.blocksList[b].getBlockBoundsMaxY() <= 1.0F && b != Block.stairsWoodOak.blockID
							&& b != Block.stairsCobblestone.blockID) {
						return 1;
					}
				}
			}
		}
		if (movingobjectposition == null || movingobjectposition.typeOfHit != EnumMovingObjectType.ENTITY || movingobjectposition.entityHit == null) {
			return 0;
		}
		if (isSelectedEntityClose(movingobjectposition.entityHit, 2.0F) && isSelectedEntityOnLevel(movingobjectposition.entityHit, 0)) {
			if (movingobjectposition.entityHit.boundingBox.maxY - movingobjectposition.entityHit.boundingBox.minY <= 1.5D) {
				return 3;
			}
		}
		return 0;
	}

	public int canJumpOverGap() {
		double d = -MathHelper.sin((player.rotationYaw / 180F) * 3.141593F) * MathHelper.cos((0 / 180F) * 3.141593F);
		double d1 = MathHelper.cos((player.rotationYaw / 180F) * 3.141593F) * MathHelper.cos((0 / 180F) * 3.141593F);
		int i = MathHelper.floor_double(player.posX);
		int i1 = MathHelper.floor_double(player.posX + d);
		int i2 = MathHelper.floor_double(player.posX + 2 * d);
		int j = MathHelper.floor_double(player.boundingBox.minY - 0.1F);
		int k = MathHelper.floor_double(player.posZ);
		int k1 = MathHelper.floor_double(player.posZ + d1);
		int k2 = MathHelper.floor_double(player.posZ + 2 * d1);
		/*
		 * if (player.onGround && !player.worldObj.getBlockMaterial(i, j,
		 * k).isSolid() && player.worldObj.getBlockMaterial(i1, j,
		 * k1).isSolid()) { return 1; } else
		 */if (player.onGround && !player.worldObj.getBlockMaterial(i, j, k).isSolid() && !player.worldObj.getBlockMaterial(i1, j, k1).isSolid()
				&& player.worldObj.getBlockMaterial(i2, j, k2).isSolid()) {
			return 2;
		}
		return 0;
	}

	public EntityLiving canLandOnMob(List<Entity> list) {
		for (Entity ent : list) {
			if (ent instanceof EntityLiving && isSelectedEntityClose(ent, 3.0F, 0D, player.motionY, 0D))
				return (EntityLiving) ent;
		}
		return null;
	}

	public boolean canWallrun() {
		MovingObjectPosition movingobjectposition = getMovingObjectPositionFromPlayer(true);
		if (movingobjectposition != null && movingobjectposition.typeOfHit == EnumMovingObjectType.TILE) {
			if (isSelectedBlockClose(movingobjectposition, 2.0F)) {
				Material m = getSelectedBlockMaterial(movingobjectposition);
				Material m1 = getSelectedBlockMaterial(movingobjectposition, 0, 1, 0);
				if (isSelectedBlockOnLevel(movingobjectposition, 1)) {
					m = getSelectedBlockMaterial(movingobjectposition);
					m1 = getSelectedBlockMaterial(movingobjectposition, 0, -1, 0);
				}
				if (!m1.isSolid()) {
					if (situation.canPushUp() != 0) {
						return m.isSolid() && !player.isJumping;
					}
				}
				return m.isSolid() && m1.isSolid() && !player.isJumping;
			}
		}
		return false;
	}

	public int getLookDirection() {
		return (MathHelper.floor_double(((player.rotationYaw * 4F) / 360F) + 0.5D) & 3);
	}

	public int getSelectedBlockId(MovingObjectPosition movingobjectposition) {
		return getSelectedBlockId(movingobjectposition, 0, 0, 0);
	}

	public int getSelectedBlockId(MovingObjectPosition movingobjectposition, int addX, int addY, int addZ) {
		return player.worldObj.getBlockId(movingobjectposition.blockX + addX, movingobjectposition.blockY + addY, movingobjectposition.blockZ + addZ);
	}

	public Material getSelectedBlockMaterial(MovingObjectPosition movingobjectposition) {
		return getSelectedBlockMaterial(movingobjectposition, 0, 0, 0);
	}

	public Material getSelectedBlockMaterial(MovingObjectPosition movingobjectposition, int addX, int addY, int addZ) {
		return player.worldObj.getBlockMaterial(movingobjectposition.blockX + addX, movingobjectposition.blockY + addY, movingobjectposition.blockZ + addZ);
	}

	public void handleThings() {
		handleFreerunning();
		handleMoves();
		handleTimers();
	}

	public boolean hasBlockInFront() {
		double d = -MathHelper.sin((player.rotationYaw / 180F) * 3.141593F) * MathHelper.cos((0 / 180F) * 3.141593F);
		double d1 = MathHelper.cos((player.rotationYaw / 180F) * 3.141593F) * MathHelper.cos((0 / 180F) * 3.141593F);
		int i = MathHelper.floor_double(player.posX + d);
		int j = MathHelper.floor_double(player.boundingBox.minY);
		int k = MathHelper.floor_double(player.posZ + d1);
		return player.worldObj.getBlockMaterial(i, j, k).isSolid();
	}

	public boolean isBlockAboveAir(int l, boolean blockAboveBlockIsSolid, boolean climbing) {
		MovingObjectPosition movingobjectposition = getMovingObjectPositionFromPlayer(true);
		if (movingobjectposition == null || movingobjectposition.typeOfHit != EnumMovingObjectType.TILE) {
			return false;
		}
		int i = movingobjectposition.blockX;
		int j = movingobjectposition.blockY + 1;
		int k = movingobjectposition.blockZ;
		Material m = player.worldObj.getBlockMaterial(i, j, k);
		Material m1 = player.worldObj.getBlockMaterial(i, j + 1, k);
		Material m2 = player.worldObj.getBlockMaterial(i, j + 2, k);
		if (l == 1) {
			if (blockAboveBlockIsSolid) {
				return !m.isSolid() && m1.isSolid();
			} else {
				return !m.isSolid();
			}
		} else if (l == 2) {
			if (blockAboveBlockIsSolid) {
				return !m.isSolid() && !m1.isSolid() && m2.isSolid();
			} else {
				return !m.isSolid() && !m1.isSolid();
			}
		} else if (l == 3) {
			return !m.isSolid() && !m1.isSolid() && !m2.isSolid();
		} else {
			return !m.isSolid();
		}
	}

	public boolean isHangingStill() {
		return isClimbing && move == null;
	}

	public boolean isMoving() {
		return move != null;
	}

	public boolean isMovingBackwards() {
		return player.moveForward < 0;
	}

	public boolean isMovingForwards() {
		return player.moveForward > 0;
	}

	public boolean isMovingLeft() {
		return player.moveStrafing > 0;
	}

	public boolean isMovingRight() {
		return player.moveStrafing < 0;
	}

	public boolean isOnCertainBlock(int blockID) {
		int i = MathHelper.floor_double(player.posX);
		int j = MathHelper.floor_double(player.boundingBox.minY - 0.1F + player.motionY);
		int k = MathHelper.floor_double(player.posZ);
		return player.worldObj.getBlockId(i, j, k) == blockID;
	}

	public boolean isRolling() {
		return rollAnimation < 1F;
	}

	public boolean isSelectedBlockClose(MovingObjectPosition movingobjectposition, float f) {
		return isSelectedBlockClose(movingobjectposition, f, 0D, 0D, 0D);
	}

	public boolean isSelectedBlockClose(MovingObjectPosition movingobjectposition, float f, double addX, double addY, double addZ) {
		double d1 = Math.sqrt(Math.pow((movingobjectposition.blockX + 0.5D) + addX - player.posX, 2));
		double d2 = Math.sqrt(Math.pow((movingobjectposition.blockY + 0.5D) + addY - player.posY, 2));
		double d3 = Math.sqrt(Math.pow((movingobjectposition.blockZ + 0.5D) + addZ - player.posZ, 2));
		double dXYZ = Math.sqrt((d1 * d1) + (d2 * d2) + (d3 * d3));
		return dXYZ <= f;
	}

	public boolean isSelectedBlockOnLevel(MovingObjectPosition movingobjectposition, int i) {
		int j = MathHelper.floor_double(player.boundingBox.minY) + i;
		return j == movingobjectposition.blockY;
	}

	public boolean isSelectedEntityClose(Entity ent, float f) {
		return isSelectedEntityClose(ent, f, 0D, 0D, 0D);
	}

	public boolean isSelectedEntityClose(Entity ent, float f, double addX, double addY, double addZ) {
		double d1 = Math.sqrt(Math.pow((ent.posX + 0.5D) + addX - player.posX, 2));
		double d2 = Math.sqrt(Math.pow((ent.posY + 0.5D) + addY - player.posY, 2));
		double d3 = Math.sqrt(Math.pow((ent.posZ + 0.5D) + addZ - player.posZ, 2));
		double dXYZ = Math.sqrt((d1 * d1) + (d2 * d2) + (d3 * d3));
		return dXYZ <= f;
	}

	public boolean isSelectedEntityOnLevel(Entity ent, int i) {
		int j = MathHelper.floor_double(player.boundingBox.minY) + i;
		return j == MathHelper.floor_double(ent.boundingBox.minY);
	}

	public boolean isTooHungry() {
		return player.getFoodStats().getFoodLevel() <= 6;
	}

	public boolean isWallrunning() {
		return move instanceof MoveWallrun;
	}

	public float roll(float f) {
		if (!freeRunning || f < 3F) {
			return f;
		}
		float maxFall = 6F;
		if (f < maxFall) {
			f /= 2F;
			return f;
		}
		int i = MathHelper.floor_double(player.posX);
		int j = MathHelper.floor_double(player.boundingBox.minY - 1.1F + player.motionY);
		int j1 = MathHelper.floor_double(player.boundingBox.minY + 0.1F + player.motionY);
		int k = MathHelper.floor_double(player.posZ);
		int b = player.worldObj.getBlockId(i, j, k);
		if (b == Block.fence.blockID || b == Block.netherFence.blockID || isOnCertainBlock(Block.leaves.blockID) || isOnCertainBlock(FRCommonProxy.barWood.blockID) || player.isInWater()) {
			f /= 2F;
			return f;
		}
		if (!isMovingForwards()) {
			f *= 0.8F;
			return f;
		}
		float f1 = 1.0F;
		double d = -MathHelper.sin((player.rotationYaw / 180F) * 3.141593F) * f1;
		double d1 = MathHelper.cos((player.rotationYaw / 180F) * 3.141593F) * f1;
		startRolling();
		player.setVelocity(d, 0, d1);
		player.addExhaustion(0.3F);
		f /= 2F;
		return f;
	}

	public void setMove(Move move) {
		this.move = move;
	}

	public void startRolling() {
		rollAnimation = 0F;
		startRollingYaw = player.rotationYaw;
		startRollingPitch = player.rotationPitch;
	}

	public void stopMove() {
		if (isMoving()) {
			move.moveDone();
		}
	}

	public void tryGrabLedge() {
		if (!player.onGround && situation.canHangStill() && !isWallrunning()) {
			isClimbing = true;
			Vec3 vec3d = situation.getHangPositions();
			player.motionX = vec3d.xCoord - player.posX;
			player.motionY = vec3d.yCoord - player.posY;
			player.motionZ = vec3d.zCoord - player.posZ;
		}
	}

	protected MovingObjectPosition getMovingObjectPositionFromPlayer(boolean par3) {
		float f = 1.0F;
		float f1 = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * f;
		float f2 = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * f;
		double d0 = player.prevPosX + (player.posX - player.prevPosX) * f;
		double d1 = player.prevPosY + (player.posY - player.prevPosY) * f + (player.worldObj.isRemote ? player.getEyeHeight() - player.getDefaultEyeHeight() : player.getEyeHeight());
		double d2 = player.prevPosZ + (player.posZ - player.prevPosZ) * f;
		Vec3 vec3 = player.worldObj.getWorldVec3Pool().getVecFromPool(d0, d1, d2);
		float f3 = MathHelper.cos(-f2 * 0.017453292F - (float) Math.PI);
		float f4 = MathHelper.sin(-f2 * 0.017453292F - (float) Math.PI);
		float f5 = -MathHelper.cos(-f1 * 0.017453292F);
		float f6 = MathHelper.sin(-f1 * 0.017453292F);
		float f7 = f4 * f5;
		float f8 = f3 * f5;
		double d3 = 5.0D;
		if (player instanceof EntityPlayerMP) {
			d3 = ((EntityPlayerMP) player).theItemInWorldManager.getBlockReachDistance();
		}
		Vec3 vec31 = vec3.addVector(f7 * d3, f6 * d3, f8 * d3);
		return player.worldObj.rayTraceBlocks_do_do(vec3, vec31, par3, !par3);
	}

	protected void updateAnimations(float rendertime) {
		if (isRolling()) {
			animateRoll(prevRollAnimation + (rollAnimation - prevRollAnimation) * rendertime);
		}
	}

	private void addAllClimableBlocks() {
		climbableBlocks.clear();
		climbableInside.clear();
		//BESIDE
		climbableBlocks.add(Block.leaves.blockID);
		climbableBlocks.add(Block.dispenser.blockID);
		climbableBlocks.add(Block.music.blockID);
		climbableBlocks.add(Block.bed.blockID);
		climbableBlocks.add(Block.woodDoubleSlab.blockID);
		climbableBlocks.add(Block.woodSingleSlab.blockID);
		climbableBlocks.add(Block.bookShelf.blockID);
		climbableBlocks.add(Block.tilledField.blockID);
		climbableBlocks.add(Block.mobSpawner.blockID);
		climbableBlocks.add(Block.stairsBrick.blockID);
		climbableBlocks.add(Block.chest.blockID);
		climbableBlocks.add(Block.workbench.blockID);
		climbableBlocks.add(Block.furnaceIdle.blockID);
		climbableBlocks.add(Block.furnaceBurning.blockID);
		climbableBlocks.add(Block.signWall.blockID);
		climbableBlocks.add(Block.signPost.blockID);
		climbableBlocks.add(Block.doorWood.blockID);
		climbableBlocks.add(Block.doorIron.blockID);
		climbableBlocks.add(Block.pistonBase.blockID);
		climbableBlocks.add(Block.pistonStickyBase.blockID);
		climbableBlocks.add(Block.pistonExtension.blockID);
		climbableBlocks.add(Block.stairsCobblestone.blockID);
		climbableBlocks.add(Block.jukebox.blockID);
		climbableBlocks.add(Block.pumpkin.blockID);
		climbableBlocks.add(Block.pumpkinLantern.blockID);
		climbableBlocks.add(Block.fence.blockID);
		climbableBlocks.add(Block.trapdoor.blockID);
		climbableBlocks.add(Block.netherFence.blockID);
		climbableBlocks.add(Block.stairsNetherBrick.blockID);
		climbableBlocks.add(Block.stairsStoneBrick.blockID);
		climbableBlocks.add(Block.stairsBrick.blockID);
		climbableBlocks.add(Block.fenceGate.blockID);
		climbableBlocks.add(Block.lockedChest.blockID);
		climbableBlocks.add(Block.enchantmentTable.blockID);
		if (FRCommonProxy.barWood != null) {
			climbableBlocks.add(FRCommonProxy.barWood.blockID);
		}
		//INSIDE
		climbableInside.add(Block.stoneButton.blockID);
		climbableInside.add(Block.woodenButton.blockID);
		climbableInside.add(Block.fenceIron.blockID);
		if (FRCommonProxy.edgeWood != null) {
			climbableInside.add(FRCommonProxy.edgeWood.blockID);
		}
		if (FRCommonProxy.edgeStone != null) {
			climbableInside.add(FRCommonProxy.edgeStone.blockID);
		}
	}

	private void handleFreerunning() {
		if (isTooHungry()) {
			stopMove();
			return;
		}
		//Not in water
		if (!player.isInWater() && !player.handleLavaMovement()) {
			if (player.onGround || player.isSneaking() || player.isOnLadder()) {
				isClimbing = false;
				stopMove();
			}
			//Climbing
			if (isClimbing) {
				player.addExhaustion(0.001F);
				player.fallDistance = 0.0F;
				player.motionX = player.motionY = player.motionZ = 0D;
				if (!situation.canHangStill() && !(move instanceof MoveAroundEdge)) {
					isClimbing = false;
				} else if (isHangingStill()) {
					Vec3 vec3d = situation.getHangPositions();
					player.motionX = vec3d.xCoord - player.posX;
					player.motionY = vec3d.yCoord - player.posY;
					player.motionZ = vec3d.zCoord - player.posZ;
					int lookdirection = situation.lookDirection;
					if (isMovingForwards()) {
						float y = situation.canPushUp();
						if (situation.canJumpUpBehind()) {
							Move.upBehind.performMove(player, lookdirection);
							player.addExhaustion(0.3F);
						} else if (situation.canClimbUp()) {
							Move.climbUp.performMove(player, lookdirection);
						} else if (y != 0) {
							Move.pushUp.performMove(player, lookdirection, y);
							player.addExhaustion(0.3F);
						}
					} else if (isMovingBackwards()) {
						if (situation.canClimbDown()) {
							Move.climbDown.performMove(player, lookdirection);
						}
					} else if (isMovingLeft()) {
						if (situation.canClimbLeft()) {
							Move.climbLeft.performMove(player, lookdirection);
						}/*
						 * else if (situation.canClimbAroundEdgeLeft()) {
						 * FR_Move.climbAroundLeft.performMove(player,
						 * lookdirection); }
						 */
					} else if (isMovingRight()) {
						if (situation.canClimbRight()) {
							Move.climbRight.performMove(player, lookdirection);
						}/*
						 * else if (situation.canClimbAroundEdgeRight()) {
						 * FR_Move.climbAroundRight.performMove(player,
						 * lookdirection); }
						 */
					}
				}
				if (freeRunning && player.isJumping && isHangingStill()) {
					if (isMovingForwards() && !isWallrunning()) {
						Move.ejectUp.performMove(player, situation.lookDirection);
						player.addExhaustion(0.3F);
					} else if (isMovingLeft()) {
						Move.ejectLeft.performMove(player, situation.lookDirection);
						player.addExhaustion(0.3F);
					} else if (isMovingRight()) {
						Move.ejectRight.performMove(player, situation.lookDirection);
						player.addExhaustion(0.3F);
					} else {
						Move.ejectBack.performMove(player, situation.lookDirection);
						player.addExhaustion(0.3F);
					}
				}
				return;
			}
			//Not climbing
			if (freeRunning) {
				tryGrabLedge();
				//Wallkick
				if (!player.onGround) {
					if (FRCommonProxy.properties.enableWallKick && isWallrunning() && player.isJumping && move.getAnimationProgress() > 0.3F) {
						stopMove();
						if (isMovingLeft()) {
							Move.ejectLeft.performMove(player, situation.lookDirection, 0.8F);
							player.addExhaustion(0.3F);
						} else if (isMovingRight()) {
							Move.ejectRight.performMove(player, situation.lookDirection, 0.8F);
							player.addExhaustion(0.3F);
						} else {
							Move.ejectBack.performMove(player, situation.lookDirection, 0.8F);
							player.addExhaustion(0.3F);
						}
					}
					return;
				}
				if ((isMovingForwards() || player.isCollidedHorizontally) && !isRolling()) {
					int i = canJumpOverGap();
					int j = canHopOver();
					if (!player.isJumping) {
						if (i == 1) {
							player.addVelocity(0D, 0.35D, 0D);
							player.isJumping = true;
							player.addExhaustion(0.1F);
						} else if (i == 2) {
							player.jump();
						}
					}
					if (j == 1 && !player.isJumping) {
						player.jump();
					} else if (j > 1 || canWallrun()) {
						Move.wallrun.performMove(player, getLookDirection(), 1.8F);
						player.addExhaustion(0.8F);
					}
				}
			}
			if (isWallrunning()) {
				if (player.isCollidedVertically && !player.onGround) {
					move.moveDone();
				}
				tryGrabLedge();
			}
			return;
		}
	}

	private void handleMoves() {
		Move.onUpdate(this);
	}

	private void handleStats(double d, double d1, double d2) {
		/*
		 * if (player.ridingEntity != null) { return; } if (isClimbing) {
		 * player.addStat(StatList.distanceClimbedStat, (int) Math.round(d1 *
		 * 100D)); } else if (!player.isInsideOfMaterial(Material.water) &&
		 * !player.isInWater() && !player.isOnLadder() && !player.onGround) {
		 * int l = Math.round(MathHelper.sqrt_double(d * d + d2 * d2) * 100F);
		 * if (l > 25 && freeRunning) {
		 * player.addStat(StatList.distanceFlownStat, -l); } }
		 */
	}

	private void handleTimers() {
	}
}
