package com.flechazo.eos.menu;

import com.flechazo.eos.entity.FriendlySurvivorEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class SurvivorPersonalMenu extends AbstractContainerMenu {
    private static final int ARMOR_COUNT = 4;
    private static final int HAND_COUNT = 2;
    private static final int TACTICAL_COUNT = 10;
    private static final int SURVIVOR_SLOT_COUNT = ARMOR_COUNT + HAND_COUNT + TACTICAL_COUNT;
    private static final int PLAYER_INV_START = SURVIVOR_SLOT_COUNT;
    private static final int PLAYER_INV_ROWS = 3;
    private static final int PLAYER_INV_COLS = 9;
    private static final int HOTBAR_START = PLAYER_INV_START + PLAYER_INV_ROWS * PLAYER_INV_COLS;
    private static final int MENU_SLOT_COUNT = HOTBAR_START + PLAYER_INV_COLS;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final EquipmentSlot[] HAND_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private final int survivorEntityId;
    private FriendlySurvivorEntity syncEntity;
    private final ItemStackHandler handler = new ItemStackHandler(SURVIVOR_SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            if (syncEntity == null) return;
            syncSlotNow(slot);
        }
    };

    public SurvivorPersonalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(EosMenus.SURVIVOR_PERSONAL.get(), containerId);
        this.survivorEntityId = buf.readVarInt();
        for (int i = 0; i < SURVIVOR_SLOT_COUNT; i++) {
            handler.setStackInSlot(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        addOwnSlots(inventory.player);
        addPlayerSlots(inventory);
    }

    public SurvivorPersonalMenu(int containerId, Inventory inventory, FriendlySurvivorEntity survivor) {
        super(EosMenus.SURVIVOR_PERSONAL.get(), containerId);
        this.survivorEntityId = survivor.getId();
        for (int i = 0; i < ARMOR_COUNT; i++) {
            handler.setStackInSlot(i, survivor.getItemBySlot(ARMOR_SLOTS[i]));
        }
        handler.setStackInSlot(ARMOR_COUNT, survivor.getMainHandItem());
        handler.setStackInSlot(ARMOR_COUNT + 1, survivor.getOffhandItem());
        for (int i = 0; i < TACTICAL_COUNT; i++) {
            handler.setStackInSlot(ARMOR_COUNT + HAND_COUNT + i, survivor.tacticalInventory.getStackInSlot(i));
        }
        addOwnSlots(inventory.player);
        addPlayerSlots(inventory);
        this.syncEntity = survivor;
    }

    private static int scaled(int value) {
        return value;
    }

    private void addOwnSlots(Player player) {
        FriendlySurvivorEntity survivor = player.level().getEntity(survivorEntityId) instanceof FriendlySurvivorEntity s ? s : null;
        for (int i = 0; i < ARMOR_COUNT; i++) {
            EquipmentSlot eq = ARMOR_SLOTS[i];
            addSlot(new SlotItemHandler(handler, i, scaled(13), scaled(37 + i * 18)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return canModifySurvivor(player) && !stack.isEmpty() && survivor != null && stack.canEquip(eq, survivor);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return canModifySurvivor(player) && super.mayPickup(player);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        addSlot(new SlotItemHandler(handler, ARMOR_COUNT, scaled(85), scaled(37)) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canModifySurvivor(player) && !stack.isEmpty();
            }

            @Override
            public boolean mayPickup(Player player) {
                return canModifySurvivor(player) && super.mayPickup(player);
            }
        });
        addSlot(new SlotItemHandler(handler, ARMOR_COUNT + 1, scaled(85), scaled(55)) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canModifySurvivor(player) && !stack.isEmpty() && survivor != null && stack.canEquip(EquipmentSlot.OFFHAND, survivor);
            }

            @Override
            public boolean mayPickup(Player player) {
                return canModifySurvivor(player) && super.mayPickup(player);
            }
        });

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 5; col++) {
                int idx = ARMOR_COUNT + HAND_COUNT + row * 5 + col;
                addSlot(new SlotItemHandler(handler, idx, scaled(13) + col * scaled(18), scaled(117) + row * scaled(18)) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return canModifySurvivor(player) && super.mayPlace(stack);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return canModifySurvivor(player) && super.mayPickup(player);
                    }
                });
            }
        }   
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < PLAYER_INV_ROWS; row++) {
            for (int col = 0; col < PLAYER_INV_COLS; col++) {
                int idx = (row + 1) * PLAYER_INV_COLS + col;
                addSlot(new Slot(playerInventory, idx, scaled(116) + col * scaled(18), scaled(84) + row * scaled(18)));
            }
        }
        for (int col = 0; col < PLAYER_INV_COLS; col++) {
            addSlot(new Slot(playerInventory, col, scaled(116) + col * scaled(18), scaled(142)));
        }
    }

    public int survivorEntityId() {
        return survivorEntityId;
    }

    @Override
    public boolean stillValid(Player player) {
        Entity entity = player.level().getEntity(survivorEntityId);
        if (!(entity instanceof FriendlySurvivorEntity survivor)) return false;
        return player.distanceToSqr(survivor) <= 64.0;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index < SURVIVOR_SLOT_COUNT) {
            if (!canModifySurvivor(player)) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, PLAYER_INV_START, MENU_SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!canModifySurvivor(player)) {
                return ItemStack.EMPTY;
            }
            if (!moveToSurvivor(stack, player)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, result);
        } else {
            slot.setChanged();
        }
        return result;
    }

    private boolean canModifySurvivor(Player player) {
        Entity entity = player.level().getEntity(survivorEntityId);
        return entity instanceof FriendlySurvivorEntity survivor && survivor.isRecruitOwner(player);
    }

    private boolean moveToSurvivor(ItemStack stack, Player player) {
        FriendlySurvivorEntity entity = player.level().getEntity(survivorEntityId) instanceof FriendlySurvivorEntity s ? s : null;
        if (entity == null) return false;

        for (int i = 0; i < ARMOR_COUNT; i++) {
            if (stack.canEquip(ARMOR_SLOTS[i], entity)) {
                if (moveItemStackTo(stack, i, i + 1, false)) return true;
                break;
            }
        }
        if (stack.canEquip(EquipmentSlot.OFFHAND, entity)) {
            if (moveItemStackTo(stack, ARMOR_COUNT + 1, ARMOR_COUNT + 2, false)) return true;
        }
        if (moveItemStackTo(stack, ARMOR_COUNT, ARMOR_COUNT + 1, false)) return true;
        return moveItemStackTo(stack, ARMOR_COUNT + HAND_COUNT, SURVIVOR_SLOT_COUNT, false);
    }

    private void syncSlotNow(int slot) {
        if (slot < ARMOR_COUNT) {
            syncEntity.setItemSlot(ARMOR_SLOTS[slot], handler.getStackInSlot(slot));
        } else if (slot < ARMOR_COUNT + HAND_COUNT) {
            syncEntity.setItemSlot(HAND_SLOTS[slot - ARMOR_COUNT], handler.getStackInSlot(slot));
        } else {
            syncEntity.tacticalInventory.setStackInSlot(slot - ARMOR_COUNT - HAND_COUNT, handler.getStackInSlot(slot));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (syncEntity != null) {
            for (int i = 0; i < SURVIVOR_SLOT_COUNT; i++) {
                syncSlotNow(i);
            }
        }
    }

    public static void open(Player player, FriendlySurvivorEntity survivor) {
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer sp)) return;

        sp.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return survivor.getDisplayName();
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                return new SurvivorPersonalMenu(containerId, inv, survivor);
            }
        }, buf -> {
            buf.writeVarInt(survivor.getId());
            for (int i = 0; i < ARMOR_COUNT; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, survivor.getItemBySlot(ARMOR_SLOTS[i]));
            }
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, survivor.getMainHandItem());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, survivor.getOffhandItem());
            for (int i = 0; i < TACTICAL_COUNT; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, survivor.tacticalInventory.getStackInSlot(i));
            }
        });
    }
}
