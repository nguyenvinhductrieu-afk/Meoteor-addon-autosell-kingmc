package com.example.addon.modules;

import baritone.api.BaritoneAPI;
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

    // 1. Danh sách quặng cần đào bằng Baritone
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

    // 3. Danh sách vật phẩm/quặng rác cần dọn dẹp
    private final Setting<List<Item>> trashItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Vật phẩm rác sẽ tự động vứt bỏ để nhường chỗ cho quặng chính.")
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

        // 1.5. Vứt/Xử lý vật phẩm rác trong balo
        cleanTrash();

        // 1. Trải đều quặng mục tiêu vào 27 ô chính (Slot ID từ 9 đến 35 trong PlayerScreenHandler)
        if (getTotalTargetCount() >= 27) {
            spreadTargetItems();
        }

        // 2. Kiểm tra nếu đã FULL 27 ô quặng mục tiêu (mỗi ô đều đủ max stack) -> Bán & Lặp lại
        if (isInventoryFullyStaked()) {
            stopBaritone();
            
            // Lệnh bán đồ
            ChatUtils.sendPlayerMsg("/sellgui");

            // Khởi động lại Baritone đào tiếp (vòng lặp vô tận)
            startBaritoneMining();
        }
    }

    // --- CÁC HÀM XỬ LÝ LOGIC BALO & BARITONE ---

    // Xóa vật phẩm rác ra khỏi 27 ô balo
    private void cleanTrash() {
        if (trashItems.get().isEmpty()) return;

        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) {
                // Vứt cả stack rác ra ngoài đất (Click chuột vứt)
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, mc.player);
            }
        }
    }

    // Trải đều quặng vào các ô trống trong balo để khóa slot không cho quặng khác lọt vào
    private void spreadTargetItems() {
        int emptySlot = -1;

        // Tìm ô trống trong 27 ô chính
        for (int i = 9; i <= 35; i++) {
            if (mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot == -1) return; // Không còn ô trống nào

        // Tìm ô quặng mục tiêu có số lượng > 1 để tách bớt 1 phần sang ô trống
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (isTargetItem(stack) && stack.getCount() > 1) {
                // Nhấp chuột phải (button 1) để lấy một nửa stack
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.PICKUP, mc.player);
                // Đặt vào ô trống
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                
                // Trả phần dư trên con trỏ (nếu có) về lại vị trí cũ
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                }
                break;
            }
        }
    }

    // Kiểm tra xem 27 ô balo đã được lấp đầy tối đa stack (64/64) chưa
    private boolean isInventoryFullyStaked() {
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!isTargetItem(stack) || stack.getCount() < stack.getMaxCount()) {
                return false;
            }
        }
        return true;
    }

    // Đếm tổng số lượng quặng mục tiêu đang có trong 27 ô balo
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

        StringBuilder command = new StringBuilder("mine ");
        for (Block block : targetBlocks.get()) {
            String blockName = block.getTranslationKey().replace("block.minecraft.", "");
            command.append(blockName).append(" ");
        }

        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command.toString().trim());
    }

    private void stopBaritone() {
        if (BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
    }
}
