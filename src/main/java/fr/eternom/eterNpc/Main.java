package fr.eternom.eterNpc;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterNpc.api.NpcApi;
import fr.eternom.eterNpc.listeners.Commands;
import fr.eternom.eterNpc.listeners.Events;
import fr.eternom.eterNpc.module.npc.NpcService;
import fr.eternom.eterNpc.module.npc.NpcSpawner;
import fr.eternom.eterNpc.module.npc.PlacementRepository;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Les PNJ du réseau (Mannequins natifs) pour tous les plugins Eter : apparition, regard, invincibilité, emplacements
 * en base. Chaque plugin donne l'apparence de ses PNJ et répond à leurs clics (API NpcApi).
 */
public final class Main extends JavaPlugin {

    private static final String REQUIRED_ETERLIB = "1.14.0";
    private static final String TABLE_PREFIX = "eternpc_";

    private Messages messages;
    private NpcService npcs;
    private NpcSpawner spawner;

    @Override
    public void onEnable() {
        try {
            if (!EterLib.requireVersion(this, REQUIRED_ETERLIB)) {
                return;
            }
        } catch (LinkageError tooOld) {
            getLogger().severe("EterLib " + REQUIRED_ETERLIB + " ou plus récent est nécessaire.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EterLib lib = EterLib.get();
        messages = lib.messages(this, "en_us", "fr_fr");
        // Un lobby (EterHub) : ses emplacements sont communs à tous les lobbys, même créés plus tard
        boolean lobby = getServer().getPluginManager().getPlugin("EterHub") != null;
        spawner = new NpcSpawner(this);
        npcs = new NpcService(this, new PlacementRepository(lib.database(TABLE_PREFIX)), spawner,
                lib.network(this, "eternpc", messages), lobby ? NpcApi.LOBBIES : lib.getServerName());
        getServer().getServicesManager().register(NpcApi.class, npcs, this, ServicePriority.Normal);

        new Commands(this);
        new Events(this);
        npcs.start();
    }

    @Override
    public void onDisable() {
        if (spawner != null) {
            spawner.despawnAll();
        }
    }

    public Messages getMessages() {
        return messages;
    }

    public NpcService getNpcs() {
        return npcs;
    }
}
