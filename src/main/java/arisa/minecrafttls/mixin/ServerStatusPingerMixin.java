package arisa.minecrafttls.mixin;

import arisa.minecrafttls.mTLSStorage;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @Inject(method = "pingServer", at = @At("HEAD"))
    private void onPingServer(ServerData serverData, Runnable runnable, Runnable runnable2, CallbackInfo ci) {
        // Set currentServer so ConnectionMixin knows which certs to use during the ping connection
        mTLSStorage.currentServer = serverData;
    }
}