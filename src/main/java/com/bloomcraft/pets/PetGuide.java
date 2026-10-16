package com.bloomcraft.pets;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pet Guide: a written book with little pictures (item sprites drawn right in the text). How to make a friend is
 * spelled out plainly; what each pet does is only hinted at, so there's something to discover.
 */
public final class PetGuide {

	// dark, warm colours that read well on the book's paper
	private static final int PINK = 0xC2185B;
	private static final int INK = 0x3E2723;
	private static final int SOFT = 0x795548;

	private PetGuide() {
	}

	public static ItemStack book() {
		List<Filterable<Component>> pages = new ArrayList<>();
		for (Component page : pages()) {
			pages.add(Filterable.passThrough(page));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT,
			new WrittenBookContent(Filterable.passThrough("Pet Guide"), "BloomCraft", 0, pages, true));
		return book;
	}

	private static List<Component> pages() {
		Pets mod = Pets.get();
		List<Component> pages = new ArrayList<>();
		pages.add(page()
			.append(nl())
			.append(text("  ✿ BloomCraft Pets ✿", PINK).withStyle(ChatFormatting.BOLD)).append(nl()).append(nl())
			.append(ink("A little guide to\nmaking animal\nfriends!")).append(nl()).append(nl())
			.append(Component.literal("  ")).append(egg(Species.RABBIT)).append(Component.literal(" "))
			.append(egg(Species.FOX)).append(Component.literal(" ")).append(egg(Species.CAT))
			.append(Component.literal(" ")).append(egg(Species.ALLAY)).append(nl()).append(nl())
			.append(ink("Every animal has a\nfavourite snack.\nShare it, and they\njust might stay. "))
			.append(text("♥", PINK)));
		// a page shows 14 short lines; every page below stays within that
		pages.add(page()
			.append(title("Make a Friend ♥")).append(nl()).append(nl())
			.append(ink("1. Find an animal\nin this book.\n"))
			.append(ink("2. Sneak and feed\nit its favourite\nsnack.\n"))
			.append(ink("3. Repeat until the\nhearts fill up!")).append(nl()).append(nl())
			.append(soft("Then it's yours,\nforever and ever.")));
		pages.add(page()
			.append(mod("pet_toy")).append(Component.literal(" ")).append(title("Your Pet Toy")).append(nl()).append(nl())
			.append(ink("Right-click: call\nyour pet, or send\nit home for a nap.")).append(nl()).append(nl())
			.append(ink("Punch: see all\nyour pets and pick\none.")).append(nl()).append(nl())
			.append(ink("Use it on your pet:\nhop on for a ride!")));
		pages.add(page()
			.append(title("More Tricks ✦")).append(nl()).append(nl())
			.append(ink("Click your pet to\nswap to another.")).append(nl()).append(nl())
			.append(ink("Sneak + click with\nan item: its bag.")).append(nl()).append(nl())
			.append(ink("Feed it its snack\nto heal it up.")).append(nl()).append(nl())
			.append(ink("Each has its own\n")).append(mod("pet_bloom")).append(ink(" Bloom, too!")));
		pages.add(page()
			.append(title("Playtime ✦")).append(nl()).append(nl())
			.append(mod("fetch_stick")).append(ink(" Grab a Fetch Stick\nfrom the pet menu\nand throw it!")).append(nl()).append(nl())
			.append(ink("Big pets (level 8+)\ncan keep a post:\nsneak + punch them\nwith your toy.")).append(nl())
			.append(soft("Guards guard, bees\nfarm, allays tidy.")));
		pages.add(page()
			.append(title("Happy Pets ♥")).append(nl()).append(nl())
			.append(ink("Sneak + click your\npet with an empty\nhand to give it a\npat.")).append(nl()).append(nl())
			.append(ink("Snacks, pats and\nfetch make it happy.\nTired pets love a\nnap in their bloom.")).append(nl()).append(nl())
			.append(soft("Fill its hearts and\nit'll love you!")));
		if (mod != null && mod.skills().on()) {
			pages.add(page()
				.append(title("Brave Skills ⚔")).append(nl()).append(nl())
				.append(ink("Wolves, Golems,\nVexes, Bears, Goats\nand Pandas learn\ntricks as your\nTaming skill grows."))
				.append(nl()).append(nl())
				.append(ink("/pets taming\nshows them all.")).append(nl()).append(nl())
				.append(soft("A sad pet won't\nshow off, though.")));
		}
		pages.add(page()
			.append(title("Growing Up ✦")).append(nl()).append(nl())
			.append(ink("Walks, snacks and\nadventures help\nyour pet grow up to\nlevel 10.")).append(nl()).append(nl())
			.append(ink("Bigger bags, more\nheart, and rides!")).append(nl()).append(nl())
			.append(soft("Pets never die. A\ntired pet just goes\nhome for a nap.")));

		for (Category category : Category.values()) {
			List<Species> roster = new ArrayList<>();
			for (Species s : Species.values()) {
				if (s.category() == category && (mod == null || mod.settings().species.contains(s))) {
					roster.add(s);
				}
			}
			for (int i = 0; i < roster.size(); i += 2) { // two pets a page, so nothing runs off the bottom
				MutableComponent page = page().append(text(heading(category), color(category)).withStyle(ChatFormatting.BOLD))
					.append(nl());
				for (Species s : roster.subList(i, Math.min(i + 2, roster.size()))) {
					page.append(nl())
						.append(egg(s)).append(text(" " + s.displayName(), color(s.category())).withStyle(ChatFormatting.BOLD))
						.append(nl())
						.append(text("♥ ", PINK)).append(food(s)).append(ink(" " + foodName(s))).append(nl())
						.append(soft(hint(s))).append(nl());
				}
				pages.add(page);
			}
		}
		return pages;
	}

