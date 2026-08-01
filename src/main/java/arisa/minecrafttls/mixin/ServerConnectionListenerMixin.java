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

    // Cache reflection method once to avoid reflection overhead on every connection
    @Unique
    private static final Method mtls$INIT_CHANNEL_METHOD;
    @Unique
    private static SslContext mtls$cachedSslContext = null;
    @Unique
    private static boolean mtls$isInitialized = false;

    static {
        try {
            mtls$INIT_CHANNEL_METHOD = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
            mtls$INIT_CHANNEL_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("[mTLS] Failed to reflectively access ChannelInitializer#initChannel", e);
        }
    }

    @Unique
    private static synchronized SslContext mtls$getOrBuildSslContext() {
        if (mtls$isInitialized) {
            return mtls$cachedSslContext;
        }

        mtls$isInitialized = true;
        File dir = new File("config/mtls_server");
        File ca = new File(dir, "ca.crt");
        File cert = new File(dir, "server.crt");
        File key = new File(dir, "server.key");

        if (ca.exists() && cert.exists() && key.exists()) {
            try {
                mtls$cachedSslContext = SslContextBuilder.forServer(cert, key)
                        .trustManager(ca)
                        .clientAuth(ClientAuth.REQUIRE)
                        .build();

                LOGGER.info("[mTLS] Server SslContext initialized. Mutual TLS active.");
            } catch (Exception e) {
                LOGGER.error("[mTLS] Failed to initialize network SSL crypto layer!", e);
            }
        } else {
            if (!dir.exists() && dir.mkdirs()) {
                LOGGER.info("[mTLS] Created directory config/mtls_server/");
            }
            LOGGER.info("[mTLS] No certificates found inside config/mtls_server/. Running in unencrypted pass-through mode.");
        }

        return mtls$cachedSslContext;
    }

    @Redirect(
            method = "startTcpServerListener",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/netty/bootstrap/ServerBootstrap;childHandler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;",
                    remap = false
            )
    )
    private ServerBootstrap onChildHandler(ServerBootstrap bootstrap, ChannelHandler childHandler) {
        if (!(childHandler instanceof ChannelInitializer)) {
            return bootstrap.childHandler(childHandler);
        }

        @SuppressWarnings("unchecked")
        ChannelInitializer<Channel> vanillaInitializer = (ChannelInitializer<Channel>) childHandler;
        SslContext sslContext = mtls$getOrBuildSslContext();

        return bootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                if (sslContext != null && channel instanceof SocketChannel) {
                    channel.pipeline().addFirst("ssl", sslContext.newHandler(channel.alloc()));
                }

                // Invoke vanilla initializer via cached reflection method
                mtls$INIT_CHANNEL_METHOD.invoke(vanillaInitializer, channel);
            }
        });
    }
}