package com.marionette.recording;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * One inventory-side action that happened between two ticks: a click inside
 * a container, an item drop, or a screen opening or closing.
 *
 * <p>Container clicks carry the kind of menu they happened in
 * ({@code menu}), because the numeric container id the server hands out
 * differs between runs. On replay the click is only sent once a menu of that
 * kind is actually open, so a chest that takes a moment to open doesn't eat
 * the first clicks.
 *
 * @param kind   {@link #CLICK}, {@link #DROP}, {@link #OPEN_INVENTORY} or {@link #CLOSE}
 * @param menu   menu kind for a click, see {@link #menuKey}; null otherwise
 * @param slot   container slot for a click
 * @param button mouse button / hotbar key for a click; 1 for a full-stack drop
 * @param input  ordinal of the {@code ContainerInput} (pickup, quick move, throw, ...)
 */
public record GuiEvent(String kind, String menu, int slot, int button, int input) {
	public static final String CLICK = "click";
	public static final String DROP = "drop";
	public static final String OPEN_INVENTORY = "inventory";
	public static final String CLOSE = "close";

	/** A stable name for the menu a player currently has open. */
	public static String menuKey(Player player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu == null || menu == player.inventoryMenu) {
			return "inventory";
		}
		try {
			var key = BuiltInRegistries.MENU.getKey(menu.getType());
			return key == null ? "unknown" : key.toString();
		} catch (RuntimeException e) {
			return "unknown";
		}
	}
}