	// =====================================================================

	private static String heading(Category c) {
		return switch (c) {
			case COMBAT -> "⚔ Brave Buddies";
			case PACK -> "✉ Pack Pals";
			case SPEEDSTER -> "➹ Speedy Pals";
			case UTILITY -> "✿ Helpful Pals";
		};
	}

	private static int color(Category c) {
		return switch (c) {
			case COMBAT -> 0xB71C1C;
			case PACK -> 0xBF5B00;
			case SPEEDSTER -> 0x00695C;
			case UTILITY -> 0x2E7D32;
		};
	}

	/** What it eats, in plain words. */
	private static String foodName(Species s) {
		return switch (s) {
			case WOLF -> "Any meat";
			case POLAR_BEAR -> "Salmon";
			case IRON_GOLEM -> "Iron Ingots";
			case GOAT, COW -> "Wheat";
			case PANDA -> "Bamboo";
			case VEX -> "Emeralds";
			case DONKEY -> "Golden Carrots";
			case MULE -> "Apples";
			case LLAMA -> "Hay Bales";
			case CAMEL -> "Cactus";
			case SNIFFER -> "Torchflower Seeds";
			case HORSE -> "Sugar";
			case FOX -> "Sweet Berries";
			case RABBIT -> "Carrots";
			case OCELOT -> "Tropical Fish";
			case STRIDER -> "Warped Fungus";
			case CAT -> "Cod";
			case BEE -> "Any flower";
			case ALLAY -> "Amethyst Shards";
			case CHICKEN -> "Seeds";
			case TURTLE -> "Seagrass";
			case ARMADILLO -> "Spider Eyes";
			case FROG -> "Slime Balls";
			case PIG -> "Carrots, Potatoes";
			case AXOLOTL -> "Tropical Fish";
			case BAT -> "Melon Slices";
		};
	}

