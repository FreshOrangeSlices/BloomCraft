package com.bloomcraft.bosses.item;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** A Nemesis's Challenge: use it to call that Nemesis to you now. It also holds its Terms of Surrender. */
public class ChallengeItem extends Item {

	public ChallengeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && Bosses.get().settings() != null) {
			Bosses.get().nemesis().useChallenge(sp, player.getItemInHand(hand), hand);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return use(context.getLevel(), context.getPlayer(), context.getHand());
	}
}
