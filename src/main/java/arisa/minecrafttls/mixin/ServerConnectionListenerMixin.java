package arisa.minecrafttls.mixin;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.lang.reflect.Method;

import static arisa.minecrafttls.MinecraftTLS.LOGGER;

@Mixin(ServerConnectionListener.class)
public class ServerConnectionListenerMixin {
    @Unique
    private static boolean mtls$hasLoggedState = false;

    @Redirect(
            method = "startTcpServerListener",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/netty/bootstrap/ServerBootstrap;childHandler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;",
                    remap = false
            )
    )
    private ServerBootstrap onChildHandler(ServerBootstrap bootstrap, ChannelHandler childHandler) {
        if (childHandler instanceof ChannelInitializer) {
            @SuppressWarnings("unchecked")
            ChannelInitializer<Channel> vanillaInitializer = (ChannelInitializer<Channel>) childHandler;

            return bootstrap.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) throws Exception {
                    if (channel instanceof SocketChannel) {
                        File dir = new File("config/mtls_server");
                        File ca = new File(dir, "ca.crt");
                        File cert = new File(dir, "server.crt");
                        File key = new File(dir, "server.key");

                        if (ca.exists() && cert.exists() && key.exists()) {
                            if (!mtls$hasLoggedState) {
                                LOGGER.info("[mTLS] Mutual TLS verification is active.");
                                mtls$hasLoggedState = true;
                            }
                            try {
                                SslContext sslContext = SslContextBuilder.forServer(cert, key)
                                        .trustManager(ca)
                                        .clientAuth(ClientAuth.REQUIRE)
                                        .build();

                                channel.pipeline().addFirst("ssl", sslContext.newHandler(channel.alloc()));
                            } catch (Exception e) {
                                LOGGER.error("[mTLS] Failed to initialize network crypto layer!");
                                e.printStackTrace();
                            }
                        } else {
                            if (!mtls$hasLoggedState) {
                                if (!dir.exists()) {
                                    dir.mkdirs();
                                }
                                LOGGER.info("[mTLS] No certificates found inside config/mtls_server/. Running in unencrypted pass-through mode.");
                                mtls$hasLoggedState = true;
                            }
                        }
                    }

                    Method initMethod = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
                    initMethod.setAccessible(true);
                    initMethod.invoke(vanillaInitializer, channel);
                }
            });
        }

        return bootstrap.childHandler(childHandler);
    }
}
