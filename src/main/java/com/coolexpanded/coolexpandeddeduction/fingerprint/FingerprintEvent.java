package com.coolexpanded.coolexpandeddeduction.fingerprint;

import com.coolexpanded.coolexpandeddeduction.CEDeduction;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.*;

@EventBusSubscriber(modid = "coolexpandeddeduction")
public class FingerprintEvent {


    private static final Map<UUID, Long> CRAFT_COOLDOWNS =
            new HashMap<>();

    private static final long CRAFT_COOLDOWN_MS = 3000L;

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FingerprintData data = FingerprintData.get(player.server);
        data.getOrCreateId(player.getUUID());
    }




    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("fingerprint")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("check")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> checkFingerprint(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("change")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> changeFingerprint(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                null
                                        ))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> changeFingerprint(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "id")
                                                ))
                                        )
                                )
                        )
        );
    }




    private static int checkFingerprint(
            net.minecraft.commands.CommandSourceStack source,
            ServerPlayer target
    ) {
        FingerprintData data = FingerprintData.get(source.getServer());
        String id = data.getOrCreateId(target.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Fingerprint of " + target.getGameProfile().getName() + ": " + id
        ), false);
        return 1;
    }


    private static int changeFingerprint(
            net.minecraft.commands.CommandSourceStack source,
            ServerPlayer target,
            String customId
    ) {
        FingerprintData data = FingerprintData.get(source.getServer());
        String newId;
        if (customId == null) {
            newId = data.generateUniqueId();
        } else {
            newId = customId.toUpperCase(Locale.ROOT);
            if (!newId.matches("[A-Z0-9]{8,10}")) {
                source.sendFailure(Component.literal(
                        "ID must be 8-10 characters: letters A-Z and digits 0-9"
                ));
                return 0;
            }
            if (data.isTaken(newId, target.getUUID())) {
                source.sendFailure(Component.literal(
                        "That ID already belongs to another player"
                ));
                return 0;
            }
        }

        data.setId(target.getUUID(), newId);
        source.sendSuccess(() -> Component.literal(
                "New fingerprint of " + target.getGameProfile().getName() + ": " + newId
        ), true);

        return 1;

    }


    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        InteractionHand usedHand = event.getHand();
        InteractionHand otherHand =
                usedHand == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND
                        : InteractionHand.MAIN_HAND;

        ItemStack usedStack = player.getItemInHand(usedHand);
        ItemStack otherStack = player.getItemInHand(otherHand);

        ItemStack paper;
        ItemStack ink;
        if (usedStack.is(Items.PAPER)
                && (otherStack.is(Items.INK_SAC)
                || otherStack.is(Items.BLACK_DYE))) {

            paper = usedStack;
            ink = otherStack;
        } else if (otherStack.is(Items.PAPER)
                && (usedStack.is(Items.INK_SAC)
                || usedStack.is(Items.BLACK_DYE))) {

            paper = otherStack;
            ink = usedStack;
        } else {
            return;
        }

        long now = System.currentTimeMillis();

        long cooldownEnd = CRAFT_COOLDOWNS.getOrDefault(
                player.getUUID(), 0L
        );

        if (now < cooldownEnd) {
            return;
        }



        FingerprintData data = FingerprintData.get(player.server);
        String fingerprintId = data.getOrCreateId(player.getUUID());

        ItemStack fingerprintPaper =
                new ItemStack(CEDeduction.FINGERPRINT_PAPER.get());

        fingerprintPaper.set(
                DataComponents.LORE,
                new ItemLore(List.of(
                        Component.literal("Отпечаток:  ")
                                .withStyle(ChatFormatting.GRAY)
                                .withStyle(style -> style.withItalic(false)),
                        Component.literal(fingerprintId)
                                .withStyle(ChatFormatting.AQUA)
                                .withStyle(style -> style.withItalic(false))
                ))
        );



        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("FingerprintId", fingerprintId);
        tag.putUUID("OwnerUUID", player.getUUID());

        fingerprintPaper.set(
                DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag)
        );


        paper.shrink(1);
        ink.shrink(1);


        if (!player.getInventory().add(fingerprintPaper)) {
            player.drop(fingerprintPaper, false);
        }

        CRAFT_COOLDOWNS.put(
                player.getUUID(),
                System.currentTimeMillis() + CRAFT_COOLDOWN_MS
        );

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.SQUID_SQUIRT,
                SoundSource.PLAYERS,
                0.6F,
                1.0F
        );

        for (int i = 0; i < 8; i++) {
            double x = player.getX()
                    + (player.getRandom().nextDouble() - 0.5) * 0.8;

            double y = player.getY() + 1.0
                    + player.getRandom().nextDouble() * 0.5;

            double z = player.getZ()
                    + (player.getRandom().nextDouble() - 0.5) * 0.8;

            player.serverLevel().sendParticles(
                    ParticleTypes.SQUID_INK,
                    x, y, z,
                    1,
                    0.05, 0.05, 0.05,
                    0.02
            );
        }
    }
}