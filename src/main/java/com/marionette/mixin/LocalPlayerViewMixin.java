package com.marionette.mixin;

import com.marionette.recording.Player;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The local player's camera reads its raw rotation with no interpolation
 * (the mouse updates it every rendered frame), so a replay that only sets
 * rotation once per 20Hz tick would look steppy. While a take is playing,
 * this hands the camera a smoothed value instead; see
 * {@link Player#viewYaw(float)}.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerViewMixin {
	@Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
	private void marionette$smoothYaw(float partialTick, CallbackInfoReturnable<Float> cir) {
		Float yaw = Player.viewYaw(partialTick);
		if (yaw != null) {
			cir.setReturnValue(yaw);
		}
	}

	@Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
	private void marionette$smoothPitch(float partialTick, CallbackInfoReturnable<Float> cir) {
		Float pitch = Player.viewPitch(partialTick);
		if (pitch != null) {
			cir.setReturnValue(pitch);
		}
	}
}
