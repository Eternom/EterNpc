package fr.eternom.eterNpc.module.own;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueTest {

    @Test
    void linesAndChoices() {
        Dialogue dialogue = Dialogue.parse("""
                Bienvenue à la guilde, {player} !

                Que veux-tu faire ?
                > Rejoindre un métier | eterjob:guild
                > Mes quêtes | eterjob:quests ; eterlib:sound entity.villager.yes
                """);
        assertEquals(List.of("Bienvenue à la guilde, {player} !", "Que veux-tu faire ?"), dialogue.lines());
        assertEquals(2, dialogue.choices().size());
        assertEquals("Rejoindre un métier", dialogue.choices().getFirst().label());
        assertEquals(List.of("eterjob:quests", "eterlib:sound entity.villager.yes"), dialogue.choices().get(1).actions());
        assertEquals(3, dialogue.allActions().size());
    }

    @Test
    void brokenChoicesAreIgnored() {
        Dialogue dialogue = Dialogue.parse("> sans barre\n> | eterjob:guild\n> Vide |  \nBonjour");
        assertTrue(dialogue.choices().isEmpty());
        assertEquals(List.of("Bonjour"), dialogue.lines());
    }

    @Test
    void emptyText() {
        assertTrue(Dialogue.parse(null).isEmpty());
        assertTrue(Dialogue.parse("  \n ").isEmpty());
    }
}
