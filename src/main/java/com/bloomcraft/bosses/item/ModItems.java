package com.bloomcraft.bosses.item;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import com.bloomcraft.bosses.waystone.WaystoneBlock;
import com.bloomcraft.bosses.waystone.Waystones;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Function;

/** The mod's items. Most carry their details (rank, relic...) as data, and pick their texture from it. */
public final class ModItems {

	/** The waystone block (its item is {@link #WAYSTONE}). */
	public static final Block WAYSTONE_BLOCK = registerBlock("waystone", WaystoneBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.5f, 3600000f).sound(SoundType.LODESTONE)
		.pushReaction(PushReaction.IMMOVEABLE).lightLevel(state -> 7).noLootTable());

	public static final Item BOSS_SOUL = register("boss_soul", SoulItem::new, new Item.Properties().stacksTo(64));
	public static final Item BOSS_TOTEM = register("boss_totem", TotemItem::new,
		new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
	public static final Item EMPOWERMENT_RUNE = register("empowerment_rune", ApplyItem::new,
		new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));
	public static final Item RELIC = register("relic", ApplyItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item RELIC_CATALYST = register("relic_catalyst", ApplyItem::new,
		new Item.Properties().stacksTo(64).rarity(Rarity.EPIC));
	public static final Item HUNTERS_COMPASS = register("hunters_compass", HuntersCompassItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item NEMESIS_CHALLENGE = register("nemesis_challenge", ChallengeItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	public static final Item NEMESIS_STATUE = register("nemesis_statue", StatueItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	public static final Item WAYSTONE = register("waystone", p -> new BlockItem(WAYSTONE_BLOCK, p),
		new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.RARE));

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BloomCraft.id("bosses"));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BloomCraft.id(name));
		Item item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item); // so the block knows its item (pick block, drops)
		}
		return item;
	}

	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory,
									   BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BloomCraft.id(name));
		Block block = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		return block;
	}

	public static void init() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(BOSS_TOTEM))
			.title(Component.translatable("itemGroup.bloomcraft.bosses"))
			.displayItems((params, output) -> {
				if (Bosses.get().settings() == null) {
					return;
				}
				for (BossRank rank : BossRank.values()) {
					output.accept(BossItems.soul(rank, 1));
				}
				for (BossRank rank : BossRank.values()) {
					if (rank.natural()) {
						output.accept(BossItems.totem(rank));
					}
				}
				output.accept(BossItems.compass(1));
				output.accept(Waystones.item(null));
				output.accept(BossItems.catalyst());
				for (var stat : Bosses.get().settings().empowermentStats) {
					output.accept(BossItems.rune(BossRank.GOLD, stat, stat.roll(BossRank.GOLD, true)));
				}
				for (var relic : Bosses.get().relics().enabledEffects(false)) {
					output.accept(BossItems.relic(relic, null));
				}
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, tab);
	}
}
