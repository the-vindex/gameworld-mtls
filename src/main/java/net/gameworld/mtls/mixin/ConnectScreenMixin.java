package net.gameworld.mtls.mixin;

import net.gameworld.mtls.GwContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** From MinecraftTLS: the "Server Connector" thread inherits the entry being joined. */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void gw$head(Screen parent, Minecraft mc, ServerAddress address, ServerData data, boolean quickPlay,
                                TransferState transfer, CallbackInfo ci) {
        GwContext.CONNECTING_SERVER.set(data);
    }

    @Inject(method = "startConnecting", at = @At("TAIL"))
    private static void gw$tail(Screen parent, Minecraft mc, ServerAddress address, ServerData data, boolean quickPlay,
                                TransferState transfer, CallbackInfo ci) {
        GwContext.CONNECTING_SERVER.remove();
    }
}