	private static Component food(Species s) {
		return switch (s) {
			case WOLF -> item("beef");
			case POLAR_BEAR -> item("salmon");
			case IRON_GOLEM -> item("iron_ingot");
			case GOAT, COW -> item("wheat");
			case PANDA -> item("bamboo");
			case VEX -> item("emerald");
			case DONKEY -> item("golden_carrot");
			case MULE -> item("apple");
			case LLAMA -> block("hay_block_side");
			case CAMEL -> block("cactus_side");
			case SNIFFER -> item("torchflower_seeds");
			case HORSE -> item("sugar");
			case FOX -> item("sweet_berries");
			case RABBIT -> item("carrot");
			case OCELOT -> item("tropical_fish");
			case STRIDER -> block("warped_fungus");
			case CAT -> item("cod");
			case BEE -> block("poppy");
			case ALLAY -> item("amethyst_shard");
			case CHICKEN -> item("wheat_seeds");
			case TURTLE -> block("seagrass");
			case ARMADILLO -> item("spider_eye");
			case FROG -> item("slime_ball");
			case PIG -> item("carrot");
			case AXOLOTL -> item("tropical_fish_bucket");
			case BAT -> item("melon_slice");
		};
	}

	/** A hint at what the pet does, never the numbers. */
	private static String hint(Species s) {
		return switch (s) {
			case WOLF -> "Brave, loyal, and\nready to ride!";
			case POLAR_BEAR -> "Never minds the cold.";
			case IRON_GOLEM -> "Sturdy as a castle.";
			case GOAT -> "Always lands on its\nfeet.";
			case PANDA -> "Loves a lazy break.";
			case VEX -> "Likes shiny things.\nDresses for battle!";
			case DONKEY -> "A big, big heart.";
			case MULE -> "Hard to push around.";
			case LLAMA -> "Has opinions about\nbullies.";
			case CAMEL -> "Loves a sandy\nstroll.";
			case SNIFFER -> "Has a nose for\ntreasure!";
			case HORSE -> "Puts a spring in\nyour step.";
			case FOX -> "Sneaky, sneaky...";
			case RABBIT -> "Boing! Hop on!";
			case OCELOT -> "Scary things keep\ntheir distance.";
			case STRIDER -> "Toasty toes in the\nNether.";
			case CAT -> "Keeps watch, brings\ngifts, rides along!";
			case BEE -> "Gardens bloom\naround it.";
			case ALLAY -> "A tidy little\nhelper.";
			case CHICKEN -> "Makes falling feel\nlike floating.";
			case COW -> "Fresh milk for bad\ndays.";
			case TURTLE -> "Breathe easy\nunderwater.";
			case ARMADILLO -> "Arrows? What\narrows?";
			case FROG -> "Swims like a dream.";
			case PIG -> "Timber! Trees beware.";
			case AXOLOTL -> "Kisses boo-boos\nbetter.";
			case BAT -> "Lights? Who needs\nlights?";
		};
	}

	// =====================================================================

	private static MutableComponent page() {
		return Component.empty().withColor(INK);
	}

	private static MutableComponent text(String text, int rgb) {
		return Component.literal(text).withColor(rgb);
	}

	private static MutableComponent title(String text) {
		return text(text, PINK).withStyle(ChatFormatting.BOLD);
	}

	private static MutableComponent ink(String text) {
		return text(text, INK);
	}

	private static MutableComponent soft(String text) {
		return text(text, SOFT).withStyle(ChatFormatting.ITALIC);
	}

	private static Component nl() {
		return Component.literal("\n");
	}

	/** A picture of the pet: its spawn egg. */
	private static Component egg(Species s) {
		return item(s.mobId() + "_spawn_egg");
	}

	private static Component item(String texture) {
		return sprite("items", Identifier.withDefaultNamespace("item/" + texture));
	}

	private static Component block(String texture) {
		return sprite("blocks", Identifier.withDefaultNamespace("block/" + texture));
	}

	/** One of the mod's own item pictures. */
	private static Component mod(String texture) {
		return sprite("items", Identifier.fromNamespaceAndPath("bloomcraft", "item/" + texture));
	}

	/** A picture. White, because text colour tints pictures (the book's brown ink turned them into silhouettes). */
	private static Component sprite(String atlas, Identifier texture) {
		return Component.object(new AtlasSprite(Identifier.withDefaultNamespace(atlas), texture)).withColor(0xFFFFFF);
	}
}
