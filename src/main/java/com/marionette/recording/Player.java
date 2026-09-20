package com.marionette.recording;

import com.marionette.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * <p>Because interaction aim doesn't depend on the entity's actual current
 * rotation, the two can be timed independently: movement/action keys are
 * applied at the <em>start</em> of the tick (physics needs to see them in
 * time to move the player correctly this tick), but the visible rotation
 * itself is applied at the <em>end</em>, after the entity's own tick has
 * already snapshotted its old rotation for the camera to interpolate from.
 * Setting it at tick start (as input is) made the camera snap straight to
 * each new angle with nothing to interpolate from, which is what made
 * playback look like it was constantly teleporting; applying it last means
 * every render between now and the next tick eases from the old angle to
 * the new one, same as it would if a person were actually turning the
 * mouse, while aim accuracy is unaffected either way.
 */
public class Player {
	private Recording recording;
	private int index;
	private boolean playing;
	private boolean loop;
	private Frame pendingInteractionFrame;

	private boolean wasAttacking;
	private boolean wasSwapping;
	private boolean wasDropping;
	private BlockPos miningPos;
	private Direction miningDirection;

	public void start(Recording recording, LocalPlayer player, boolean loop) {
		this.recording = recording;
		this.index = 0;
		this.playing = true;
		this.loop = loop;
		this.pendingInteractionFrame = null;
		this.wasAttacking = false;
		this.wasSwapping = false;
		this.wasDropping = false;
		this.miningPos = null;
		this.miningDirection = null;

		resetForStart(player);
	}

	/**
	 * Snaps X/Z to the center of whatever block the player is standing on
	 * before the first frame runs (once, not every tick — this doesn't
	 * fight physics the way per-tick teleporting did). Movement is purely
	 * input-driven and relies on physics being deterministic given the same
	 * starting conditions; starting from an arbitrary sub-block position
	 * (e.g. x=363.499 instead of x=363.5) is itself a source of drift
	 * between the original recording and any replay of it, compounding
	 * over a long take. Y is left alone since it's already exact once
	 * standing on solid ground.
	 */
	private void resetForStart(LocalPlayer player) {
		player.setPos(Math.floor(player.getX()) + 0.5, player.getY(), Math.floor(player.getZ()) + 0.5);
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
		pendingInteractionFrame = null;
		if (wasAttacking) {
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
			if (!loop) {
				stop();
				return;
			}
			index = 0;
			resetForStart(player);
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
		player.getInventory().setSelectedSlot(frame.selectedSlot());

		pendingInteractionFrame = frame;
	}

	public void applyInteractions(Minecraft client) {
		if (pendingInteractionFrame == null) {
			return;
		}
		Frame frame = pendingInteractionFrame;
		pendingInteractionFrame = null;

		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		player.setYRot(frame.yaw());
		player.setXRot(frame.pitch());

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
