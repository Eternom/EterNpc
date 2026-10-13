package fr.eternom.eterNpc.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Les PNJ du réseau (Mannequins natifs). EterNpc les fait apparaître, tourner la tête et réapparaître, et enregistre
 * OÙ ils sont placés. Le plugin qui possède un PNJ garde ses données (rôle, boutique...) et donne son apparence avec
 * {@link #define} à chaque démarrage ; un clic arrive par {@link NpcClickEvent}.
 * Un emplacement posé sur un lobby (EterHub) est commun à tous les lobbys, même créés plus tard.
 */
public interface NpcApi {

    static Optional<NpcApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(NpcApi.class));
    }

    /**
     * Thread principal. Définit ou met à jour un PNJ sur ce serveur : il apparaît là où il est placé, et ses Mannequins
     * déjà là changent d'apparence sans réapparaître. Rien n'est enregistré : à redonner à chaque démarrage. Les PNJ
     * d'un plugin disparaissent quand il s'arrête.
     */
    void define(Plugin owner, NpcLook look);

    /** Thread principal. Le PNJ disparaît de ce serveur (ses emplacements restent enregistrés). */
    void undefine(String id);

    /** Thread principal. Place le PNJ là où est location (direction comprise) ; done ensuite, sur le thread principal. */
    void place(String id, Location location, Runnable done);

    /**
     * Thread principal. Retire l'emplacement le plus proche (rayon en blocs) parmi les PNJ acceptés par which (ex : ceux
     * d'un plugin) ; done reçoit le PNJ retiré, ou rien.
     */
    void removeNearest(Location location, double radius, Predicate<String> which, Consumer<Optional<String>> done);

    /** Comme ci-dessus, parmi tous les PNJ (EterNpc 1.0.0). */
    default void removeNearest(Location location, double radius, Consumer<Optional<String>> done) {
        removeNearest(location, radius, npc -> true, done);
    }

    /** Bloquant. Supprime tous les emplacements de ce PNJ, sur tout le réseau. */
    void deletePlacements(String id);

    /**
     * Bloquant. Ajoute un emplacement tel quel (reprise des données d'un plugin). scope : nom du serveur, ou
     * {@link #LOBBIES} pour tous les lobbys.
     */
    void importPlacement(String id, String scope, String world, double x, double y, double z, float yaw);

    /** Le PNJ représenté par cette entité, s'il en est un. */
    Optional<String> npcOf(Entity entity);

    /** Nombre d'emplacements de ce PNJ sur ce serveur. */
    int placedHere(String id);

    /** Portée des emplacements posés sur un lobby : communs à tous les lobbys. */
    String LOBBIES = "@lobbies";
}
