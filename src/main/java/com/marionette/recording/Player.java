package com.marionette.recording;

import com.marionette.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Replays a {@link Recording}, one frame per client tick.
 *
 * <p>Movement is driven by pressing the same keys that were recorded and
 * letting the engine's own physics move the player — the same as it would
 * for a real key press, jump arcs included. Rotation is set directly since
 * there's no key-based equivalent for "mouse moved this far."
 *
 * <p>Interactions (breaking/placing) are done as our own raycast against
 * the player's actual live position and the recorded angle — passed
 * explicitly into {@link LocalPlayer#calculateViewVector(float, float)}
 * rather than read off the entity's live rotation — instead of toggling
 * the attack/use key mappings and hoping the engine's own crosshair target
 * (which only refreshes once per rendered frame, not per tick) happens to
 * line up with what was recorded.
 *
 * <p>Everything for a frame — keys, rotation, then clicks — is applied at
 * the <em>start</em> of its tick, matching what the recorder saw (mouse
 * turns land between ticks, so a whole tick sees one angle). The local
 * player's camera doesn't interpolate rotation on its own, so between
 * ticks {@code LocalPlayerViewMixin} feeds it a smooth curve through the
 * recorded angles; see {@link #viewYaw(float)}.
 */
public class Player {
	/** The instance currently replaying, read by the camera mixin. */
	private static volatile Player active;

	private Recording recording;
	private int index;
	private boolean playing;
	private LoopMode loopMode = LoopMode.OFF;
	private double startX;
	private double startY;
	private double startZ;
	private boolean skipMenuClicks;
	private Frame pendingInteractionFrame;

	private boolean wasAttacking;
	private boolean wasSwapping;
	private boolean wasDropping;
	private BlockPos miningPos;
	private Direction miningDirection;
	private int guiWaitTicks;

	public void start(Recording recording, LocalPlayer player, LoopMode loopMode) {
		this.recording = recording;
		this.index = 0;
		active = this;
		this.playing = true;
		this.loopMode = loopMode;
		this.skipMenuClicks = false;
		this.pendingInteractionFrame = null;
		this.guiWaitTicks = 0;
		this.wasAttacking = false;
		this.wasSwapping = false;
		this.wasDropping = false;
		this.miningPos = null;
		this.miningDirection = null;

		resetForStart(player);
		startX = player.getX();
		startY = player.getY();
		startZ = player.getZ();
	}

	/**
	 * Moves X/Z to the same spot within the current block that recording
	 * started from (recorded at 64.458 and replayed while standing anywhere
	 * on block 200 gives 200.458), before the first frame runs. Once, not
	 * every tick, so it doesn't fight physics. Movement is purely
	 * input-driven and relies on physics being deterministic given the same
	 * starting conditions, so matching the sub-block position removes a
	 * source of drift between the recording and its replay. Takes without a
	 * recorded offset use the block centre. Y is left alone since it's
	 * already exact once standing on solid ground.
	 */
	private void resetForStart(LocalPlayer player) {
		double offsetX = recording.startOffsetX() != null ? recording.startOffsetX() : 0.5;
		double offsetZ = recording.startOffsetZ() != null ? recording.startOffsetZ() : 0.5;
		player.setPos(Math.floor(player.getX()) + offsetX, player.getY(), Math.floor(player.getZ()) + offsetZ);
		player.setDeltaMovement(
				recording.initialVelocityX(),
				recording.initialVelocityY(),
				recording.initialVelocityZ()
		);
	}

	public void stop() {
		if (!playing) {
			return;
		}
		playing = false;
		if (active == this) {
			active = null;
		}
		pendingInteractionFrame = null;
		if (wasAttacking && Minecraft.getInstance().gameMode != null) {
			Minecraft.getInstance().gameMode.stopDestroyBlock();
		}
		wasAttacking = false;
		wasSwapping = false;
		wasDropping = false;
		miningPos = null;
		miningDirection = null;
		releaseInputs();
	}

	public boolean isPlaying() {
		return playing;
	}

	public void applyInput(Minecraft client) {
		if (!playing) {
			return;
		}
		if (recording == null || recording.frames().isEmpty()) {
			stop();
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			stop();
			return;
		}
		if (index >= recording.frames().size()) {
			if (loopMode == LoopMode.OFF) {
				stop();
				return;
			}
			index = 0;
			if (loopMode == LoopMode.RETURN_TO_START) {
				player.setPos(startX, startY, startZ);
				player.setDeltaMovement(recording.initialVelocityX(), recording.initialVelocityY(), recording.initialVelocityZ());
			} else {
				resetForStart(player);
			}
			skipMenuClicks = false;
		}

		// A container click can only be sent once that container is open (a
		// chest takes a moment to open after the click that opens it), so hold
		// the timeline here instead of letting the rest of the take run ahead.
		if (waitingForMenu(player, recording.frames().get(index))) {
			releaseInputs();
			return;
		}

		Frame frame = recording.frames().get(index++);

		Options options = client.options;
		setDown(options.keyUp, frame.forward());
		setDown(options.keyDown, frame.back());
		setDown(options.keyLeft, frame.left());
		setDown(options.keyRight, frame.right());
		setDown(options.keyJump, frame.jump());
		setDown(options.keyShift, frame.sneak());
		setDown(options.keySprint, frame.sprint());
		// Negative means "leave the hotbar alone" (timeline-authored takes have no slot data).
		if (frame.selectedSlot() >= 0) {
			player.getInventory().setSelectedSlot(frame.selectedSlot());
		}

		// Rotation goes in at the start of the tick, same as it was seen
		// when recorded (mouse turns land between ticks, so the whole tick
		// saw this angle): movement physics, aim and clicks all agree.
		player.setYRot(frame.yaw());
		player.setXRot(frame.pitch());

		// Clicks and mining run now too, not at the end of the tick: vanilla
		// handles them at the start of the tick, before this tick's movement,
		// so doing them later put every placement a tick behind.
		pendingInteractionFrame = frame;
		applyInteractions(client);
	}

	private static final int MAX_MENU_WAIT_TICKS = 80;

	private boolean waitingForMenu(LocalPlayer player, Frame next) {
		String expected = firstClickMenu(next);
		if (expected == null || expected.equals(GuiEvent.menuKey(player))) {
			guiWaitTicks = 0;
			skipMenuClicks = false;
			return false;
		}
		if (skipMenuClicks) {
			return false;
		}
		// Give up (the chest never opened, e.g. the click missed it) so a bad
		// start can't hang forever. The rest of that container visit is then
		// skipped rather than waited on again for every click.
		if (++guiWaitTicks > MAX_MENU_WAIT_TICKS) {
			guiWaitTicks = 0;
			skipMenuClicks = true;
			return false;
		}
		return true;
	}

	private static String firstClickMenu(Frame frame) {
		if (frame.gui() == null) {
			return null;
		}
		for (GuiEvent event : frame.gui()) {
			if (GuiEvent.CLICK.equals(event.kind())) {
				return event.menu();
			}
		}
		return null;
	}

	private void applyGui(Minecraft client, LocalPlayer player, Frame frame) {
		if (frame.gui() == null) {
			return;
		}
		for (GuiEvent event : frame.gui()) {
			switch (event.kind()) {
				case GuiEvent.CLICK -> {
					ContainerInput[] inputs = ContainerInput.values();
					// Slot -999 / -1 mean "outside the window"; anything else must exist in
					// the menu that is open now, or the game itself would throw.
					int slotCount = player.containerMenu.slots.size();
					if (client.gameMode != null && event.input() >= 0 && event.input() < inputs.length
							&& GuiEvent.menuKey(player).equals(event.menu())
							&& (event.slot() < 0 || event.slot() < slotCount)) {
						client.gameMode.handleContainerInput(player.containerMenu.containerId,
								event.slot(), event.button(), inputs[event.input()], player);
					}
				}
				case GuiEvent.DROP -> {
					if (player.drop(event.button() == 1)) {
						player.swing(InteractionHand.MAIN_HAND);
					}
				}
				case GuiEvent.OPEN_INVENTORY -> {
					if (client.gui.screen() == null) {
						client.setScreenAndShow(new InventoryScreen(player));
					}
				}
				case GuiEvent.CLOSE -> {
					skipMenuClicks = false;
					if (player.containerMenu != player.inventoryMenu || client.gui.screen() != null) {
						player.closeContainer();
					}
				}
				default -> {
				}
			}
		}
	}

	/**
	 * Camera yaw between ticks: a Catmull-Rom curve through the recorded
	 * frames, running from the frame just applied to the next one, so it
	 * lands exactly on that frame when the next tick applies it. Null when
	 * nothing is playing, so the camera falls back to the real rotation.
	 */
	public static Float viewYaw(float partialTick) {
		Player p = active;
		return p == null ? null : p.smoothedRotation(partialTick, true);
	}

	public static Float viewPitch(float partialTick) {
		Player p = active;
		return p == null ? null : p.smoothedRotation(partialTick, false);
	}

	private Float smoothedRotation(float partialTick, boolean yaw) {
		Recording current = recording;
		if (!playing || current == null) {
			return null;
		}
		java.util.List<Frame> frames = current.frames();
		int k = index - 1;
		if (k < 0 || k >= frames.size()) {
			return null;
		}
		float t = Math.max(0f, Math.min(1f, partialTick));
		float a = component(frames.get(Math.max(k - 1, 0)), yaw);
		float b = component(frames.get(k), yaw);
		float c = component(frames.get(Math.min(k + 1, frames.size() - 1)), yaw);
		float d = component(frames.get(Math.min(k + 2, frames.size() - 1)), yaw);
		if (yaw) {
			// unwrap around b so a turn across the -180/180 seam takes the short way
			a = b + net.minecraft.util.Mth.wrapDegrees(a - b);
			c = b + net.minecraft.util.Mth.wrapDegrees(c - b);
			d = c + net.minecraft.util.Mth.wrapDegrees(d - c);
		}
		float t2 = t * t;
		float t3 = t2 * t;
		float value = 0.5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
		return yaw ? value : Math.max(-90f, Math.min(90f, value));
	}

	private static float component(Frame frame, boolean yaw) {
		return yaw ? frame.yaw() : frame.pitch();
	}

	private void applyInteractions(Minecraft client) {
		if (pendingInteractionFrame == null) {
			return;
		}
		Frame frame = pendingInteractionFrame;
		pendingInteractionFrame = null;

		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		applyGui(client, player, frame);

		boolean attacking = frame.attack();
		if (attacking && !wasAttacking) {
			HitResult hit = pick(player, frame);
			if (hit instanceof EntityHitResult entityHit) {
				client.gameMode.attack(player, entityHit.getEntity());
				miningPos = null;
				miningDirection = null;
			} else if (hit.getType() == HitResult.Type.BLOCK) {
				BlockHitResult blockHit = (BlockHitResult) hit;
				miningPos = blockHit.getBlockPos();
				miningDirection = blockHit.getDirection();
				client.gameMode.startDestroyBlock(miningPos, miningDirection);
			} else {
				miningPos = null;
				miningDirection = null;
			}
			// Vanilla always swings on an attack press, hit or miss, block
			// or entity — it's not conditional on the interaction result the
			// way use is.
			player.swing(InteractionHand.MAIN_HAND);
		} else if (attacking) {
			if (miningPos != null) {
				client.gameMode.continueDestroyBlock(miningPos, miningDirection);
			}
		} else if (wasAttacking) {
			client.gameMode.stopDestroyBlock();
			miningPos = null;
			miningDirection = null;
		}
		wasAttacking = attacking;

		// Recompute the raycast fresh for each click: placing one block can
		// change what the next click in the same tick should hit (e.g.
		// pillaring up while spamming use).
		for (int i = 0; i < frame.useClicks(); i++) {
			BlockHitResult hit = raycast(player, frame);
			InteractionResult result = hit.getType() == HitResult.Type.BLOCK
					? client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
					: client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			swingIfClientTriggered(player, result);
		}

		boolean swapping = frame.swapHands();
		if (swapping && !wasSwapping) {
			ClientPacketListener connection = client.getConnection();
			if (connection != null) {
				connection.send(new ServerboundPlayerActionPacket(
						ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
			}
		}
		wasSwapping = swapping;

		boolean dropping = frame.drop();
		if (dropping && !wasDropping) {
			player.drop(false);
		}
		wasDropping = dropping;
	}

	/**
	 * Vanilla's own click handling swings the arm itself, as a side effect
	 * of the click code in {@code Minecraft} rather than of {@code useItemOn}
	 * itself — calling {@code useItemOn} directly like we do skips that, so
	 * without this the animation (and the swing packet other players would
	 * see) is silently missing.
	 */
	private static void swingIfClientTriggered(LocalPlayer player, InteractionResult result) {
		if (result instanceof InteractionResult.Success success
				&& success.swingSource() == InteractionResult.SwingSource.CLIENT) {
			player.swing(InteractionHand.MAIN_HAND);
		}
	}

	private static BlockHitResult raycast(LocalPlayer player, Frame frame) {
		Vec3 origin = player.getEyePosition();
		Vec3 look = player.calculateViewVector(frame.pitch(), frame.yaw());
		double reach = player.blockInteractionRange();
		Vec3 end = origin.add(look.scale(reach));
		ClipContext context = new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
		return player.level().clip(context);
	}

	/**
	 * Block-only {@link #raycast} is fine for placing, but attacking needs
	 * to consider entities too — {@code Level.clip()} tests blocks
	 * exclusively, so without this an attack aimed at a mob would sail
	 * straight through it and hit (or start mining) whatever block happens
	 * to be behind or beneath it instead.
	 */
	private static HitResult pick(LocalPlayer player, Frame frame) {
		Vec3 origin = player.getEyePosition();
		Vec3 look = player.calculateViewVector(frame.pitch(), frame.yaw());
		double blockReach = player.blockInteractionRange();
		double entityReach = player.entityInteractionRange();
		double reach = Math.max(blockReach, entityReach);
		Vec3 end = origin.add(look.scale(reach));

		ClipContext context = new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
		BlockHitResult blockHit = player.level().clip(context);
		double blockDistSq = blockHit.getLocation().distanceToSqr(origin);
		double entityReachSq = entityReach * entityReach;

		AABB searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				player, origin, end, searchBox,
				candidate -> !candidate.isSpectator() && candidate.isPickable(),
				Math.min(blockDistSq, entityReachSq)
		);

		if (entityHit != null) {
			double entityDistSq = entityHit.getLocation().distanceToSqr(origin);
			if (entityDistSq <= entityReachSq && (blockHit.getType() != HitResult.Type.BLOCK || entityDistSq < blockDistSq)) {
				return entityHit;
			}
		}
		return blockHit;
	}

	private void releaseInputs() {
		Options options = Minecraft.getInstance().options;
		setDown(options.keyUp, false);
		setDown(options.keyDown, false);
		setDown(options.keyLeft, false);
		setDown(options.keyRight, false);
		setDown(options.keyJump, false);
		setDown(options.keyShift, false);
		setDown(options.keySprint, false);
	}

	private static void setDown(KeyMapping mapping, boolean down) {
		((KeyMappingAccessor) (Object) mapping).marionette$setDown(down);
	}
}
