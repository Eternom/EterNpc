package fr.eternom.eterNpc.module.npc;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;

import java.util.List;
import java.util.Map;

/**
 * Où sont placés les PNJ (eternpc_placements) : un PNJ peut être placé autant de fois qu'on veut, sur n'importe quel
 * serveur. scope : le nom du serveur, NpcApi.LOBBIES (tous les lobbys) ou NpcApi.RESOURCE (tous les mondes ressource).
 * actions : actions du clic droit propres à cet emplacement (remplacent celles du PNJ), null sinon.
 * Appels bloquants : hors du thread principal.
 */
public class PlacementRepository {

    private static final String PLACEMENTS = "placements";

    public record Placement(long id, String npc, String scope, String world, double x, double y, double z, float yaw,
                            String actions) {

        public Placement(long id, String npc, String scope, String world, double x, double y, double z, float yaw) {
            this(id, npc, scope, world, x, y, z, yaw, null);
        }

        /** Même place (pour savoir s'il faut faire réapparaître le Mannequin). */
        boolean samePlace(Placement other) {
            return other != null && npc.equals(other.npc) && world.equals(other.world) && x == other.x && y == other.y
                    && z == other.z && yaw == other.yaw;
        }
    }

    private final Database database;

    public PlacementRepository(Database database) {
        this.database = database;
        database.createTable(PLACEMENTS,
                Column.of("id", Column.Type.LONG).autoIncrement(),
                Column.of("npc", Column.Type.STRING).length(64).notNull(),
                Column.of("scope", Column.Type.STRING).length(64).notNull(),
                Column.of("world", Column.Type.STRING).length(64).notNull(),
                Column.of("x", Column.Type.DOUBLE).notNull(),
                Column.of("y", Column.Type.DOUBLE).notNull(),
                Column.of("z", Column.Type.DOUBLE).notNull(),
                Column.of("yaw", Column.Type.FLOAT).notNull());
        database.addColumn(PLACEMENTS, Column.of("actions", Column.Type.TEXT));
    }

    public List<Placement> of(String scope) {
        return database.get(PLACEMENTS, Map.of("scope", scope)).stream().map(PlacementRepository::toPlacement).toList();
    }

    /** Tous les emplacements d'un PNJ, sur tout le réseau (menu, téléportation). */
    public List<Placement> ofNpc(String npc) {
        return database.get(PLACEMENTS, Map.of("npc", npc)).stream().map(PlacementRepository::toPlacement).toList();
    }

    /** Tous les emplacements du réseau (nettoyage). */
    public List<Placement> all() {
        return database.get(PLACEMENTS, Map.of()).stream().map(PlacementRepository::toPlacement).toList();
    }

    /** @return l'identifiant de l'emplacement */
    public long add(String npc, String scope, String world, double x, double y, double z, float yaw) {
        return database.insertReturningId(PLACEMENTS, Map.of("npc", npc, "scope", scope, "world", world, "x", x, "y", y, "z", z, "yaw", yaw));
    }

    public void move(long id, String world, double x, double y, double z, float yaw) {
        database.update(PLACEMENTS, Map.of("world", world, "x", x, "y", y, "z", z, "yaw", yaw), Map.of("id", id));
    }

    public void setActions(long id, String actions) {
        database.execute("UPDATE " + database.table(PLACEMENTS) + " SET actions = ? WHERE id = ?", actions, id);
    }

    public void remove(long id) {
        database.delete(PLACEMENTS, Map.of("id", id));
    }

    public void removeNpc(String npc) {
        database.delete(PLACEMENTS, Map.of("npc", npc));
    }

    /** @return le nombre d'emplacements supprimés */
    public int removeByPrefix(String prefix) {
        String escaped = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return database.execute("DELETE FROM " + database.table(PLACEMENTS) + " WHERE npc LIKE ?", escaped + "%");
    }

    private static Placement toPlacement(Row row) {
        return new Placement(row.getLong("id"), row.getString("npc"), row.getString("scope"), row.getString("world"),
                row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"), row.getString("actions"));
    }
}
