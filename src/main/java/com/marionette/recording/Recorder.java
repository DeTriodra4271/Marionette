package com.marionette.recording;

import com.marionette.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures the local player's held keys, look direction, and selected slot
 * on every client tick while active.
 *
 * <p>Use-clicks are counted as they happen, by hooking the game's own
 * {@code startUseItem} (see {@code MinecraftUseMixin}). That catches both
 * discrete clicks and the automatic repeats while the key is held, which
 * never touch the key's click counter.
 */
public class Recorder {
	private final List<Frame> frames = new ArrayList<>();
	private boolean recording;
	private double initialVelocityX;
	private double initialVelocityY;
	private double initialVelocityZ;
	private double startOffsetX;
	private double startOffsetZ;
	private static volatile int useAttempts;
	private static volatile boolean capturing;
	private static final List<GuiEvent> pendingGui = new ArrayList<>();
	private boolean inventoryScreenOpen;

	/** Called from the game's use-item hook; only counted while a recording is being made. */
	public static void noteUseAttempt() {
		useAttempts++;
	}

	public static boolean isCapturing() {
		return capturing;
	}

	/** Queues an inventory action; it lands on the next recorded tick. */
	public static void note(GuiEvent event) {
		synchronized (pendingGui) {
			pendingGui.add(event);
		}
	}

	private static List<GuiEvent> drainGui() {
		synchronized (pendingGui) {
			if (pendingGui.isEmpty()) {
				return null;
			}
			List<GuiEvent> events = new ArrayList<>(pendingGui);
			pendingGui.clear();
			return events;
		}
	}

	public void start(LocalPlayer player) {
		frames.clear();
		recording = true;
		capturing = true;
		inventoryScreenOpen = false;
		drainGui();
		useAttempts = 0;
		initialVelocityX = player.getDeltaMovement().x;
		initialVelocityY = player.getDeltaMovement().y;
		initialVelocityZ = player.getDeltaMovement().z;
		startOffsetX = player.getX() - Math.floor(player.getX());
		startOffsetZ = player.getZ() - Math.floor(player.getZ());
	}

	public Recording stop(String name) {
		recording = false;
		capturing = false;
		drainGui();
		return new Recording(name, new ArrayList<>(frames), initialVelocityX, initialVelocityY, initialVelocityZ,
				startOffsetX, startOffsetZ);
	}

	public boolean isRecording() {
		return recording;
	}

	public void tickStart(Minecraft client) {
		if (!recording) {
			return;
		}
		useAttempts = 0;
	}

	public void tick(Minecraft client) {
		if (!recording) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Options options = client.options;
		// The survival inventory opens locally with no packet to hook, so
		// spot it by the screen appearing.
		boolean inventoryNow = client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
		if (inventoryNow && !inventoryScreenOpen) {
			note(new GuiEvent(GuiEvent.OPEN_INVENTORY, null, 0, 0, 0));
		}
		inventoryScreenOpen = inventoryNow;
		frames.add(new Frame(
				isDown(options.keyUp),
				isDown(options.keyDown),
				isDown(options.keyLeft),
				isDown(options.keyRight),
				isDown(options.keyJump),
				isDown(options.keyShift),
				isDown(options.keySprint),
				isDown(options.keyAttack),
				useAttempts,
				isDown(options.keySwapOffhand),
				false,
				player.getYRot(),
				player.getXRot(),
				player.getInventory().getSelectedSlot(),
				drainGui()
		));
		useAttempts = 0;
	}

	private static boolean isDown(KeyMapping mapping) {
		return ((KeyMappingAccessor) (Object) mapping).marionette$isDown();
	}
}
