package com.bloomcraft.pets;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The pet roster. Each species has a category (how it moves), the food that bonds and heals it, the flower its Pet
 * Bloom looks like, how much it can carry, and one small passive bonus (see {@link Bonuses}).
 *
 * <p>{@code rideScale} &gt; 0 marks a small pet: to ride it, either the pet grows by that factor or the rider shrinks
 * by it (decided once per pet, 50/50, when it is tamed).</p>
 */
public enum Species {

	// ---- Combat: fight beside you, slowest ----
	WOLF(EntityTypes.WOLF, Category.COMBAT, "Wolf", meats(), "oxeye_daisy", Storage.MEDIUM, 1.6, 4,
		"Pack Hunter: +8% melee damage"),
	POLAR_BEAR(EntityTypes.POLAR_BEAR, Category.COMBAT, "Polar Bear", of(Items.SALMON, Items.COOKED_SALMON),
		"lily_of_the_valley", Storage.MEDIUM, 0, 6, "Thick Fur: you can't freeze, and Slowness can't touch you"),
	IRON_GOLEM(EntityTypes.IRON_GOLEM, Category.COMBAT, "Iron Golem", of(Items.IRON_INGOT), "poppy", Storage.MEDIUM,
		0, 8, "Bulwark: +3 armor"),
	GOAT(EntityTypes.GOAT, Category.COMBAT, "Goat", of(Items.WHEAT), "azure_bluet", Storage.MEDIUM, 0, 4,
		"Sure-footed: 40% less fall damage"),
	PANDA(EntityTypes.PANDA, Category.COMBAT, "Panda", of(Items.BAMBOO), "white_tulip", Storage.MEDIUM, 0, 5,
		"Bamboo Nap: stand still for a moment and you slowly heal"),
	VEX(EntityTypes.VEX, Category.COMBAT, "Vex", of(Items.EMERALD), "wither_rose", Storage.NONE, 0, 4,
		"Phantom Edge: +10% attack speed. Wears armor and weapons; can't be ridden"),

	// ---- Pack: carry the most, step up 1.5 blocks ----
	DONKEY(EntityTypes.DONKEY, Category.PACK, "Donkey", of(Items.GOLDEN_CARROT), "allium", Storage.PACK, 0, 0,
		"Sturdy Back: +2 hearts"),
	MULE(EntityTypes.MULE, Category.PACK, "Mule", of(Items.APPLE), "rose_bush", Storage.PACK, 0, 0,
		"Stubborn: +30% knockback resistance"),
	LLAMA(EntityTypes.LLAMA, Category.PACK, "Llama", of(Items.HAY_BLOCK), "peony", Storage.PACK, 0, 0,
		"Spit: mobs that hit you get spat on"),
	CAMEL(EntityTypes.CAMEL, Category.PACK, "Camel", of(Items.CACTUS), "sunflower", Storage.PACK, 0, 0,
		"Desert Stride: Speed on sand, soul sand and snow"),
	SNIFFER(EntityTypes.SNIFFER, Category.PACK, "Sniffer", of(Items.TORCHFLOWER_SEEDS), "pitcher_plant",
		Storage.PACK, 0, 0, "Treasure Nose: now and then it digs up buried treasure, armor trims included"),

	// ---- Speedsters: fastest, good jump ----
	HORSE(EntityTypes.HORSE, Category.SPEEDSTER, "Horse", of(Items.SUGAR), "lilac", Storage.MEDIUM, 0, 0,
		"Stamina: +8% movement speed"),
	FOX(EntityTypes.FOX, Category.SPEEDSTER, "Fox", of(Items.SWEET_BERRIES, Items.GLOW_BERRIES), "torchflower",
		Storage.SMALL, 2.2, 0, "Sly: mobs notice you from 25% shorter range"),
	RABBIT(EntityTypes.RABBIT, Category.SPEEDSTER, "Rabbit", of(Items.CARROT, Items.DANDELION), "pink_tulip",
		Storage.SMALL, 3.0, 0, "Spring: Jump Boost"),
	OCELOT(EntityTypes.OCELOT, Category.SPEEDSTER, "Ocelot", of(Items.TROPICAL_FISH), "orange_tulip", Storage.SMALL,
		2.4, 0, "Night Watch: creepers and phantoms keep away, and you wake up to little gifts"),
	STRIDER(EntityTypes.STRIDER, Category.SPEEDSTER, "Strider", of(Items.WARPED_FUNGUS), "crimson_fungus",
		Storage.MEDIUM, 0, 0, "Lava-born: Fire Resistance in the Nether, and it walks on lava"),

