package com.marionette.mixin;

import com.marionette.recording.GuiEvent;
import com.marionette.recording.Recorder;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sees every click the player makes inside an inventory or container screen. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "handleContainerInput", at = @At("HEAD"))
	private void marionette$recordClick(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (Recorder.isCapturing()) {
			Recorder.note(new GuiEvent(GuiEvent.CLICK, GuiEvent.menuKey(player), slot, button, input.ordinal()));
		}
	}
}
