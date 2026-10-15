package com.bloomcraft.bosses.guide;

import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.EmpowermentStat;
import com.bloomcraft.bosses.config.MobCategory;
import com.bloomcraft.bosses.config.RankSettings;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.nemesis.NemesisRecord;
import com.bloomcraft.bosses.relic.RelicEffect;
import com.bloomcraft.bosses.trait.BossTrait;
import com.bloomcraft.bosses.trait.Synergies;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the "Boss Hunter's Compendium": two written books that explain everything the bosses part of the mod adds.
 * They're generated from the live config, so every number matches the world's actual settings, and the second ends
 * with the reader's own boss-hunting record.
 */
public final class GuideBook {

	private static final int INK = 0x000000;
	private static final int SOFT = 0x555555;
	private static final int TITLE = 0x7A1F1F;
	private static final int RELIC = 0xAA00AA;
	private static final int CURSE = 0xAA0000;
	private static final int EMPOWER = 0x00AAAA;
	private static final int LINK = 0x0000AA;
	private static final int MAX_PAGES = 100;

	private final Bosses bosses;

	public GuideBook(Bosses bosses) {
		this.bosses = bosses;
	}

	/** Gives both volumes. */
	public void give(ServerPlayer player) {
		for (ItemStack book : create(player)) {
			if (!player.getInventory().add(book)) {
				player.spawnAtLocation(player.level(), book);
			}
		}
		player.sendSystemMessage(bosses.settings().messages.prefixed("guide-received", Map.of()));
	}

	/** I. Bosses (how they appear, ranks, traits, how they fight) and II. Loot &amp; Nemesis. */
	public List<ItemStack> create(ServerPlayer reader) {
		return List.of(volumeOne(), volumeTwo(reader));
	}

	/** Volume 1 or 2 (anything else: 1). */
	public ItemStack volume(ServerPlayer reader, int n) {
		return n == 2 ? volumeTwo(reader) : volumeOne();
	}

