package com.pwetutils.listener;

import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class SettingsOverlayListener {
    private static final int BOX_WIDTH = 300;
    private static final int PADDING = 2;

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();

        int x = 4;
        int y = 500;
        int height = mc.fontRendererObj.FONT_HEIGHT;
        int spacing = height + PADDING * 2 + 2;
        drawTextBox(mc, "bedwarsResourceTimer", x, y);
        drawTextBox(mc, "bedwarsExperienceCounter", x, y + spacing);
        drawTextBox(mc, "hypixelEmoteConverter", x, y + spacing * 2);
    }

    private void drawTextBox(Minecraft mc, String text, int x, int y) {
        int height = mc.fontRendererObj.FONT_HEIGHT;
        Gui.drawRect(x - PADDING, y - PADDING,
                x + BOX_WIDTH + PADDING, y + height + PADDING,
                0x80000000);
        mc.fontRendererObj.drawStringWithShadow(text, x, y, 0xFFFFFF);
    }
}