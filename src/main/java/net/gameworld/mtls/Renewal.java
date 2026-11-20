package net.gameworld.mtls;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Silent renewal: at game start and when the server list opens, if a card has fewer than renewDays left, run the
 * enrollment in the background (same login as joining the enroll entry, with a listener that
 * shows no screens). The old card stays in use until the new one is installed.
 */
public final class Renewal {
    private static final Set<String> attempted = new HashSet<>();
    private static final List<Connection> active = new CopyOnWriteArrayList<>();

    private Renewal() {}

    static void maybeRenew(Minecraft mc) {
        GwConfig cfg = GameworldMtls.config();
        String uuid = CardStore.dashless(mc.getUser().getProfileId());
        Instant now = Instant.now();
        for (GwConfig.Server s : cfg.servers()) {
            CardStore.Card card = GameworldMtls.store().load(s.host, uuid);
            if (card == null || !card.validAt(now)) continue;           // no card / expired: the player joins enroll
            if (card.remaining(now).compareTo(Duration.ofDays(cfg.renewDays())) >= 0) continue;
            if (!attempted.add(s.host + "/" + uuid)) continue;          // once per session
            GameworldMtls.LOGGER.info("[gameworld-mtls] card for {} expires {}; renewing in the background", s.host, card.notAfter());
            Thread t = new Thread(() -> start(mc, s), "gameworld-mtls renewal");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void start(Minecraft mc, GwConfig.Server s) {
        String ip = GameworldMtls.address(s.host, s.enrollPort);
        ServerData sd = new ServerData(s.name + " enroll", ip, ServerData.Type.OTHER);
        try {
            Optional<InetSocketAddress> addr = ServerNameResolver.DEFAULT.resolveAddress(ServerAddress.parseString(ip))
                    .map(ResolvedServerAddress::asInetSocketAddress);
            if (addr.isEmpty()) {
                GameworldMtls.LOGGER.warn("[gameworld-mtls] renewal: cannot resolve {}", ip);
                return;
            }
            GwContext.CONNECTING_SERVER.set(sd);
            Connection c;
            try {
                c = Connection.connectToServer(addr.get(), mc.options.useNativeTransport(), null);
            } finally {
                GwContext.CONNECTING_SERVER.remove();
            }
            c.initiateServerboundPlayConnection(addr.get().getHostName(), addr.get().getPort(),
                    new ClientHandshakePacketListenerImpl(c, mc, sd, null, false, null, status -> { }, null) {
                        @Override
                        public void onDisconnect(DisconnectionDetails details) {
                            GameworldMtls.LOGGER.info("[gameworld-mtls] renewal for {} ended: {}", s.host, details.reason().getString());
                        }
                    });
            c.send(new ServerboundHelloPacket(mc.getUser().getName(), mc.getUser().getProfileId()));
            active.add(c);
        } catch (Exception e) {
            GameworldMtls.LOGGER.warn("[gameworld-mtls] renewal for {} failed: {}", s.host, e.toString());
        }
    }

    /** Client tick: drive background connections like ConnectScreen does. */
    static void tick() {
        for (Connection c : active) {
            if (c.isConnected()) {
                c.tick();
            } else {
                c.handleDisconnection();
                active.remove(c);
            }
        }
    }
}
