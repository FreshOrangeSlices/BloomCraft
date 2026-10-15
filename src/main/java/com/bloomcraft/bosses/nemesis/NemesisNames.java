package com.bloomcraft.bosses.nemesis;

import com.bloomcraft.core.Rng;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Personal names for Nemeses, picked to fit the mob. No two Nemeses in a world share a name: when a mob's list
 * is used up, a new name is built from syllables in the same style.
 */
public final class NemesisNames {

	private static final Map<EntityType<?>, List<String>> NAMES = new HashMap<>();

	static {
		put(EntityTypes.ZOMBIE, "Gorrath", "Rotgut", "Mawkin", "Grubb", "Sallowjaw", "Hollowmere");
		put(EntityTypes.HUSK, "Dustmaw", "Sirocco", "Parchjaw", "Duneshade", "Sandrot", "Old Khamsin");
		put(EntityTypes.DROWNED, "Brinewell", "Gloam", "Saltbones", "Kelpgrin", "Undertow", "Drownmother");
		put(EntityTypes.ZOMBIE_VILLAGER, "Hagrim", "Old Mott", "Brother Mire", "Grimsby", "Hollow Ezra", "Ashwick");
		put(EntityTypes.SKELETON, "Scrawl", "Rattlebone", "Ossian", "Marrowmind", "Clatter", "Sliver");
		put(EntityTypes.STRAY, "Rimeshank", "Frostmarrow", "Hoarfang", "Shiverbone", "Glacis", "Wintermute");
		put(EntityTypes.BOGGED, "Mirewick", "Sporegrin", "Fenrattle", "Moldmarrow", "Bogsworth", "Rotroot");
		put(EntityTypes.PARCHED, "Sunsear", "Cinderbone", "Ashmarrow", "Scorchwhistle", "Drybone Dax", "Mirage");
		put(EntityTypes.WITHER_SKELETON, "Charnel", "Vexmourn", "Soot King", "Blackmarrow", "Grimshard", "Coalheart");
		put(EntityTypes.CREEPER, "Hissfang", "Fusewick", "Sizzlegrin", "Cinderhush", "Thunderwick", "Boomwhisper");
		put(EntityTypes.SPIDER, "Skitterfang", "Vashra", "Eightfold", "Silkshade", "Venna", "Old Widow");
		put(EntityTypes.CAVE_SPIDER, "Needlefang", "Venomweft", "Skrit", "Gloomspinner", "Bitterweb", "Nyx");
		put(EntityTypes.ENDERMAN, "Voidgaze", "Starless", "Hushstep", "Nullwalker", "Eclipse", "Long Shadow");
		put(EntityTypes.ENDERMITE, "Voidtick", "Blip", "Speck", "Chitter", "Pip");
		put(EntityTypes.WITCH, "Mother Hemlock", "Granny Grimbrew", "Nettle", "Old Wyrt", "Belladonna", "Auntie Bane");
		put(EntityTypes.PILLAGER, "Captain Raske", "Crowmark", "Quarrel", "Harrow", "Bolt", "Fletch");
		put(EntityTypes.VINDICATOR, "Johnny", "Grudge", "Varn", "Hatchet", "Cleaver", "Brother Axe");
		put(EntityTypes.EVOKER, "Fangcaller", "Maledict", "Oracle Vex", "Thorne", "Hexmouth", "Grand Malice");
		put(EntityTypes.ILLUSIONER, "Mirage", "Twofold", "Glimmer", "Hollowface", "Phantasm");
		put(EntityTypes.RAVAGER, "Ironhide", "Gorehorn", "Bulldoze", "Stampede", "Rampart", "Old Thunder");
		put(EntityTypes.BLAZE, "Pyre", "Ember Rook", "Scorchling", "Kindlewrath", "Flarecrown", "Ashfall");
		put(EntityTypes.GHAST, "Wail", "Weepwhisper", "Sorrowcloud", "Moan", "Teardrift", "Grief");
		put(EntityTypes.MAGMA_CUBE, "Slagheart", "Molten Mo", "Cinderblob", "Lavalump", "Searbounce", "Crucible");
		put(EntityTypes.SLIME, "Gloop", "Wobblegut", "Squelch", "Bouncewart", "Mucus Rex", "Jellyhide");
		put(EntityTypes.HOGLIN, "Tuskbreaker", "Gnash", "Bristleback", "Hamhock", "Snortwrath", "Big Ham");
		put(EntityTypes.ZOGLIN, "Rottusk", "Grimbristle", "Snarl", "Witherhock", "Mangetooth");
		put(EntityTypes.PIGLIN, "Goldtooth", "Glint", "Nugget", "Brassjaw", "Baron Bling", "Gildfang");
		put(EntityTypes.PIGLIN_BRUTE, "Kragg", "Goldcrush", "Bastion", "Hammerhock", "Grimgold", "Big Bruiser");
		put(EntityTypes.ZOMBIFIED_PIGLIN, "Rotgild", "Pallor", "Tarnish", "Gildrot", "Mourngold", "Sallow Sow");
		put(EntityTypes.PHANTOM, "Insomnia", "Nightrend", "Skyrot", "Dreamshade", "Moonwing", "Lullaby");
		put(EntityTypes.BREEZE, "Gale", "Whirl", "Tempest", "Zephyr", "Gusto", "Squall");
		put(EntityTypes.GUARDIAN, "Spike", "Tidewatch", "Prismfin", "Glare", "Sentinel");
		put(EntityTypes.ELDER_GUARDIAN, "Fathom", "Deepgaze", "Tidelord", "Brinewatch", "Leviathan");
		put(EntityTypes.SILVERFISH, "Nibble", "Gnawstone", "Burrow", "Itch", "Scuttle");
		put(EntityTypes.VEX, "Tink", "Jinx", "Hexling", "Prick", "Sting");
		put(EntityTypes.WARDEN, "Hush", "Sculkheart", "Dirge", "Echo", "Silence");
		put(EntityTypes.WITHER, "Ruin", "Blight", "Mortis", "Nevermore", "Desolation");
		put(EntityTypes.SHULKER, "Shellshock", "Lidlock", "Purpur", "Hover", "Clamp");
	}

	private static final String[] START = {"Grim", "Mor", "Vex", "Rot", "Skar", "Dread", "Gloom", "Ash", "Kra",
		"Vor", "Hex", "Mal", "Thorn", "Bane", "Wrath"};
	private static final String[] END = {"maw", "gash", "fang", "mourn", "tooth", "grin", "shade", "rend", "wick",
		"hollow", "claw", "bite", "scar", "spite", "gloom"};

	private NemesisNames() {
	}

	private static void put(EntityType<?> type, String... names) {
		NAMES.put(type, List.of(names));
	}

	/** A name that fits the mob and isn't used by any other Nemesis ({@code taken} is lower-case). */
	public static String pick(EntityType<?> type, Set<String> taken) {
		List<String> free = new ArrayList<>();
		for (String name : NAMES.getOrDefault(type, List.of())) {
			if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
				free.add(name);
			}
		}
		if (!free.isEmpty()) {
			return Rng.pick(free);
		}
		for (int attempt = 0; attempt < 200; attempt++) {
			String name = START[Rng.between(0, START.length - 1)] + END[Rng.between(0, END.length - 1)];
			if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
				return name;
			}
		}
		int n = 2;
		String base = START[0] + END[0];
		while (taken.contains((base + " " + n).toLowerCase(Locale.ROOT))) {
			n++;
		}
		return base + " " + n;
	}
}
