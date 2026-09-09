package com.example.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

public class AutoMineSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 1. Danh sách quặng cần đào
    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("target-blocks")
        .description("Danh sách khối quặng Baritone sẽ đào.")
        .build()
    );

    // 2. Danh sách quặng mục tiêu cần gom & bán
    private final Setting<List<Item>> targetItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("target-items")
        .description("Quặng chính cần giữ, trải đều full 27 ô và bán.")
        .build()
    );

    // 3. Danh sách vật phẩm rác
    private final Setting<List<Item>> trashItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Vật phẩm rác sẽ tự động vứt bỏ.")
        .build()
    );

    private final Setting<Integer> checkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("check-delay-ticks")
        .description("Độ trễ kiểm tra kho đồ (20 ticks = 1 giây).")
        .defaultValue(20)
        .min(5)
        .sliderMax(100)
        .build()
    );

    private int timer = 0;

    public AutoMineSell(Category category) {
        super(category, "auto-mine-sell", "Đào quặng, khóa 27 ô balo và tự động dùng /sellgui.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        startBaritoneMining();
    }

    @Override
    public void onDeactivate() {
        stopBaritone();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        timer++;
        if (timer < checkDelay.get()) return;
        timer = 0;

        // Vứt rác
        cleanTrash();

        // Khóa 27 ô balo bằng quặng mục tiêu
        if (getTotalTargetCount() >= 27) {
            spreadTargetItems();
        }

        // Bán khi full balo
        if (isInventoryFullyStacked()) {
            stopBaritone();
            ChatUtils.sendPlayerMsg("/sellgui");
            startBaritoneMining();
        }
    }

    private void cleanTrash() {
        if (trashItems.get().isEmpty()) return;

        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, mc.player);
            }
        }
    }

    private void spreadTargetItems() {
        int emptySlot = -1;

        for (int i = 9; i <= 35; i++) {
            if (mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot == -1) return;

        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (isTargetItem(stack) && stack.getCount() > 1) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);

                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                }
                break;
            }
        }
    }

    private boolean isInventoryFullyStacked() {
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!isTargetItem(stack) || stack.getCount() < stack.getMaxCount()) {
                return false;
            }
        }
        return true;
    }

    private int getTotalTargetCount() {
        int total = 0;
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (isTargetItem(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean isTargetItem(ItemStack stack) {
        return !stack.isEmpty() && targetItems.get().contains(stack.getItem());
    }

    private void startBaritoneMining() {
        if (targetBlocks.get().isEmpty()) return;

        StringBuilder command = new StringBuilder("#mine ");
        for (Block block : targetBlocks.get()) {
            String blockName = block.getTranslationKey().replace("block.minecraft.", "");
            command.append(blockName).append(" ");
        }

        ChatUtils.sendPlayerMsg(command.toString().trim());
    }

    private void stopBaritone() {
        ChatUtils.sendPlayerMsg("#stop");
    }
}
