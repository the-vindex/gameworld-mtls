package net.gameworld.mtls.mixin;

import net.gameworld.mtls.Enrollment;
import net.gameworld.mtls.GwConnection;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The explicit login-query hook for gameworld:enroll (NeoForge payload registration has no login phase). */
@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerImplMixin {
    @Shadow @Final private Connection connection;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void gw$handleCustomQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        if (!(packet.payload() instanceof Enrollment.Query q)) return;
        GwConnection gc = (GwConnection) this.connection;
        byte[] answer = Enrollment.answer(gc.gw$match(), gc.gw$enroll(), q.data());
        this.connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(),
                answer == null ? null : new Enrollment.Answer(answer)));
        ci.cancel();
    }
}
