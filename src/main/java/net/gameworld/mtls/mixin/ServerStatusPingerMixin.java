package net.gameworld.mtls.mixin;

import net.gameworld.mtls.CardStore;
import net.gameworld.mtls.GameworldMtls;
import net.gameworld.mtls.GwConfig;
import net.gameworld.mtls.GwContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * Server-list pings: the entry travels to Connection.connect (from MinecraftTLS); no plaintext
 * legacy ping is ever sent to a Gameworld address; a gate entry without a usable card says so.
 */
@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @Inject(method = "pingServer", at = @At("HEAD"))
    private void gw$head(ServerData data, Runnable a, Runnable b, CallbackInfo ci) {
        GwContext.CONNECTING_SERVER.set(data);
    }

    @Inject(method = "pingServer", at = @At("RETURN"))
    private void gw$tail(ServerData data, Runnable a, Runnable b, CallbackInfo ci) {
        GwContext.CONNECTING_SERVER.remove();
    }

    @Inject(method = "pingLegacyServer", at = @At("HEAD"), cancellable = true)
    private void gw$noLegacy(InetSocketAddress resolved, ServerAddress address, ServerData data, CallbackInfo ci) {
        if (GameworldMtls.config().match(data.ip).role() != GwConfig.Role.NONE) ci.cancel();
    }

    @Inject(method = "onPingFailed", at = @At("TAIL"))
    private void gw$failed(Component reason, ServerData data, CallbackInfo ci) {
        GwConfig.Match m = GameworldMtls.config().match(data.ip);
        if (m.role() != GwConfig.Role.GATE) return;
        String uuid = CardStore.dashless(Minecraft.getInstance().getUser().getProfileId());
        CardStore.Card card = GameworldMtls.store().load(m.server().host, uuid);
        String enroll = "\"" + m.server().name + " enroll\"";
        if (card == null) {
            data.motd = Component.literal("No key card yet: join " + enroll + " once").withStyle(ChatFormatting.GOLD);
        } else if (!card.validAt(Instant.now())) {
            data.motd = Component.literal("Card expired: join " + enroll).withStyle(ChatFormatting.GOLD);
        }
    }
}
