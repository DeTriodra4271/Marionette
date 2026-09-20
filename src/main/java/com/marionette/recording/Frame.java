package com.marionette.recording;

/**
 * One tick's worth of captured (or replayed) player input: which movement
 * keys were held, look direction, attack (a held button, for mining), how
 * many times use was clicked this tick, and the selected hotbar slot.
 *
 * <p>This is an input recording, not a position recording — playback drives
 * the same keys and lets the engine's own physics produce the motion, the
 * same way a real key press would. Directly teleporting the player to a
 * recorded position each tick was tried and rejected: it fights gravity and
 * momentum every tick (visible as jitter, worst during jumps), and a build
 * only needs to look the same, not have literally identical coordinates.
 *
 * <p>{@code useClicks} is a count rather than a boolean because use is a
 * discrete per-click action (each click places one block), and clicking
 * faster than 20 times a second means multiple real clicks can land inside
 * a single tick — a boolean "held this tick" flag would silently merge or
 * drop them.
 *
 * <p>{@code gui} holds the inventory actions (container clicks, drops, screens
 * opening and closing) that happened just before this tick; null when there
 * were none, and in takes saved before this existed.
 */
public record Frame(
		boolean forward,
		boolean back,
		boolean left,
		boolean right,
		boolean jump,
		boolean sneak,
		boolean sprint,
		boolean attack,
		int useClicks,
		boolean swapHands,
		boolean drop,
		float yaw,
		float pitch,
		int selectedSlot,
		java.util.List<GuiEvent> gui
) {
	public Frame(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak,
			boolean sprint, boolean attack, int useClicks, boolean swapHands, boolean drop,
			float yaw, float pitch, int selectedSlot) {
		this(forward, back, left, right, jump, sneak, sprint, attack, useClicks, swapHands, drop,
				yaw, pitch, selectedSlot, null);
	}
}
