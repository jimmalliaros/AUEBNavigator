package com.example.auebnavigator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class AuebGraphTest {

    private AuebGraph graph;

    @Before
    public void setUp() {
        // Η κλάση AuebGraph χτίζει αυτόματα τον πραγματικό χάρτη της ΑΣΟΕΕ στο constructor της!
        graph = new AuebGraph();
    }

    // --- 1. HAPPY PATH TESTS (Τα βασικά καλά σενάρια) ---

    @Test
    public void testAStarFindsCorrectPathToT101() {
        List<AuebGraph.Edge> path = graph.findPathAStar("Κεντρική Είσοδος", "T101");

        assertNotNull("Το μονοπάτι δεν πρέπει να είναι null", path);
        assertFalse("Η λίστα με τα βήματα δεν πρέπει να είναι άδεια", path.isEmpty());

        AuebGraph.Edge lastEdge = path.get(path.size() - 1);
        assertEquals("Ο τελικός προορισμός πρέπει να είναι η T101", "T101", lastEdge.targetNode);
    }

    @Test
    public void testAStarPathToGroundFloorToilet() {
        List<AuebGraph.Edge> path = graph.findPathAStar("Κεντρική Είσοδος", "Τουαλέτες Ισόγειο");

        assertNotNull(path);
        assertEquals("Πρέπει να έχει ακριβώς 2 ακμές", 2, path.size());

        int totalSteps = 0;
        for (AuebGraph.Edge edge : path) totalSteps += edge.steps;
        assertEquals("Τα συνολικά βήματα πρέπει να είναι 18", 18, totalSteps);
    }

    // --- 2. EDGE CASES (Ακραίες Περιπτώσεις & Σφάλματα) ---

    @Test
    public void testAStarHandlesInvalidNodes() {
        // Δοκιμάζουμε έναν κόμβο που δεν υπάρχει στο HashMap
        List<AuebGraph.Edge> path = graph.findPathAStar("Κεντρική Είσοδος", "Κυλικείο");
        assertNull("Το μονοπάτι πρέπει να είναι null για ανύπαρκτους κόμβους", path);
    }

    @Test
    public void testAStarHandlesNullInputs() {
        // Τι γίνεται αν περάσουμε null; Η method containsKey() πρέπει να το διαχειριστεί και να γυρίσει null, όχι NullPointerException.
        List<AuebGraph.Edge> path1 = graph.findPathAStar(null, "T101");
        List<AuebGraph.Edge> path2 = graph.findPathAStar("Κεντρική Είσοδος", null);
        List<AuebGraph.Edge> path3 = graph.findPathAStar(null, null);

        assertNull("Πρέπει να επιστρέφει null αν η αφετηρία είναι null", path1);
        assertNull("Πρέπει να επιστρέφει null αν ο προορισμός είναι null", path2);
        assertNull("Πρέπει να επιστρέφει null αν και τα δύο είναι null", path3);
    }

    @Test
    public void testAStarSameStartAndGoal() {
        // Αν ζητήσουμε να πάμε εκεί που είμαστε ήδη, ο Α* πρέπει να γυρίσει μια ΑΔΕΙΑ λίστα (0 βήματα)
        List<AuebGraph.Edge> path = graph.findPathAStar("Κεντρική Είσοδος", "Κεντρική Είσοδος");

        assertNotNull("Δεν πρέπει να επιστρέψει null (ο κόμβος υπάρχει)", path);
        assertTrue("Η λίστα διαδρομής πρέπει να είναι εντελώς άδεια", path.isEmpty());
    }

    // --- 3. ALGORITHMIC COMPLEXITY TESTS (Δοκιμές Λογικής Αλγορίθμου) ---

    @Test
    public void testMultiFloorNavigationLogic() {
        // Τεστάρουμε αν η ευρετική συνάρτηση (Heuristic Z-axis) δουλεύει σωστά για να ανεβεί όροφο.
        // Είσοδος (Z=0) -> T103 (Z=1).
        List<AuebGraph.Edge> path = graph.findPathAStar("Κεντρική Είσοδος", "T103");

        assertNotNull("Πρέπει να βρει διαδρομή ανάμεσα σε ορόφους", path);

        // Ψάχνουμε αν το μονοπάτι περιέχει υποχρεωτικά τη "Βάση Σκάλας" και το "Κεφαλόσκαλο"
        boolean wentThroughStairs = false;
        for (AuebGraph.Edge edge : path) {
            if (edge.targetNode.equals("Κεφαλόσκαλο") || edge.targetNode.equals("Βάση Σκάλας")) {
                wentThroughStairs = true;
                break;
            }
        }
        assertTrue("Η διαδρομή από ισόγειο σε 1ο ΟΦΕΙΛΕΙ να περάσει από τη σκάλα", wentThroughStairs);
    }

    @Test
    public void testRoundTripConsistency() {
        // Αν το Είσοδος -> T101 έχει Χ βήματα, το T101 -> Είσοδος πρέπει να έχει τα ίδια ακριβώς βήματα
        List<AuebGraph.Edge> pathForward = graph.findPathAStar("Κεντρική Είσοδος", "T101");
        List<AuebGraph.Edge> pathBackward = graph.findPathAStar("T101", "Κεντρική Είσοδος");

        assertNotNull(pathForward);
        assertNotNull(pathBackward);

        int stepsForward = 0;
        for (AuebGraph.Edge edge : pathForward) stepsForward += edge.steps;

        int stepsBackward = 0;
        for (AuebGraph.Edge edge : pathBackward) stepsBackward += edge.steps;

        assertEquals("Η απόσταση Α->Β πρέπει να είναι ίδια με τη Β->Α", stepsForward, stepsBackward);
    }
}