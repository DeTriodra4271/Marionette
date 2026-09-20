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
 * <p>Use-clicks are peeked at the <em>start</em> of the tick, before
 * vanilla's own click handling drains that same counter to actually place
 * the block — reading it later (e.g. at tick end) would usually see 0
 * regardless of how many clicks really happened that tick.
 */
public class Recorder {
	private final List<Frame> frames = new ArrayList<>();
	private boolean recording;
	private double initialVelocityX;
	private double initialVelocityY;
	private double initialVelocityZ;
	private int pendingUseClicks;

	public void start(LocalPlayer player) {
		frames.clear();
		recording = true;
		pendingUseClicks = 0;
		initialVelocityX = player.getDeltaMovement().x;
		initialVelocityY = player.getDeltaMovement().y;
		initialVelocityZ = player.getDeltaMovement().z;
	}

	public Recording stop(String name) {
		recording = false;
		return new Recording(name, new ArrayList<>(frames), initialVelocityX, initialVelocityY, initialVelocityZ);
	}

	public boolean isRecording() {
		return recording;
	}

	public void tickStart(Minecraft client) {
		if (!recording) {
			return;
		}
		pendingUseClicks = clickCount(client.options.keyUse);
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
		frames.add(new Frame(
				isDown(options.keyUp),
				isDown(options.keyDown),
				isDown(options.keyLeft),
				isDown(options.keyRight),
				isDown(options.keyJump),
				isDown(options.keyShift),
				isDown(options.keySprint),
				isDown(options.keyAttack),
				pendingUseClicks,
				isDown(options.keySwapOffhand),
				isDown(options.keyDrop),
				player.getYRot(),
				player.getXRot(),
				player.getInventory().getSelectedSlot()
		));
		pendingUseClicks = 0;
	}

	private static boolean isDown(KeyMapping mapping) {
		return ((KeyMappingAccessor) (Object) mapping).marionette$isDown();
	}

	private static int clickCount(KeyMapping mapping) {
		return ((KeyMappingAccessor) (Object) mapping).marionette$getClickCount();
	}
}
