package arisa.minecrafttls;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public class mTLSConfigScreen extends Screen {
    private final Screen parent;
    private final ServerData serverData;
    private final ImTLSServerData extendedData;

    private EditBox caEdit;
    private EditBox certEdit;
    private EditBox keyEdit;
    private Button toggleButton;

    public mTLSConfigScreen(Screen parent, ServerData serverData) {
        super(Component.literal("mTLS Configuration"));
        this.parent = parent;
        this.serverData = serverData;
        this.extendedData = (ImTLSServerData) serverData;
    }

    @Override
    protected void init() {
        int fieldWidth = 200;
        int startX = this.width / 2 - fieldWidth / 2;
        int startY = 45;

        this.toggleButton = Button.builder(
                Component.literal("mTLS Security: " + (extendedData.mtls$isEnabled() ? "ENABLED" : "DISABLED")),
                button -> {
                    extendedData.mtls$setEnabled(!extendedData.mtls$isEnabled());
                    button.setMessage(Component.literal("mTLS Security: " + (extendedData.mtls$isEnabled() ? "ENABLED" : "DISABLED")));
                }
        ).bounds(startX, startY, fieldWidth, 20).build();
        this.addRenderableWidget(this.toggleButton);

        this.caEdit = new EditBox(this.font, startX, startY + 40, fieldWidth, 20, Component.literal("CA Path"));
        this.caEdit.setValue(extendedData.mtls$getCaPath());
        this.caEdit.setMaxLength(512);
        this.addRenderableWidget(this.caEdit);

        this.certEdit = new EditBox(this.font, startX, startY + 80, fieldWidth, 20, Component.literal("Cert Path"));
        this.certEdit.setValue(extendedData.mtls$getCertPath());
        this.certEdit.setMaxLength(512);
        this.addRenderableWidget(this.certEdit);

        this.keyEdit = new EditBox(this.font, startX, startY + 120, fieldWidth, 20, Component.literal("Key Path"));
        this.keyEdit.setValue(extendedData.mtls$getKeyPath());
        this.keyEdit.setMaxLength(512);
        this.addRenderableWidget(this.keyEdit);

        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), button -> {
            extendedData.mtls$setCaPath(this.caEdit.getValue());
            extendedData.mtls$setCertPath(this.certEdit.getValue());
            extendedData.mtls$setKeyPath(this.keyEdit.getValue());
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, startY + 160, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int labelX = this.width / 2 - 100;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal("Target: " + serverData.ip), this.width / 2, 22, 0x808080);

        guiGraphics.drawString(this.font, "Authority (CA) Certificate Path", labelX, 73, 0xA0A0A0);
        guiGraphics.drawString(this.font, "Player Certificate Path", labelX, 113, 0xA0A0A0);
        guiGraphics.drawString(this.font, "Player Key Path", labelX, 153, 0xA0A0A0);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}