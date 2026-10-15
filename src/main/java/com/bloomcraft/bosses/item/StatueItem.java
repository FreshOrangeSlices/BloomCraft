package com.bloomcraft.bosses.item;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A Nemesis Statue: use it on a block to place the figure (it faces you). */
public class StatueItem extends Item {

	public StatueItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || Bosses.get().settings() == null) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();
		BlockPos at = level.getBlockState(pos).canBeReplaced() ? pos : pos.relative(context.getClickedFace());
		float yaw = player == null ? 0 : player.getYRot() + 180f;
		ItemStack stack = context.getItemInHand();
		if (Bosses.get().statues().place(level, stack, Vec3.atBottomCenterOf(at), yaw)) {
			if (player == null || !player.isCreative()) {
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.FAIL;
	}
}