	/**
	 * Opens a volume for reading without putting it in the inventory: the client briefly sees it in the held slot,
	 * opens it, and gets the real item back.
	 */
	public void open(ServerPlayer player, int volume) {
		ItemStack book = volume(player, volume);
		int slot = player.getInventory().getSelectedSlot();
		int menuSlot = 36 + slot; // hotbar slots in the player's own inventory screen
		var menu = player.inventoryMenu;
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), menuSlot, book));
		player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), menuSlot,
			player.getInventory().getItem(slot).copy()));
	}

	private static MutableComponent c(String text, int rgb) {
		return Component.literal(text).withColor(rgb);
	}

	private ItemStack volumeOne() {
		BossSettings s = bosses.settings();
		BookWriter w = new BookWriter();
		Map<String, Integer> sections = new LinkedHashMap<>();

		// ---------------- How bosses appear ----------------
		sections.put("How Bosses Appear", w.currentPage());
		w.heading("How Bosses Appear", TITLE);
		w.text("Any hostile mob has a " + Text.num(s.spawnChance) + "% chance to spawn as a boss.", INK);
		w.blank();
		w.add(c("★★★★★ Legendary Undying Zombie", BossRank.GOLD.bookRgb()).withStyle(ChatFormatting.BOLD), true);
		w.blank();
		w.text("Top ranks are very rare in the wild; the Nether breeds tougher ones.", INK);
		if (s.glowWithin > 0) {
			w.text("Bosses glow within " + Math.round(s.glowWithin) + " blocks.", SOFT);
		}
		if (s.despawnAfterKills > 0) {
			w.text("A boss that kills " + s.despawnAfterKills + " players leaves.", SOFT);
		}
		w.newPage();

		// ---------------- Ranks ----------------
		sections.put("Ranks", w.currentPage());
		w.heading("Ranks", TITLE);
		for (BossRank rank : BossRank.values()) {
			RankSettings rs = s.rank(rank);
			w.reserve(3);
			w.add(c(rs.title(), rank.bookRgb()).withStyle(ChatFormatting.BOLD), true);
			String traits = rs.traits().min() == rs.traits().max() ? String.valueOf(rs.traits().min())
				: rs.traits().min() + "-" + rs.traits().max();
			w.text("Health x" + Text.num(rs.stats().health()) + ", damage x" + Text.num(rs.stats().damage())
				+ ", " + traits + " trait" + (rs.traits().max() == 1 ? "" : "s"), INK);
			if (!rank.natural()) {
				w.text("Only made with Boss Souls.", SOFT);
			}
		}
		w.newPage();

		// ---------------- Rank odds ----------------
		sections.put("Rank Odds", w.currentPage());
		w.heading("Rank Odds", TITLE);
		MutableComponent legend = Component.empty();
		for (BossRank rank : BossRank.values()) {
			if (!rank.natural()) {
				continue;
			}
			if (rank.ordinal() > 0) {
				legend.append(c("/", SOFT));
			}
			legend.append(c(Text.pretty(rank.name()), rank.bookRgb()));
		}
		w.add(legend, false);
		w.blank();
		for (MobCategory category : s.categories) {
			if (!category.enabled()) {
				continue;
			}
			w.reserve(2);
			w.add(c(category.displayName(), INK).withStyle(ChatFormatting.BOLD), true);
			MutableComponent odds = Component.empty();
			for (BossRank rank : BossRank.values()) {
				if (!rank.natural()) {
					continue;
				}
				if (rank.ordinal() > 0) {
					odds.append(c("/", SOFT));
				}
				odds.append(c(Text.num(category.percent(rank)), rank.bookRgb()));
			}
			w.add(odds.append(c(" %", SOFT)), false);
		}
		w.newPage();

		// ---------------- Traits ----------------
		sections.put("Traits", w.currentPage());
		w.heading("Traits", TITLE);
		w.text("Traits change how a boss fights. Its first trait names it: a Vampiric zombie is a Ravenous Zombie.", INK);
		for (TraitCategory category : TraitCategory.values()) {
			List<BossTrait> traits = new ArrayList<>();
			for (BossTrait t : bosses.traits().enabledTraits()) {
				if (t.category() == category) {
					traits.add(t);
				}
			}
			if (traits.isEmpty()) {
				continue;
			}
			w.newPage();
			w.heading(category.displayName(), category.bookRgb());
			for (BossTrait t : traits) {
				w.reserve(3);
				w.add(c(t.displayName(), category.bookRgb()).withStyle(ChatFormatting.BOLD), true);
				w.text(t.description(), INK);
				w.blank();
			}
		}
		w.newPage();

		// ---------------- Synergies + tells ----------------
		sections.put("Synergies & Tells", w.currentPage());
		w.heading("Synergies", TITLE);
		w.text("Some trait pairs fuse and hit harder:", INK);
		for (Synergies.Synergy syn : Synergies.ALL) {
			BossTrait a = bosses.traits().get(syn.first());
			BossTrait b = bosses.traits().get(syn.second());
			if (a == null || b == null || !bosses.traits().isEnabled(a) || !bosses.traits().isEnabled(b)) {
				continue;
			}
			w.add(c(syn.title() + ": ", TITLE).withStyle(ChatFormatting.BOLD)
				.append(c(a.displayName() + " + " + b.displayName(), SOFT).withStyle(s2 -> s2.withBold(false))), false);
		}
		w.blank();
		w.heading("Tells", TITLE);
		w.text("Big attacks give a warning. Move!", INK);
		w.entry("Quaking", TITLE, "a red ring", INK);
		w.entry("Shadowed", TITLE, "gathering smoke", INK);
		w.entry("Gravitic", TITLE, "a violet ring", INK);
		w.entry("Leaping", TITLE, "a crouch", INK);
		w.entry("Charging", TITLE, "a roar", INK);
		w.newPage();

		// ---------------- Last Stand, threat ----------------
		sections.put("Last Stand & Threat", w.currentPage());
		w.heading("Last Stand", TITLE);
		w.text("Purple and Gold bosses (and every Nemesis) fight harder once, at "
			+ Text.num(s.lastStandHealthPercent) + "% health.", INK);
		w.blank();
		w.heading("Threat", TITLE);
		if (s.threatEnabled) {
			w.text("Wearing or holding Boss Gear makes the bosses you fight tougher, and their loot better.", INK);
		}
		if (s.overflowArmor) {
			w.blank();
			w.text("Armor over " + Math.round(s.overflowArmorFrom) + ": each extra point is "
				+ Text.num(s.overflowArmorPerPoint) + "% less damage from bosses.", INK);
		}
		w.blank();
		w.text("Bosses can't be trapped in boats, minecarts or leads.", SOFT);

		return book("Boss Compendium I", "Book I: Bosses", new ArrayList<>(sections.entrySet()), w.finish());
	}

	private ItemStack volumeTwo(ServerPlayer reader) {
		BossSettings s = bosses.settings();
		BookWriter w = new BookWriter();
		Map<String, Integer> sections = new LinkedHashMap<>();

		// ---------------- Rewards ----------------
		sections.put("Rewards", w.currentPage());
		w.heading("Rewards", TITLE);
		w.text("Each drop rolls on its own:", SOFT);
		for (BossRank rank : BossRank.values()) {
			RankSettings.Rewards r = s.rank(rank).rewards();
			w.reserve(2);
			w.add(c(s.rank(rank).name(), rank.bookRgb()).withStyle(ChatFormatting.BOLD), true);
			w.text("Gear " + Text.num(r.bossGear()) + "  Rune " + Text.num(r.empowerment()) + "  Relic "
				+ Text.num(r.relic()) + (r.extraRelics().isEmpty() ? "" : "+") + "%", INK);
		}
		w.newPage();

		// ---------------- Boss Gear ----------------
		sections.put("Boss Gear", w.currentPage());
		w.heading("Boss Gear", TITLE);
		w.add(c("★★★★ Nightmare Diamond Sword", BossRank.PURPLE.bookRgb()), false);
		w.blank();
		w.text("Higher ranks: better materials and enchantments. Weak bosses drop armor and tools, strong ones"
			+ " weapons.", INK);
		if (s.overlappingEnchantsFrom != null) {
			w.blank();
			w.text(s.rank(s.overlappingEnchantsFrom).name() + "+ gear can mix enchantments like Sharpness and Smite.", SOFT);
		}
		w.newPage();

		// ---------------- Empowerment ----------------
		sections.put("Runes", w.currentPage());
		w.heading("Runes", TITLE);
		w.text("A rune adds a permanent stat to an item. Click it onto the item in your inventory. Up to "
			+ s.empowermentMaxPerItem + " per item.", INK);
		w.blank();
		for (EmpowermentStat stat : s.empowermentStats) {
			w.reserve(2);
			w.add(c(stat.displayName(), EMPOWER).withStyle(ChatFormatting.BOLD), true);
			w.text(stat.fitsText(), SOFT);
		}
		w.newPage();

		// ---------------- Relics ----------------
		sections.put("Relics", w.currentPage());
		w.heading("Relics", TITLE);
		w.text("Click a relic onto gear to bind a power to it, for good. " + Text.num(s.corruptionChance)
			+ "% carry a curse too" + (s.revealCorruption ? "." : ": you find out when you bind it."), INK);
		w.blank();
		for (RelicEffect relic : bosses.relics().enabledEffects(false)) {
			w.reserve(3);
			w.add(c("✦ " + relic.displayName(), RELIC).withStyle(ChatFormatting.BOLD), true);
			w.text(relic.description(), INK);
		}
		w.newPage();

		// ---------------- Curses ----------------
		sections.put("Curses", w.currentPage());
		w.heading("Curses", CURSE);
		w.text("Cursed armor can't be taken off until you die.", SOFT);
		w.blank();
		for (RelicEffect curse : bosses.relics().enabledEffects(true)) {
			w.reserve(3);
			w.add(c("☠ " + curse.displayName(), CURSE).withStyle(ChatFormatting.BOLD), true);
			w.text(curse.description(), INK);
		}
		w.newPage();

		// ---------------- The Hunt ----------------
		sections.put("The Hunt", w.currentPage());
		w.heading("The Hunt", TITLE);
		if (s.compassEnabled) {
			w.entry("Hunter's Compass", TITLE, "points to the nearest boss." + (s.compassRecipe
				? " Craft: Compass + Eye of Ender + Bone." : "") + " Click gear onto it to get a Soul.", INK);
			w.blank();
		}
		if (s.soulsEnabled) {
			w.entry("Boss Souls", TITLE, "offer one to a boss and it rises in rank.", INK);
			w.blank();
		}
		if (s.totemEnabled) {
			w.entry("Boss Totem", TITLE, "use it to call a boss to fight.", INK);
			w.blank();
		}
		if (s.escalationEnabled) {
			w.entry("Escalation", TITLE, "kill " + s.escalationKills + " bosses in a day and worse ones come.", INK);
			w.blank();
		}
		if (s.promotionEnabled) {
			w.entry("Ascendant", BossRank.ASCENDANT.bookRgb(), "raise a boss past Legendary: the top rank.", INK);
		}
		if (s.waystonesEnabled) {
			w.blank();
			w.entry("Waystones", TITLE, "Ascendants drop them. Place one to join the free travel network.", INK);
		}
		w.newPage();

		// ---------------- Nemesis ----------------
		if (s.nemesisEnabled) {
			sections.put("Nemesis", w.currentPage());
			w.heading("Nemesis", CURSE);
			w.text("A boss that kills you (or that you flee from) may become your Nemesis. It leaves, grows"
				+ " stronger, and returns after " + Text.num(s.nemesisReturnDays) + " days.", INK);
			w.blank();
			w.text("It learns how you fight. Mix it up.", INK);
			w.blank();
			w.text("When it kills you, you get its Challenge: use it to call it now, or meet its Terms of Surrender"
				+ " to end the feud.", INK);
			w.blank();
			w.text("Kill it for great loot and a statue.", SOFT);
			w.newPage();
		}

		// ---------------- Commands ----------------
		sections.put("Commands", w.currentPage());
		w.heading("Commands", TITLE);
		command(w, "/bosses inspect", "Everything on your held item.");
		command(w, "/bosses stats", "Your record.");
		command(w, "/bosses nemesis", "Your Nemeses.");
		command(w, "/bosses guide", "A copy of these books.");
		w.newPage();

		// ---------------- Personal record ----------------
		sections.put("Your Record", w.currentPage());
		w.heading("Your Record", TITLE);
		int total = 0;
		for (BossRank rank : BossRank.values()) {
			int kills = Data.integer(reader, BossKeys.kills(rank), 0);
			total += kills;
			w.add(c(s.rank(rank).title() + ": ", rank.bookRgb()).append(c(String.valueOf(kills), INK)), false);
		}
		w.blank();
		w.text("Total: " + total, INK);
		w.text("Relics bound: " + Data.integer(reader, BossKeys.RELICS_BOUND, 0), INK);
		if (s.nemesisEnabled) {
			List<NemesisRecord> mine = bosses.nemesis().forOwner(reader.getUUID());
			w.blank();
			w.add(c("Nemeses: " + mine.size(), CURSE).withStyle(ChatFormatting.BOLD), true);
			for (NemesisRecord r : mine) {
				w.reserve(2);
				w.text(bosses.nemesis().plainName(r) + " (Lv " + r.level + ")", INK);
				w.text(bosses.nemesis().describeReturn(r), SOFT);
			}
		}

		return book("Boss Compendium II", "Book II: Loot & Nemesis", new ArrayList<>(sections.entrySet()), w.finish());
	}

	private static ItemStack book(String title, @Nullable String volume, List<Map.Entry<String, Integer>> sections,
								  List<Component> body) {
		int contentsCount = contentsPages(sections, 0).size();
		int offset = 1 + contentsCount; // title page + contents pages come before the body
		List<Component> pages = new ArrayList<>();
		pages.add(titlePage(volume));
		pages.addAll(contentsPages(sections, offset));
		pages.addAll(body);
		List<Filterable<Component>> content = new ArrayList<>();
		for (Component page : pages.subList(0, Math.min(MAX_PAGES, pages.size()))) {
			content.add(Filterable.passThrough(page));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT,
			new WrittenBookContent(Filterable.passThrough(title), "BloomCraft", 0, content, true));
		BossItems.edit(book, tag -> tag.putString("kind", "guide"));
		return book;
	}

	/** The contents list, spread over as many pages as needed (each line is a clickable link). */
	private static List<Component> contentsPages(List<Map.Entry<String, Integer>> sections, int offset) {
		List<Component> pages = new ArrayList<>();
		MutableComponent b = Component.empty()
			.append(c("Contents", TITLE).withStyle(ChatFormatting.BOLD)).append("\n\n");
		int used = 2;
		for (Map.Entry<String, Integer> e : sections) {
			String label = "▸ " + e.getKey();
			int lines = BookWriter.lineCount(label, false);
			if (used + lines > BookWriter.PAGE_LINES) {
				pages.add(b);
				b = Component.empty();
				used = 0;
			}
			int page = e.getValue() + offset + 1;
			b.append(c(label, LINK).withStyle(st -> st.withClickEvent(new ClickEvent.ChangePage(page))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + page)))));
			b.append("\n");
			used += lines;
		}
		pages.add(b);
		return pages;
	}

	private static Component titlePage(@Nullable String volume) {
		return Component.empty()
			.append(c(volume == null ? "" : volume, SOFT)).append("\n")
			.append(c("   BLOOMCRAFT", TITLE).withStyle(ChatFormatting.BOLD)).append("\n")
			.append(c("      BOSSES", TITLE).withStyle(ChatFormatting.BOLD)).append("\n\n")
			.append(c("Boss Hunter's", SOFT).withStyle(ChatFormatting.ITALIC)).append("\n")
			.append(c("Compendium", SOFT).withStyle(ChatFormatting.ITALIC)).append("\n\n")
			.append(c("★", BossRank.GRAY.bookRgb()))
			.append(c("★", BossRank.GREEN.bookRgb()))
			.append(c("★", BossRank.RED.bookRgb()))
			.append(c("★", BossRank.PURPLE.bookRgb()))
			.append(c("★", BossRank.GOLD.bookRgb()))
			.append("\n\n")
			.append(c("Ordinary mobs don't always stay ordinary.", INK)).append("\n\n")
			.append(c("Turn the page →", SOFT));
	}

	private static void command(BookWriter w, String command, String description) {
		w.reserve(3);
		w.add(c(command, LINK), false);
		w.text(description, SOFT);
		w.blank();
	}
}
