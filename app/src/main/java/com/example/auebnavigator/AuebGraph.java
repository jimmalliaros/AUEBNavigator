package com.example.auebnavigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class AuebGraph {

    // Κλάση που κρατάει τις Συντεταγμένες (για το Heuristic του A
    // *)
    public static class Node {
        String name;
        int x, y;

        public Node(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }

    // Η Ακμή (Ο διάδρομος που ενώνει δύο σημεία)
    public static class Edge {
        String targetNode;
        int steps;
        String instruction;

        public Edge(String targetNode, int steps, String instruction) {
            this.targetNode = targetNode;
            this.steps = steps;
            this.instruction = instruction;
        }
    }

    // Το "κουτί" για την Ουρά Προτεραιότητας του A*
    private static class AStarNode implements Comparable<AStarNode> {
        String nodeName;
        int fScore; // Το g(n) + h(n)

        public AStarNode(String nodeName, int fScore) {
            this.nodeName = nodeName;
            this.fScore = fScore;
        }

        @Override
        public int compareTo(AStarNode other) {
            return Integer.compare(this.fScore, other.fScore);
        }
    }

    private Map<String, Node> nodes = new HashMap<>();
    private Map<String, List<Edge>> adjList = new HashMap<>();

    public AuebGraph() {
        buildGraph();
    }

    private void buildGraph() {
        // 1. Δημιουργία Κόμβων με Συντεταγμένες (Grid)
        // Το (0,0) είναι η αρχή μας, το Κεφαλόσκαλο του 1ου ορόφου.
        nodes.put("Κεφαλόσκαλο", new Node("Κεφαλόσκαλο", 0, 0));
        nodes.put("Τουαλέτες", new Node("Τουαλέτες", -5, 0)); // Υποθέτουμε 5 βήματα αριστερά
        nodes.put("Ασανσέρ", new Node("Ασανσέρ", 5, 0));      // Υποθέτουμε 5 βήματα δεξιά

        // Οι κόμβοι πάνω στον κεντρικό διάδρομο (Άξονας Y)
        nodes.put("Διασταύρωση T103", new Node("Διασταύρωση T103", 0, 10)); // Στα 10 βήματα
        nodes.put("Διασταύρωση T102", new Node("Διασταύρωση T102", 0, 15)); // Στα 15 βήματα
        nodes.put("Σημείο 20 Βημάτων", new Node("Σημείο 20 Βημάτων", 0, 20)); // Απλό checkpoint
        nodes.put("Διασταύρωση T101", new Node("Διασταύρωση T101", 0, 25)); // Στα 25 βήματα
        nodes.put("Έξοδος Κινδύνου", new Node("Έξοδος Κινδύνου", 0, 28)); // Λίγο μετά το 25

        // Οι τελικοί προορισμοί (Αίθουσες)
        nodes.put("T103", new Node("T103", -5, 10)); // 5 βήματα αριστερά από τη διασταύρωση
        nodes.put("T102", new Node("T102", -5, 15)); // 5 βήματα αριστερά από τη διασταύρωση

        // Το T101 κάνει "Γωνία" σύμφωνα με το σχέδιο (5 αριστερά, και μετά πάνω)
        nodes.put("Γωνία T101", new Node("Γωνία T101", -5, 25));
        nodes.put("T101", new Node("T101", -5, 28)); // 3 βήματα πιο πάνω από τη γωνία

        // Αρχικοποίηση των λιστών γειτνίασης
        for (String key : nodes.keySet()) {
            adjList.put(key, new ArrayList<>());
        }

        // 2. Δημιουργία Ακμών (Διαδρομές και Φωνητικές Οδηγίες)
        // Προσοχή: Πρέπει να μπαίνουν αμφίδρομα! (Α -> Β και Β -> Α)

        // --- Βάση (Κεφαλόσκαλο) ---
        addEdge("Κεφαλόσκαλο", "Τουαλέτες", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για τις τουαλέτες.");
        addEdge("Τουαλέτες", "Κεφαλόσκαλο", 5, "Βγες από τις τουαλέτες, προχώρα ευθεία 5 βήματα μέχρι το κεφαλόσκαλο.");

        addEdge("Κεφαλόσκαλο", "Ασανσέρ", 5, "Στρίψε δεξιά και προχώρα 5 βήματα για το ασανσέρ.");
        addEdge("Ασανσέρ", "Κεφαλόσκαλο", 5, "Από το ασανσέρ, προχώρα ευθεία 5 βήματα μέχρι το κεφαλόσκαλο.");

        // --- Κεντρικός Διάδρομος (Προς τα πάνω) ---
        addEdge("Κεφαλόσκαλο", "Διασταύρωση T103", 10, "Προχώρα ευθεία στον κεντρικό διάδρομο για 10 βήματα.");
        addEdge("Διασταύρωση T103", "Κεφαλόσκαλο", 10, "Προχώρα ευθεία για 10 βήματα. Το κεφαλόσκαλο είναι μπροστά σου.");

        addEdge("Διασταύρωση T103", "Διασταύρωση T102", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");
        addEdge("Διασταύρωση T102", "Διασταύρωση T103", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");

        addEdge("Διασταύρωση T102", "Σημείο 20 Βημάτων", 5, "Συνέχισε ευθεία στον διάδρομο για άλλα 5 βήματα.");
        addEdge("Σημείο 20 Βημάτων", "Διασταύρωση T102", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");

        addEdge("Σημείο 20 Βημάτων", "Διασταύρωση T101", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");
        addEdge("Διασταύρωση T101", "Σημείο 20 Βημάτων", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");

        addEdge("Διασταύρωση T101", "Έξοδος Κινδύνου", 3, "Προχώρα ευθεία 3 βήματα. Η έξοδος κινδύνου είναι μπροστά σου.");
        addEdge("Έξοδος Κινδύνου", "Διασταύρωση T101", 3, "Προχώρα ευθεία 3 βήματα.");

        // --- Αριστεροί Κλάδοι (Αίθουσες) ---
        addEdge("Διασταύρωση T103", "T103", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για την αίθουσα T103.");
        addEdge("T103", "Διασταύρωση T103", 5, "Βγες από την αίθουσα και προχώρα ευθεία 5 βήματα μέχρι τον κεντρικό διάδρομο.");

        addEdge("Διασταύρωση T102", "T102", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για την αίθουσα T102.");
        addEdge("T102", "Διασταύρωση T102", 5, "Βγες από την αίθουσα και προχώρα ευθεία 5 βήματα μέχρι τον κεντρικό διάδρομο.");

        // Το "σπάσιμο" του T101
        addEdge("Διασταύρωση T101", "Γωνία T101", 5, "Στρίψε αριστερά και προχώρα 5 βήματα.");
        addEdge("Γωνία T101", "Διασταύρωση T101", 5, "Στρίψε δεξιά και προχώρα 5 βήματα μέχρι τον κεντρικό διάδρομο.");

        addEdge("Γωνία T101", "T101", 3, "Στρίψε δεξιά και προχώρα 3 βήματα για την αίθουσα T101.");
        addEdge("T101", "Γωνία T101", 3, "Βγες από την αίθουσα, προχώρα 3 βήματα ευθεία μέχρι τη γωνία.");
    }

    private void addEdge(String from, String to, int weight, String instr) {
        adjList.get(from).add(new Edge(to, weight, instr));
    }

    // Η Ευρετική Συνάρτηση (Heuristic - Manhattan Distance)
    private int heuristic(String nodeA, String nodeB) {
        Node a = nodes.get(nodeA);
        Node b = nodes.get(nodeB);
        if (a == null || b == null) return 0;
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    // 🔥 Ο ΑΛΓΟΡΙΘΜΟΣ Α* 🔥
    // Επιστρέφει μια λίστα με τα "βήματα" (Edges) που πρέπει να ακολουθήσει ο χρήστης
    public List<Edge> findPathAStar(String start, String goal) {
        if (!nodes.containsKey(start) || !nodes.containsKey(goal)) {
            return null; // Λάθος τοποθεσίες
        }

        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();
        Map<String, String> cameFromNode = new HashMap<>();
        Map<String, Edge> cameFromEdge = new HashMap<>(); // Κρατάμε ποια ακμή πήραμε για να φτάσουμε

        Map<String, Integer> gScore = new HashMap<>();
        for (String node : nodes.keySet()) gScore.put(node, Integer.MAX_VALUE);
        gScore.put(start, 0);

        openSet.add(new AStarNode(start, heuristic(start, goal)));

        while (!openSet.isEmpty()) {
            String current = openSet.poll().nodeName;

            if (current.equals(goal)) {
                return reconstructPath(cameFromNode, cameFromEdge, current);
            }

            for (Edge neighbor : adjList.get(current)) {
                int tentative_gScore = gScore.get(current) + neighbor.steps;

                if (tentative_gScore < gScore.get(neighbor.targetNode)) {
                    // Βρήκαμε καλύτερο/συντομότερο δρόμο για αυτόν τον κόμβο!
                    cameFromNode.put(neighbor.targetNode, current);
                    cameFromEdge.put(neighbor.targetNode, neighbor);
                    gScore.put(neighbor.targetNode, tentative_gScore);

                    int fScore = tentative_gScore + heuristic(neighbor.targetNode, goal);
                    openSet.add(new AStarNode(neighbor.targetNode, fScore));
                }
            }
        }
        return null; // Δεν υπάρχει διαδρομή
    }

    // Ξετυλίγει το "κουβάρι" προς τα πίσω για να μας δώσει τη διαδρομή στη σωστή σειρά
    private List<Edge> reconstructPath(Map<String, String> cameFromNode, Map<String, Edge> cameFromEdge, String current) {
        List<Edge> totalPath = new ArrayList<>();
        while (cameFromNode.containsKey(current)) {
            totalPath.add(cameFromEdge.get(current));
            current = cameFromNode.get(current);
        }
        Collections.reverse(totalPath); // Τη γυρνάμε ανάποδα γιατί την πήραμε από το τέλος προς την αρχή
        return totalPath;
    }
}