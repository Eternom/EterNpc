package fr.eternom.eterNpc.listeners;

import fr.eternom.eterNpc.Main;
import fr.eternom.eterNpc.module.npc.NpcCommand;
import org.bukkit.command.PluginCommand;

import java.util.Objects;

public class Commands {

    public Commands(Main main) {
        PluginCommand npc = Objects.requireNonNull(main.getCommand("eternpc"), "Commande absente du plugin.yml : eternpc");
        NpcCommand executor = new NpcCommand(main.getNpcs(), main.getMessages());
        npc.setExecutor(executor);
        npc.setTabCompleter(executor);
    }
}
