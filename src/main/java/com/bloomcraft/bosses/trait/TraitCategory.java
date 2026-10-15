package com.bloomcraft.bosses.trait;

public enum TraitCategory {
	OFFENSE("Offense", 0xAA0000),
	DEFENSE("Defense", 0x0000AA),
	MOVEMENT("Movement", 0x00AA00),
	CONTROL("Control", 0x00AAAA),
	SPECIAL("Special", 0xAA00AA);

	private final String displayName;
	private final int bookRgb;

	TraitCategory(String displayName, int bookRgb) {
		this.displayName = displayName;
		this.bookRgb = bookRgb;
	}

	public String displayName() {
		return displayName;
	}

	public int bookRgb() {
		return bookRgb;
	}
}
