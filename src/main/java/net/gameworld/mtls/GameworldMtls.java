package net.gameworld.mtls;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Gameworld mTLS (client only). Fork of MinecraftTLS by Arisa Snowbell, GPL-3.0.
 *
 * <ul>
 *   <li>Connections to a pack-configured gate address always go through TLS with the player's
 *       card, verified against the pack's Server-CA and the name {@code gate.<host>}; if TLS can't
 *       be set up, the connection is closed before a single byte is written (fail closed).</li>
 *   <li>Vanilla encryption is untouched (it runs inside TLS). No server-side part exists.</li>
 *   <li>On the pack-configured enroll address, answers the {@code gameworld:enroll} login queries
 *       (CSR out, card in) and installs the card atomically. Renews silently under N days left.</li>
 * </ul>
 */
@Mod(value = GameworldMtls.MODID, dist = Dist.CLIENT)
public class GameworldMtls {
    public static final String MODID = "gameworld_mtls";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile GwConfig config = GwConfig.load(Path.of("config/gameworld-mtls")); // replaced in the constructor
    private static volatile CardStore store = new CardStore(Path.of("config/gameworld-mtls"));
    private static boolean entriesChecked;

    public GameworldMtls(IEventBus modBus, ModContainer container) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("gameworld-mtls");
        config = GwConfig.load(dir);
        store = new CardStore(dir);
        if (config.error() != null) {
            LOGGER.error("[gameworld-mtls] pack config broken ({}); gate connections will be refused", config.error());
        }
        for (GwConfig.Server s : config.servers()) {
            LOGGER.info("[gameworld-mtls] {}: gate {}:{} (TLS name {}), enroll {}:{}", s.name, s.host, s.gatePort, s.gateName(), s.host, s.enrollPort);
        }
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, GameworldMtls::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> Renewal.tick());
    }

    public static GwConfig config() { return config; }
    public static CardStore store() { return store; }

    private static void onScreenInit(ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof TitleScreen && !entriesChecked) {
            entriesChecked = true;
            ensureServerEntries(Minecraft.getInstance());
        }
        // Renew at game start and whenever the server list opens, i.e. before the player connects.
        if (e.getScreen() instanceof TitleScreen || e.getScreen() instanceof JoinMultiplayerScreen) {
            Renewal.maybeRenew(Minecraft.getInstance());
        }
    }

    static String address(String host, int port) { return port == 25565 ? host : host + ":" + port; }

    /** The pack's entries ("Gameworld", "Gameworld enroll") exist in the server list. */
    static void ensureServerEntries(Minecraft mc) {
        try {
            ServerList list = new ServerList(mc);
            list.load();
            boolean changed = false;
            for (GwConfig.Server s : config.servers()) {
                String gate = address(s.host, s.gatePort), enroll = address(s.host, s.enrollPort);
                if (list.get(gate) == null) {
                    list.add(new ServerData(s.name, gate, ServerData.Type.OTHER), false);
                    changed = true;
                }
                if (list.get(enroll) == null) {
                    list.add(new ServerData(s.name + " enroll", enroll, ServerData.Type.OTHER), false);
                    changed = true;
                }
            }
            if (changed) {
                list.save();
                LOGGER.info("[gameworld-mtls] added the pack's server entries");
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("[gameworld-mtls] could not add server entries", ex);
        }
    }
}
