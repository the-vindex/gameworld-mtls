package arisa.minecrafttls.mixin;

import arisa.minecrafttls.mTLSConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditServerScreen.class)
public abstract class ServerEditScreenMixin extends Screen {

    @Shadow
    @Final
    private ServerData serverData;
    @Shadow
    private EditBox ipEdit;

    protected ServerEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addMtlsButton(CallbackInfo ci) {
        int btnWidth = 100;
        int xPos = this.ipEdit.getX() + this.ipEdit.getWidth() - btnWidth;
        int yPos = this.ipEdit.getY() + 24;

        this.addRenderableWidget(Button.builder(
                Component.literal("mTLS Config"),
                button -> this.minecraft.setScreen(new mTLSConfigScreen(this, this.serverData))
        ).bounds(xPos, yPos, btnWidth, 20).build());
    }
}
