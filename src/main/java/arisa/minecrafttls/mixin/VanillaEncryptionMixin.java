package arisa.minecrafttls.mixin;

import arisa.minecrafttls.Config;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;

@Mixin(Connection.class)
public class VanillaEncryptionMixin {
    @Shadow
    private Channel channel;

    @Inject(method = "setEncryptionKey", at = @At("HEAD"), cancellable = true)
    private void skipVanillaEncryption(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        if (minecraftTLS$shouldDisableVanillaEncryption()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean minecraftTLS$shouldDisableVanillaEncryption() {
        boolean hasSslHandler = this.channel != null && this.channel.pipeline().get(SslHandler.class) != null;
        boolean forceDisable = Config.ALWAYS_DISABLE_VANILLA_ENCRYPTION.get();

        return hasSslHandler || forceDisable;
    }
}
