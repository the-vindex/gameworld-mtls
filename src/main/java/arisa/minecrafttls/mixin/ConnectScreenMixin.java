package arisa.minecrafttls.mixin;

import arisa.minecrafttls.mTLSContext;
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

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void onStartConnectingHead(Screen parent, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, boolean isQuickPlay, TransferState transferState, CallbackInfo ci) {
        mTLSContext.CONNECTING_SERVER.set(serverData);
    }

    @Inject(method = "startConnecting", at = @At("TAIL"))
    private static void onStartConnectingTail(Screen parent, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, boolean isQuickPlay, TransferState transferState, CallbackInfo ci) {
        mTLSContext.CONNECTING_SERVER.remove();
    }
}