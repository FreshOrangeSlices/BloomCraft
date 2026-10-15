package com.bloomcraft.bosses.item;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.core.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The Hunter's Compass: held, it points at the nearest boss. Click Boss Gear onto it to break the gear down into
 * a Boss Soul of its rank; click an Empowerment Rune onto it to upgrade it.
 */
public class HuntersCompassItem extends Item {

	public HuntersCompassItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (BossItems.kind(stack) == null && Bosses.get().settings() != null) {
			// freshly crafted: give it its name, lore and tier
			stack.applyComponents(BossItems.compass(1).getComponentsPatch());
		}
		if (entity instanceof ServerPlayer player && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
			&& level.getGameTime() % 10 == 0) {
			Bosses.get().compass().update(player, stack);
		}
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack compass, ItemStack carried, Slot slot, ClickAction action,
											Player player, SlotAccess access) {
		if (!BossItems.isSalvage(carried, compass) || player.isCreative()) {
			return false;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return true;
		}
		BossRank soulRank = BossItems.salvageRank(carried);
		if (soulRank == null) {
			Fx.actionBar(sp, Component.literal("This gear came from a summoned boss: there's no soul in it to draw out.")
				.withStyle(ChatFormatting.GRAY));
			return true;
		}
		Component confirm = BossItems.salvagePrompt(sp, carried, "slot" + slot.index);
		if (confirm != null) {
			Fx.actionBar(sp, confirm);
			Fx.playTo(sp, "block.note_block.pling", 0.8f, 1.2f);
			return true;
		}
		Component name = carried.getHoverName();
		access.set(BossItems.soul(soulRank, 1));
		Fx.actionBar(sp, Bosses.get().settings().messages.get("salvaged", Map.of("item", name)));
		Fx.playTo(sp, "block.sculk_catalyst.bloom", 1.0f, 1.0f);
		Fx.playTo(sp, "entity.allay.item_taken", 0.8f, 0.7f);
		Bosses.get().relics().refresh(sp);
		return true;
	}
}
