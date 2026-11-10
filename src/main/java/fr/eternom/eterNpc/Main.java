package fr.eternom.eterNpc;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterNpc.api.NpcApi;
import fr.eternom.eterNpc.listeners.Commands;
import fr.eternom.eterNpc.listeners.Events;
import fr.eternom.eterNpc.module.npc.NpcService;
import fr.eternom.eterNpc.module.npc.NpcSpawner;
import fr.eternom.eterNpc.module.npc.PlacementRepository;
import fr.eternom.eterNpc.module.own.OwnNpcRepository;
import fr.eternom.eterNpc.module.own.OwnNpcs;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Les PNJ du réseau (Mannequins natifs) pour tous les plugins Eter : apparition, regard, invincibilité, emplacements
 * en base. Chaque plugin donne l'apparence de ses PNJ et répond à leurs clics (API NpcApi).
 */
public final class Main extends JavaPlugin {

    private static final String REQUIRED_ETERLIB = "1.25.0";
    private static final String TABLE_PREFIX = "eternpc_";

    private Messages messages;
    private NpcService npcs;
    private NpcSpawner spawner;
    private OwnNpcs own;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        String scope = scope(lib);
        spawner = new NpcSpawner(this);
        npcs = new NpcService(this, new PlacementRepository(lib.database(TABLE_PREFIX)), spawner,
                lib.network(this, "eternpc", messages), scope);
        getLogger().info("Emplacements des PNJ : portée " + scope);
        getServer().getServicesManager().register(NpcApi.class, npcs, this, ServicePriority.Normal);
        own = new OwnNpcs(this, new OwnNpcRepository(lib.database(TABLE_PREFIX)), npcs, lib.network(this, "eternpc-own", messages), messages);

        new Commands(this);
        new Events(this);
        npcs.start();
        own.start();
        // Réputation d'EterLib : réputation du joueur × ratio du PNJ (/eternpc reputation)
        lib.getReputation().setRatioProvider(own::ratio);
    }

    /**
     * Portée des emplacements de ce serveur (config scope) : auto = « @lobbies » sur un lobby (EterHub), sinon le nom du
     * serveur ; ou une valeur imposée, ex : « @resource » dans le modèle des mondes ressource (noms changeants).
     */
    private String scope(EterLib lib) {
        String configured = getConfig().getString("scope", "auto").trim();
        if (!configured.isEmpty() && !configured.equalsIgnoreCase("auto")) {
            return configured;
        }
        return getServer().getPluginManager().getPlugin("EterHub") != null ? NpcApi.LOBBIES : lib.getServerName();
    }

    @Override
    public void onDisable() {
        if (own != null) {
            own.stop();
        }
        if (spawner != null) {
            spawner.despawnAll();
        }
    }

    public Messages getMessages() {
        return messages;
    }

    public OwnNpcs getOwn() {
        return own;
    }

    public NpcService getNpcs() {
        return npcs;
    }
}
