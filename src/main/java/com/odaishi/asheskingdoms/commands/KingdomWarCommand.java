package com.odaishi.asheskingdoms.commands;

import com.odaishi.asheskingdoms.kingdoms.KingdomWarManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KingdomWarCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("kingdom")
                        .then(literal("war")
                                .then(literal("declare")
                                        .then(argument("attacker", StringArgumentType.word())
                                                .then(argument("defender", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            ServerCommandSource src = ctx.getSource();
                                                            String attacker = StringArgumentType.getString(ctx, "attacker");
                                                            String defender = StringArgumentType.getString(ctx, "defender");

                                                            KingdomWarManager.War war = KingdomWarManager.declareWar(attacker, defender);
                                                            src.sendFeedback(() -> Text.of("Declared war: " + war.attacker + " -> " + war.defender + " (id=" + war.id + ")"), false);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                                .then(literal("list")
                                        .executes(ctx -> {
                                            ServerCommandSource src = ctx.getSource();
                                            StringBuilder sb = new StringBuilder();
                                            for (KingdomWarManager.War w : KingdomWarManager.getAllWars()) {
                                                sb.append(w.id).append(" : ").append(w.attacker).append(" -> ").append(w.defender).append(w.active ? " (active)" : " (ended)").append("\n");
                                            }
                                            String out = sb.length() == 0 ? "No wars." : sb.toString();
                                            src.sendFeedback(() -> Text.of(out), false);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
