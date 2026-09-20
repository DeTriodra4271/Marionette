package com.marionette.mixin;

import com.marionette.recording.GuiEvent;
import com.marionette.recording.Recorder;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records item drops (Q / Ctrl+Q) and the player closing a container screen. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerActionMixin {
	@Inject(method = "drop(Z)Z", at = @At("HEAD"))
	private void marionette$recordDrop(boolean fullStack, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (Recorder.isCapturing()) {
			Recorder.note(new GuiEvent(GuiEvent.DROP, null, 0, fullStack ? 1 : 0, 0));
		}
	}

	@Inject(method = "closeContainer", at = @At("HEAD"))
	private void marionette$recordClose(CallbackInfo ci) {
		if (Recorder.isCapturing()) {
			Recorder.note(new GuiEvent(GuiEvent.CLOSE, null, 0, 0, 0));
		}
	}
}
