package arisa.minecrafttls.mixin;

import arisa.minecrafttls.ImTLSServerData;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerData.class)
public class ServerDataMixin implements ImTLSServerData {
    private boolean mtlsEnabled = false;
    private String mtlsCaPath = "config/mtls/ca.crt";
    private String mtlsCertPath = "config/mtls/client.crt";
    private String mtlsKeyPath = "config/mtls/client.key";

    @Inject(method = "read", at = @At("RETURN"))
    private static void onReadNbt(CompoundTag tag, CallbackInfoReturnable<ServerData> cir) {
        ServerData serverData = cir.getReturnValue();
        if (serverData != null) {
            ImTLSServerData extendedData = (ImTLSServerData) serverData;
            extendedData.mtls$setEnabled(tag.getBoolean("mtls_enabled"));
            if (tag.contains("mtls_ca_path")) extendedData.mtls$setCaPath(tag.getString("mtls_ca_path"));
            if (tag.contains("mtls_cert_path")) extendedData.mtls$setCertPath(tag.getString("mtls_cert_path"));
            if (tag.contains("mtls_key_path")) extendedData.mtls$setKeyPath(tag.getString("mtls_key_path"));
        }
    }

    @Override
    public boolean mtls$isEnabled() {
        return mtlsEnabled;
    }

    @Override
    public void mtls$setEnabled(boolean enabled) {
        this.mtlsEnabled = enabled;
    }

    @Override
    public String mtls$getCaPath() {
        return mtlsCaPath;
    }

    @Override
    public void mtls$setCaPath(String path) {
        this.mtlsCaPath = path;
    }

    @Override
    public String mtls$getCertPath() {
        return mtlsCertPath;
    }

    @Override
    public void mtls$setCertPath(String path) {
        this.mtlsCertPath = path;
    }

    @Override
    public String mtls$getKeyPath() {
        return mtlsKeyPath;
    }

    @Override
    public void mtls$setKeyPath(String path) {
        this.mtlsKeyPath = path;
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void onWriteNbt(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag != null) {
            tag.putBoolean("mtls_enabled", this.mtlsEnabled);
            tag.putString("mtls_ca_path", this.mtlsCaPath);
            tag.putString("mtls_cert_path", this.mtlsCertPath);
            tag.putString("mtls_key_path", this.mtlsKeyPath);
        }
    }

    @Inject(method = "copyFrom", at = @At("HEAD"))
    private void onCopyFrom(ServerData other, CallbackInfo ci) {
        ImTLSServerData extendedOther = (ImTLSServerData) other;
        this.mtlsEnabled = extendedOther.mtls$isEnabled();
        this.mtlsCaPath = extendedOther.mtls$getCaPath();
        this.mtlsCertPath = extendedOther.mtls$getCertPath();
        this.mtlsKeyPath = extendedOther.mtls$getKeyPath();
    }
}