	// ---- Utility: about their bonus ----
	CAT(EntityTypes.CAT, Category.UTILITY, "Cat", of(Items.COD, Items.COOKED_COD), "red_tulip", Storage.SMALL, 2.4,
		0, "Night Watch: creepers and phantoms keep away, and you wake up to little gifts"),
	BEE(EntityTypes.BEE, Category.UTILITY, "Bee", stack -> stack.is(ItemTags.BEE_FOOD), "dandelion", Storage.SMALL, 2.6, 0,
		"Pollinator: crops near you grow faster"),
	ALLAY(EntityTypes.ALLAY, Category.UTILITY, "Allay", of(Items.AMETHYST_SHARD), "blue_orchid", Storage.SMALL, 3.0,
		0, "Collector: pulls nearby drops to you and bottles loose XP into its storage"),
	CHICKEN(EntityTypes.CHICKEN, Category.UTILITY, "Chicken", of(Items.WHEAT_SEEDS, Items.MELON_SEEDS,
		Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS), "oxeye_daisy", Storage.SMALL, 2.6, 0,
		"Feather Fall: you glide down long drops"),
	COW(EntityTypes.COW, Category.UTILITY, "Cow", of(Items.WHEAT), "cornflower", Storage.MEDIUM, 0, 0,
		"Fresh Milk: clears one bad effect every 2 minutes"),
	TURTLE(EntityTypes.TURTLE, Category.UTILITY, "Turtle", of(Items.SEAGRASS), "lily_pad", Storage.SMALL, 1.8, 0,
		"Gills: you can breathe underwater"),
	ARMADILLO(EntityTypes.ARMADILLO, Category.UTILITY, "Armadillo", of(Items.SPIDER_EYE), "pink_petals",
		Storage.SMALL, 2.6, 0, "Scutes: 20% less damage from arrows and other projectiles"),
	FROG(EntityTypes.FROG, Category.UTILITY, "Frog", of(Items.SLIME_BALL), "big_dripleaf", Storage.SMALL, 2.4, 0,
		"Frog Kick: Dolphin's Grace while swimming"),
	PIG(EntityTypes.PIG, Category.UTILITY, "Pig", of(Items.CARROT, Items.POTATO, Items.BEETROOT), "brown_mushroom",
		Storage.SMALL, 0, 0, "Lumberjack: chop a tree's bottom log with an axe and the whole tree comes down "
		+ "(huge mushrooms and nether trees too). Sneak to chop just one"),
	AXOLOTL(EntityTypes.AXOLOTL, Category.UTILITY, "Axolotl", of(Items.TROPICAL_FISH_BUCKET, Items.TROPICAL_FISH),
		"spore_blossom", Storage.SMALL, 2.6, 0, "Healing Touch: you slowly heal while hurt, faster in water"),
	BAT(EntityTypes.BAT, Category.UTILITY, "Bat", of(Items.MELON_SLICE), "open_eyeblossom", Storage.SMALL, 3.0, 0,
		"Echolocation: you can see in the dark");

	/** How much a pet carries. Sizes grow with level (see {@link #slots}). */
	public enum Storage {
		NONE, SMALL, MEDIUM, PACK;

		/** Inventory slots at this level: small 9 -> 18, medium 18 -> 27, pack 27 -> 54 (a double chest). */
		public int slots(int level) {
			return switch (this) {
				case NONE -> 0;
				case SMALL -> level >= 5 ? 18 : 9;
				case MEDIUM -> level >= 6 ? 27 : 18;
				case PACK -> level >= 10 ? 54 : level >= 7 ? 45 : level >= 4 ? 36 : 27;
			};
		}

