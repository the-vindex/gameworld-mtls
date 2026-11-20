package net.gameworld.mtls.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import net.gameworld.mtls.BlockAll;
import net.gameworld.mtls.CardStore;
import net.gameworld.mtls.Enrollment;
import net.gameworld.mtls.GameworldMtls;
import net.gameworld.mtls.GateTls;
import net.gameworld.mtls.GwConfig;
import net.gameworld.mtls.GwConnection;
import net.gameworld.mtls.GwContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * Classifies every client connection when it is opened (gate / enroll / other) and, for the gate,
 * puts TLS with the player's card in front of everything else in the pipeline. Fail closed: if
 * TLS can't be set up, a handler that refuses every write goes first and the connection is
 * closed; nothing reaches the network in plaintext. (MinecraftTLS continued without TLS here.)
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin implements GwConnection {
    @Unique private volatile GwConfig.Match gw$match;
    @Unique private final Enrollment.State gw$enroll = new Enrollment.State();

    @Override public GwConfig.Match gw$match() { return gw$match; }
    @Override public void gw$setMatch(GwConfig.Match m) { gw$match = m; }
    @Override public Enrollment.State gw$enroll() { return gw$enroll; }

    @Inject(method = "connect", at = @At("HEAD"))
    private static void gw$classify(InetSocketAddress address, boolean epoll, Connection connection,
                                    CallbackInfoReturnable<?> cir) {
        GwConfig cfg = GameworldMtls.config();
        ServerData sd = GwContext.CONNECTING_SERVER.get();
        GwConfig.Match m = sd != null ? cfg.match(sd.ip) : null;
        if (m == null || m.role() == GwConfig.Role.NONE) {
            m = cfg.match(address.getHostString(), address.getPort()); // entries the ThreadLocal didn't reach
        }
        ((GwConnection) connection).gw$setMatch(m);
    }

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void gw$channelActive(ChannelHandlerContext ctx, CallbackInfo ci) {
        GwConfig.Match m = gw$match;
        if (m == null || m.role() != GwConfig.Role.GATE) return;
        GwConfig.Server s = m.server();
        String reason;
        try {
            GwConfig cfg = GameworldMtls.config();
            if (cfg.error() != null) throw new IOException("pack config broken: " + cfg.error());
            String uuid = CardStore.dashless(Minecraft.getInstance().getUser().getProfileId());
            CardStore.Card card = GameworldMtls.store().load(s.host, uuid);
            if (card == null) {
                reason = "No key card for " + s.name + " yet. Join \"" + s.name + " enroll\" once to get one.";
            } else if (!card.validAt(Instant.now())) {
                reason = "Your " + s.name + " key card has expired. Join \"" + s.name + " enroll\" to renew it.";
            } else {
                SslHandler tls = GateTls.newHandler(ctx.alloc(), cfg.serverCAs(), card, s.gateName(), s.gatePort);
                tls.handshakeFuture().addListener(f -> {
                    if (f.isSuccess()) {
                        GameworldMtls.LOGGER.info("[gameworld-mtls] TLS handshake with {} OK ({})", s.gateName(), tls.engine().getSession().getProtocol());
                    } else {
                        GameworldMtls.LOGGER.warn("[gameworld-mtls] TLS handshake with {} failed: {}", s.gateName(), String.valueOf(f.cause()));
                    }
                });
                ctx.pipeline().addFirst("gameworld_tls", tls);
                GameworldMtls.LOGGER.info("[gameworld-mtls] TLS to {} as {} (card valid until {})", s.gateName(), uuid, card.notAfter());
                return;
            }
        } catch (Throwable t) {
            GameworldMtls.LOGGER.error("[gameworld-mtls] TLS setup failed; refusing to connect", t);
            reason = "Gameworld secure connection could not start: " + t.getMessage();
        }
        ctx.pipeline().addFirst("gameworld_block", BlockAll.INSTANCE);
        GameworldMtls.LOGGER.warn("[gameworld-mtls] not connecting to {}: {}", s.gateName(), reason);
        ((Connection) (Object) this).disconnect(Component.literal(reason)); // channel not set yet: vanilla closes it right after
    }
}
