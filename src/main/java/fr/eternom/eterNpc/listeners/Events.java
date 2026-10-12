package fr.eternom.eterNpc.listeners;

import fr.eternom.eterNpc.Main;
import fr.eternom.eterNpc.module.npc.NpcListener;

public class Events {

    public Events(Main main) {
        main.getServer().getPluginManager().registerEvents(new NpcListener(main.getNpcs()), main);
    }
}
