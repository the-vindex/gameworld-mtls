package arisa.minecrafttls.mixin;

import arisa.minecrafttls.mTLSContext;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @Inject(method = "pingServer", at = @At("HEAD"))
    private void onPingServerHead(ServerData serverData, Runnable serverListUpdater, Runnable stateUpdater, CallbackInfo ci) {
        mTLSContext.CONNECTING_SERVER.set(serverData);
    }

    @Inject(method = "pingServer", at = @At("TAIL"))
    private void onPingServerTail(ServerData serverData, Runnable serverListUpdater, Runnable stateUpdater, CallbackInfo ci) {
        mTLSContext.CONNECTING_SERVER.remove();
    }
}