		public String displayName() {
			return switch (this) {
				case NONE -> "None";
				case SMALL -> "Small";
				case MEDIUM -> "Medium";
				case PACK -> "Large";
			};
		}
	}

	private final EntityType<?> type;
	private final Category category;
	private final String displayName;
	private final Predicate<ItemStack> food;
	private final String bloomModel;
	private final Storage storage;
	private final double rideScale;
	private final double attackDamage;
	private final String bonus;

	Species(EntityType<?> type, Category category, String displayName, Predicate<ItemStack> food, String bloomModel,
			Storage storage, double rideScale, double attackDamage, String bonus) {
		this.type = type;
		this.category = category;
		this.displayName = displayName;
		this.food = food;
		this.bloomModel = bloomModel;
		this.storage = storage;
		this.rideScale = rideScale;
		this.attackDamage = attackDamage;
		this.bonus = bonus;
	}

	private static Predicate<ItemStack> of(Item... items) {
		Set<Item> set = Set.of(items);
		return stack -> set.contains(stack.getItem());
	}

	private static Predicate<ItemStack> meats() {
		return of(Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.CHICKEN,
			Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON, Items.RABBIT, Items.COOKED_RABBIT,
			Items.ROTTEN_FLESH);
	}

	public EntityType<?> type() {
		return type;
	}

	public Category category() {
		return category;
	}

	public String displayName() {
		return displayName;
	}

	/** "Foxes", "Wolves", "Polar Bears"... */
	public String plural() {
		if (displayName.endsWith("x")) {
			return displayName + "es";
		}
		if (displayName.endsWith("f")) {
			return displayName.substring(0, displayName.length() - 1) + "ves";
		}
		return displayName + "s";
	}

	public boolean likes(ItemStack stack) {
		return !stack.isEmpty() && food.test(stack);
	}

	/** The flower its bloom looks like (an item model id, e.g. minecraft:poppy). */
	public String bloomModel() {
		return bloomModel;
	}

	public Storage storage() {
		return storage;
	}

	/** Small pets: the factor the pet grows by (or the rider shrinks by) to ride. 0 = big enough already. */
	public double rideScale() {
		return rideScale;
	}

	public boolean small() {
		return rideScale > 0;
	}

	public boolean combat() {
		return category == Category.COMBAT;
	}

	/** Base melee damage of a combat pet. */
	public double attackDamage() {
		return attackDamage;
	}

	public boolean rideable() {
		return this != VEX;
	}

	/** Bees, Allays and Bats hover a few blocks up when ridden; the Vex flies on its own. */
	public boolean hovers() {
		return this == BEE || this == ALLAY || this == BAT;
	}

	/** Turtles, Frogs and Axolotls are quick in water, following or ridden. */
	public boolean swims() {
		return this == TURTLE || this == FROG || this == AXOLOTL;
	}

	/** Pets that play fetch (animals, not the Vex or the Iron Golem). */
	public boolean playsFetch() {
		return this != VEX && this != IRON_GOLEM;
	}

	public boolean flies() {
		return this == BEE || this == ALLAY || this == BAT || this == VEX;
	}

	public String bonus() {
		return bonus;
	}

	/** "polar-bear" (as used in the config and commands). */
	public String id() {
		return name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

	/** The game's id of its mob ("polar_bear"), for its spawn egg picture. */
	public String mobId() {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
	}

	public static @Nullable Species of(EntityType<?> type) {
		for (Species s : values()) {
			if (s.type == type) {
				return s;
			}
		}
		return null;
	}

	public static @Nullable Species parse(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		for (Species s : values()) {
			if (s.name().equals(key)) {
				return s;
			}
		}
		return null;
	}

	public static List<String> ids() {
		return java.util.Arrays.stream(values()).map(Species::id).toList();
	}
}
