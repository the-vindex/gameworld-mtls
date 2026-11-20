package net.gameworld.mtls.mixin;

import net.gameworld.mtls.Enrollment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the bytes of a gameworld:enroll query (vanilla discards unknown payloads). Size-capped. */
@Mixin(ClientboundCustomQueryPacket.class)
public class ClientboundCustomQueryPacketMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void gw$readPayload(ResourceLocation id, FriendlyByteBuf buf, CallbackInfoReturnable<CustomQueryPayload> cir) {
        if (!Enrollment.CHANNEL.equals(id)) return;
        int n = buf.readableBytes();
        if (n > Enrollment.MAX_QUERY) throw new IllegalArgumentException("gameworld:enroll query too large");
        byte[] data = new byte[n];
        buf.readBytes(data);
        cir.setReturnValue(new Enrollment.Query(data));
    }
}
