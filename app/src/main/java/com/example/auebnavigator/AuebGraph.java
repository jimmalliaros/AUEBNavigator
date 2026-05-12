package com.example.auebnavigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class AuebGraph {

    // Κλάση που κρατάει τις Συντεταγμένες (για το Heuristic του A*)
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
        // 1. Δημιουργία Κόμβων με Συντεταγμένες (Σχετικές μεταξύ τους)
        // Υποθέτουμε ότι το (0,0) είναι η Είσοδος.
        nodes.put("Κεντρική Είσοδος", new Node("Κεντρική Είσοδος", 0, 0));
        nodes.put("Κεντρικές Σκάλες", new Node("Κεντρικές Σκάλες", 0, 10)); // 10 μέτρα/βήματα πιο μέσα
        nodes.put("Αμφιθέατρο Α", new Node("Αμφιθέατρο Α", 5, 10)); // Δεξιά από τις σκάλες
        nodes.put("Κυλικείο", new Node("Κυλικείο", -5, 10)); // Αριστερά από τις σκάλες

        for (String key : nodes.keySet()) {
            adjList.put(key, new ArrayList<>());
        }

        // 2. Δημιουργία Ακμών (Διαδρομές και Φωνητικές Οδηγίες)
        addEdge("Κεντρική Είσοδος", "Κεντρικές Σκάλες", 10, "Προχώρα ευθεία για 10 βήματα μέχρι τις κεντρικές σκάλες.");
        addEdge("Κεντρικές Σκάλες", "Αμφιθέατρο Α", 5, "Στρίψε δεξιά και προχώρα 5 βήματα.");
        addEdge("Κεντρικές Σκάλες", "Κυλικείο", 8, "Στρίψε αριστερά και προχώρα 8 βήματα.");

        // (Μπορείς να προσθέσεις όλη τη σχολή εδώ!)
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