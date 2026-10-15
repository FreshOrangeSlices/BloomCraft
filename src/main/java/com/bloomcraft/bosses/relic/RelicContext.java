package com.bloomcraft.bosses.relic;

import net.minecraft.server.level.ServerPlayer;

/** Where an active relic is: on the item in the main hand, or on worn armor / an off-hand shield. */
public record RelicContext(ServerPlayer player, Slot slot, RelicManager manager) {

	public enum Slot { HAND, ARMOR }

	public boolean inHand() {
		return slot == Slot.HAND;
	}
}
