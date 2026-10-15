package com.bloomcraft.bosses.waystone;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The waystone block: use it to open the network, rename it with a name tag, sneak-use an item to set its icon. */
public class WaystoneBlock extends Block {

	public WaystoneBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && placer instanceof ServerPlayer player && Bosses.get().settings() != null) {
			Bosses.get().waystones().place(player, pos, stack);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp && Bosses.get().settings() != null) {
			Bosses.get().waystones().use(sp, pos, ItemStack.EMPTY);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && Bosses.get().settings() != null) {
			Bosses.get().waystones().use(sp, pos, stack);
		}
		return InteractionResult.SUCCESS;
	}

	/** A soft shimmer (drawn by each client, costs the server nothing). */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.05;
		double z = pos.getZ() + 0.5;
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.5, y,
				z + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
		}
		level.addParticle(ParticleTypes.PORTAL, x + (random.nextDouble() - 0.5) * 0.6, y - 0.2 + random.nextDouble() * 0.4,
			z + (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.4, 0.2, (random.nextDouble() - 0.5) * 0.4);
	}
}
