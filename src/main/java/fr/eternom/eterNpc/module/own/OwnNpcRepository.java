package fr.eternom.eterNpc.module.own;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les PNJ d'EterNpc (eternpc_npcs), communs au réseau. Appels bloquants : hors du thread principal.
 * Les actions et le dialogue sont du texte (une ligne par action), l'équipement « HAND=IRON_PICKAXE;HEAD=... ».
 */
public class OwnNpcRepository {

    private static final String NPCS = "npcs";

    private static final String REPUTATION = "reputation";
    private final Database database;

    public OwnNpcRepository(Database database) {
        this.database = database;
        database.createTable(REPUTATION,
                Column.of("name", Column.Type.STRING).length(32).primaryKey(),
                Column.of("ratio", Column.Type.DOUBLE).notNull());
        database.createTable(NPCS,
                Column.of("name", Column.Type.STRING).length(32).primaryKey(),
                Column.of("display", Column.Type.TEXT),
                Column.of("description", Column.Type.TEXT),
                Column.of("skin", Column.Type.STRING).length(16),
                Column.of("texture_value", Column.Type.TEXT),
                Column.of("texture_signature", Column.Type.TEXT),
                Column.of("actions_right", Column.Type.TEXT),
                Column.of("actions_left", Column.Type.TEXT),
                Column.of("actions_shift", Column.Type.TEXT),
                Column.of("dialogue", Column.Type.TEXT),
                Column.of("equipment", Column.Type.TEXT),
                Column.of("pose", Column.Type.STRING).length(16),
                Column.of("scale", Column.Type.DOUBLE),
                Column.of("look", Column.Type.BOOLEAN),
                Column.of("look_range", Column.Type.DOUBLE),
                Column.of("glow", Column.Type.BOOLEAN),
                Column.of("permission", Column.Type.STRING).length(128),
                Column.of("sound", Column.Type.STRING).length(128),
                Column.of("cooldown", Column.Type.INT),
                Column.of("clicks", Column.Type.LONG));
    }

    /** Ratio de réputation de chaque PNJ (EterLib : réputation × ratio) ; absent : 1. */
    public Map<String, Double> ratios() {
        Map<String, Double> ratios = new java.util.HashMap<>();
        database.get(REPUTATION, Map.of()).forEach(row -> ratios.put(row.getString("name"), row.getDouble("ratio")));
        return ratios;
    }

    public void setRatio(String name, double ratio) {
        if (ratio == 1) {
            database.delete(REPUTATION, Map.of("name", name));
        } else {
            database.set(REPUTATION, Map.of("name", name, "ratio", ratio), "name");
        }
    }

    public List<OwnNpc> all() {
        return database.get(NPCS, Map.of()).stream().map(OwnNpcRepository::toNpc).toList();
    }

    /** Crée le PNJ s'il n'existe pas ; false s'il existait déjà (même sur un autre serveur). */
    public boolean create(OwnNpc npc) {
        return database.execute("INSERT IGNORE INTO " + database.table(NPCS) + " (name, display, clicks) VALUES (?, ?, 0)",
                npc.name(), npc.display()) > 0 && saveAnd(npc);
    }

    private boolean saveAnd(OwnNpc npc) {
        save(npc);
        return true;
    }

    /** Enregistre tout, sauf le compteur de clics (qui ne fait que s'ajouter). */
    public void save(OwnNpc npc) {
        Map<String, Object> values = new HashMap<>();
        values.put("display", npc.display());
        values.put("description", npc.description());
        values.put("skin", npc.skin());
        values.put("texture_value", npc.textureValue());
        values.put("texture_signature", npc.textureSignature());
        values.put("actions_right", String.join("\n", npc.right()));
        values.put("actions_left", String.join("\n", npc.left()));
        values.put("actions_shift", String.join("\n", npc.shift()));
        values.put("dialogue", npc.dialogue());
        values.put("equipment", OwnNpc.equipmentText(npc.equipment()));
        values.put("pose", npc.pose());
        values.put("scale", npc.scale());
        values.put("look", npc.look());
        values.put("look_range", npc.lookRange());
        values.put("glow", npc.glow());
        values.put("permission", npc.permission());
        values.put("sound", npc.sound());
        values.put("cooldown", npc.cooldown());
        database.update(NPCS, values, Map.of("name", npc.name()));
    }

    public void delete(String name) {
        database.delete(NPCS, Map.of("name", name));
        database.delete(REPUTATION, Map.of("name", name));
    }

    /** Ajoute des clics au compteur (statistiques). */
    public void addClicks(String name, long clicks) {
        database.execute("UPDATE " + database.table(NPCS) + " SET clicks = COALESCE(clicks, 0) + ? WHERE name = ?", clicks, name);
    }

    private static List<String> lines(String text) {
        return text == null || text.isBlank() ? List.of() : List.of(text.split("\n"));
    }

    private static OwnNpc toNpc(Row row) {
        return new OwnNpc(row.getString("name"), orEmpty(row.getString("display")), orEmpty(row.getString("description")),
                row.getString("skin"), row.getString("texture_value"), row.getString("texture_signature"),
                lines(row.getString("actions_right")), lines(row.getString("actions_left")), lines(row.getString("actions_shift")),
                orEmpty(row.getString("dialogue")), OwnNpc.parseEquipment(row.getString("equipment")),
                row.getString("pose") == null ? "STANDING" : row.getString("pose"),
                row.get("scale") == null ? 1 : row.getDouble("scale"),
                row.get("look") == null || row.getBoolean("look"),
                row.get("look_range") == null ? 8 : row.getDouble("look_range"),
                row.getBoolean("glow"), row.getString("permission"), orEmpty(row.getString("sound")),
                row.getInt("cooldown"), row.getLong("clicks"));
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }
}
