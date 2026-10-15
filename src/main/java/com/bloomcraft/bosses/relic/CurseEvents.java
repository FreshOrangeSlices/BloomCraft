package com.bloomcraft.bosses.relic;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.relic.effects.HauntCurses;
import com.bloomcraft.bosses.relic.effects.NatureCurses;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Spawns;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** The event-driven halves of curses: sleeping, trading, leashing, eating, fishing and the haunted mobs. */
public final class CurseEvents {

	private CurseEvents() {
	}

	public static void register(Bosses mod) {
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
			if (player instanceof ServerPlayer sp && mod.settings() != null && mod.relics().preventsSleep(sp)) {
				return new Player.BedSleepingProblem(mod.settings().messages.get("insomnia", Map.of()));
			}
			return null;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || mod.settings() == null) {
				return InteractionResult.PASS;
			}
			if (Spawns.tagged(entity, HauntCurses.GUEST)) {
				return InteractionResult.FAIL; // nobody trades with the guest
			}
			if (!mod.relics().has(sp, "pariah")) {
				return InteractionResult.PASS;
			}
			if (entity instanceof Villager villager && mod.relics().get("pariah") instanceof NatureCurses.Pariah pariah) {
				pariah.sour(villager, sp);
				return InteractionResult.PASS;
			}
			// A Pariah can't put a lead on an animal: it shies away.
			if (entity instanceof Animal animal && NatureCurses.Pariah.wild(animal) && player.getItemInHand(hand).is(Items.LEAD)) {
				Fx.actionBar(sp, Component.literal("It shies away from you.").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || mod.settings() == null) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (NatureCurses.MEAT.contains(held.getItem()) && mod.relics().has(sp, "herbivore")) {
				Fx.actionBar(sp, Component.literal("You can't bring yourself to eat that.").withStyle(ChatFormatting.DARK_GREEN));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// A Pariah's fish swim away from the hook; only treasure bites.
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!table.is(BuiltInLootTables.FISHING) || mod.settings() == null
				|| !(context.getOptional(LootContextParams.THIS_ENTITY) instanceof FishingHook hook)
				|| !(hook.getPlayerOwner() instanceof ServerPlayer angler) || !mod.relics().has(angler, "pariah")) {
				return;
			}
			for (int i = 0; i < drops.size(); i++) {
				if (NatureCurses.FISH.contains(drops.get(i).getItem())) {
					List<ItemStack> treasure = treasure(context, hook);
					drops.set(i, treasure.isEmpty() ? new ItemStack(Items.NAUTILUS_SHELL) : treasure.getFirst());
				}
			}
		});
	}

	/** A roll from the vanilla fishing treasure table (enchanted books, bows and rods, name tags, saddles, shells). */
	private static List<ItemStack> treasure(LootContext context, Entity hook) {
		ServerLevel level = context.getLevel();
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		@Nullable ItemInstance tool = context.getOptional(LootContextParams.TOOL);
		if (origin == null || tool == null) {
			return List.of();
		}
		LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_TREASURE);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, origin)
			.withParameter(LootContextParams.TOOL, tool)
			.withOptionalParameter(LootContextParams.THIS_ENTITY, hook)
			.withLuck(context.getLuck())
			.create(LootContextParamSets.FISHING);
		return table.getRandomItems(params);
	}
}
