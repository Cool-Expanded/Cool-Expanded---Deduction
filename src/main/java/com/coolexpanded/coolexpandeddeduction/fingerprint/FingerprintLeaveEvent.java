package com.coolexpanded.coolexpandeddeduction.fingerprint;

import com.coolexpanded.coolexpandeddeduction.fingerprint.FingerprintLeaveData.Trace;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = "coolexpandeddeduction")
public class FingerprintLeaveEvent {

    private static final int PLAYER_FINGERPRINT_CHANCE = 20;
    private static final int CLEANUP_INTERVAL_TICKS = 1200;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiEvent) {
            for (BlockSnapshot snapshot : multiEvent.getReplacedBlockSnapshots()) {
                leaveBlockFingerprint(player, snapshot.getPos());
            }
        } else {
            leaveBlockFingerprint(player, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean holdingItem = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (player.isSecondaryUseActive() && holdingItem) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = player.level().getBlockState(pos);

        if (isInteractive(player.level(), pos, state)) {
            leaveBlockFingerprint(player, pos);
        }
    }

    private static boolean isInteractive(Level level, BlockPos pos, BlockState state) {
        return state.getMenuProvider(level, pos) != null
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BEDS)
                || state.is(Blocks.LEVER)
                || state.is(Blocks.ENDER_CHEST);
        //TODO: change this in the tag
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }

        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        if (victim.getRandom().nextInt(100) >= PLAYER_FINGERPRINT_CHANCE) {
            return;
        }

        FingerprintLeaveData.get(victim.server).addPlayerTrace(victim.getUUID(), createTrace(attacker));
    }


    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        if (server.getTickCount() % CLEANUP_INTERVAL_TICKS == 0) {
            FingerprintLeaveData.get(server).removeAllExpired();
        }
    }

    private static void leaveBlockFingerprint(ServerPlayer player, BlockPos pos) {
        FingerprintLeaveData.get(player.server).addBlockTrace(player.level(), pos, createTrace(player));
    }

    private static Trace createTrace(ServerPlayer player) {
        String fingerprintId = FingerprintData.get(player.server).getOrCreateId(player.getUUID());

        return new Trace(
                player.getUUID(),
                player.getGameProfile().getName(),
                fingerprintId,
                System.currentTimeMillis()
        );
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("fingerprint")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("residue")
                                .then(Commands.literal("block")
                                        .executes(context -> showBlockTraces(context.getSource()))
                                )
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> showPlayerTraces(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player")
                                                ))
                                        )
                                )
                        )
        );
    }

    private static int showBlockTraces(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        HitResult hit = player.pick(6.0, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a block"));
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        List<Trace> traces = FingerprintLeaveData.get(source.getServer()).getBlockTraces(player.level(), pos);

        printTraces(source, "Block " + pos.toShortString(), traces);
        return 1;
    }

    private static int showPlayerTraces(CommandSourceStack source, ServerPlayer target) {
        List<Trace> traces = FingerprintLeaveData.get(source.getServer()).getPlayerTraces(target.getUUID());

        printTraces(source, target.getGameProfile().getName(), traces);
        return 1;
    }

    private static void printTraces(CommandSourceStack source, String title, List<Trace> traces) {
        if (traces.isEmpty()) {
            source.sendSuccess(() -> Component.literal(title + ": no fingerprints"), false);
            return;
        }

        source.sendSuccess(() -> Component.literal(title + " (newest first):"), false);

        int number = 1;
        for (Trace trace : traces) {
            long minutesAgo = (System.currentTimeMillis() - trace.createdAt()) / 60000L;
            String line = number + ". " + trace.playerName() + " [" + trace.fingerprintId() + "] - " + minutesAgo + " min ago";
            source.sendSuccess(() -> Component.literal(line), false);
            number++;
        }
    }
}