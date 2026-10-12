package fr.eternom.eterNpc.module.npc;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;

import java.util.List;
import java.util.Map;

/**
 * Où sont placés les PNJ (eternpc_placements) : un PNJ peut être placé autant de fois qu'on veut, sur n'importe quel
 * serveur. scope : le nom du serveur, ou NpcApi.LOBBIES (tous les lobbys). Appels bloquants : hors du thread principal.
 */
public class PlacementRepository {

    private static final String PLACEMENTS = "placements";

    public record Placement(long id, String npc, String scope, String world, double x, double y, double z, float yaw) {
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
    }

    public List<Placement> of(String scope) {
        return database.get(PLACEMENTS, Map.of("scope", scope)).stream().map(PlacementRepository::toPlacement).toList();
    }

    /** @return l'identifiant de l'emplacement */
    public long add(String npc, String scope, String world, double x, double y, double z, float yaw) {
        database.insert(PLACEMENTS, Map.of("npc", npc, "scope", scope, "world", world, "x", x, "y", y, "z", z, "yaw", yaw));
        return database.query("SELECT MAX(id) AS id FROM " + database.table(PLACEMENTS) + " WHERE npc = ? AND scope = ?", npc, scope)
                .getFirst().getLong("id");
    }

    public void remove(long id) {
        database.delete(PLACEMENTS, Map.of("id", id));
    }

    public void removeNpc(String npc) {
        database.delete(PLACEMENTS, Map.of("npc", npc));
    }

    private static Placement toPlacement(Row row) {
        return new Placement(row.getLong("id"), row.getString("npc"), row.getString("scope"), row.getString("world"),
                row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"));
    }
}
