package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.function.Function;

/**
 * The pet items: Pet Blooms (one per pet, looking like its flower), the Pet Toy (one for all your pets) and the
 * Fetch Stick. Right-clicking with them is handled by {@link PetEvents}.
 */
public final class PetItems {

	/** Right-click: summon / dismiss its pet. Punch: the pet menu. */
	public static final class BloomItem extends Item {
		public BloomItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp && Pets.get() != null) {
				Pets.get().events().useBloom(sp, hand);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** Right-click: call your pet / send it home. Punch: the pet menu. */
	public static final class ToyItem extends Item {
		public ToyItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp && Pets.get() != null) {
				Pets.get().events().useToy(sp, hand);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** Right-click: throw it for your pet. */
	public static final class FetchStickItem extends Item {
		public FetchStickItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp && Pets.get() != null) {
				Pets.get().events().useFetchStick(sp, hand);
			}
			return InteractionResult.SUCCESS;
		}
	}

	public static final Item PET_BLOOM = register("pet_bloom", BloomItem::new, new Item.Properties().stacksTo(1)
		.rarity(Rarity.UNCOMMON).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
	public static final Item PET_TOY = register("pet_toy", ToyItem::new, new Item.Properties().stacksTo(1));
	public static final Item FETCH_STICK = register("fetch_stick", FetchStickItem::new, new Item.Properties().stacksTo(1));

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BloomCraft.id("pets"));

	private PetItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BloomCraft.id(name));
		Item item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void init() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(PET_BLOOM))
			.title(Component.translatable("itemGroup.bloomcraft.pets"))
			.displayItems((params, output) -> {
				output.accept(Blooms.toy());
				output.accept(Blooms.fetchStick());
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, tab);
	}
}
