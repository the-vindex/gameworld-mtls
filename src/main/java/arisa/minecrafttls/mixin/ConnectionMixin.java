package arisa.minecrafttls.mixin;

import arisa.minecrafttls.ImTLSServerData;
import arisa.minecrafttls.mTLSStorage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

import static arisa.minecrafttls.MinecraftTLS.LOGGER;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        ServerData activeServer = mTLSStorage.currentServer;

        if (activeServer != null) {
            ImTLSServerData mtlsData = (ImTLSServerData) activeServer;

            if (mtlsData.mtls$isEnabled()) {
                try {
                    File ca = new File(mtlsData.mtls$getCaPath());
                    LOGGER.debug("[mTLS] CA: {}", mtlsData.mtls$getCaPath());
                    LOGGER.debug("[mTLS] CA: {}", ca);
                    File cert = new File(mtlsData.mtls$getCertPath());
                    LOGGER.debug("[mTLS] Player cert: {}", mtlsData.mtls$getCertPath());
                    LOGGER.debug("[mTLS] Player cert: {}", cert);
                    File key = new File(mtlsData.mtls$getKeyPath());
                    LOGGER.debug("[mTLS] Player key: {}", mtlsData.mtls$getKeyPath());
                    LOGGER.debug("[mTLS] Player key: {}", key);

                    if (ca.exists() && cert.exists() && key.exists()) {
                        SslContext sslContext = SslContextBuilder.forClient()
                                .trustManager(ca)
                                .keyManager(cert, key)
                                .build();

                        // Slap the handler at the absolute front of the Netty pipeline context queue
                        context.pipeline().addFirst("ssl", sslContext.newHandler(context.channel().alloc()));
                        LOGGER.info("[mTLS] Successfully injected SslHandler into client pipeline channel!");
                    } else {
                        LOGGER.warn("[mTLS] Handshake aborted: Missing physical key verification files.");
                    }
                } catch (Exception e) {
                    LOGGER.error("[mTLS] Critical failure initializing SSL layer context.");
                    e.printStackTrace();
                }
            } else {
                LOGGER.debug("[mTLS] I am disabled!");
            }
        } else {
            LOGGER.error("[mTLS] No active server!");
        }
    }
}