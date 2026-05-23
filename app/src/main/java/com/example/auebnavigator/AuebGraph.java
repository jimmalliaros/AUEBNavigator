package com.example.auebnavigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class AuebGraph {

    // 3D Node για υποστήριξη ορόφων
    public static class Node {
        String name;
        int x, y, z;

        public Node(String name, int x, int y, int z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

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

    private static class AStarNode implements Comparable<AStarNode> {
        String nodeName;
        int fScore;

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
        // ==========================================
        // --- ΜΕΡΟΣ 1: ΙΣΟΓΕΙΟ (ΟΡΟΦΟΣ Z = 0) ---
        // ==========================================
        nodes.put("Κεντρική Είσοδος", new Node("Κεντρική Είσοδος", 0, 0, 0));
        nodes.put("Διασταύρωση Τουαλέτες Ισόγειο", new Node("Διασταύρωση Τουαλέτες Ισόγειο", 0, 3, 0));
        nodes.put("Τουαλέτες Ισόγειο", new Node("Τουαλέτες Ισόγειο", 15, 3, 0));
        nodes.put("Διασταύρωση Σκάλας", new Node("Διασταύρωση Σκάλας", 0, 10, 0)); // 3 + 7 βήματα
        nodes.put("Βάση Σκάλας", new Node("Βάση Σκάλας", 5, 10, 0)); // 5 βήματα δεξιά
        nodes.put("Ασανσέρ Ισόγειο", new Node("Ασανσέρ Ισόγειο", 0, 26, 0)); // 10 + 16 βήματα

        // ==========================================
        // --- ΜΕΡΟΣ 2: ΠΡΩΤΟΣ ΟΡΟΦΟΣ (ΟΡΟΦΟΣ Z = 1) ---
        // ==========================================
        nodes.put("Κεφαλόσκαλο", new Node("Κεφαλόσκαλο", 0, 0, 1));

        // Διασταύρωση στα 5 βήματα
        nodes.put("Διασταύρωση Παροχών", new Node("Διασταύρωση Παροχών", 0, 5, 1));
        nodes.put("Τουαλέτες", new Node("Τουαλέτες", -5, 5, 1)); // Αριστερά
        nodes.put("Ασανσέρ", new Node("Ασανσέρ", 6, 5, 1));      // 6 βήματα δεξιά

        // Διασταύρωση στα 10 βήματα
        nodes.put("Διασταύρωση T103", new Node("Διασταύρωση T103", 0, 10, 1));
        nodes.put("T103", new Node("T103", -5, 10, 1)); // Αριστερά

        // Διασταύρωση στα 15 βήματα
        nodes.put("Διασταύρωση T102", new Node("Διασταύρωση T102", 0, 15, 1));
        nodes.put("T102", new Node("T102", -5, 15, 1)); // Αριστερά

        // Διασταύρωση στα 20 βήματα
        nodes.put("Διασταύρωση T101", new Node("Διασταύρωση T101", 0, 20, 1));
        nodes.put("Γωνία T101", new Node("Γωνία T101", -5, 20, 1)); // 5 βήματα αριστερά
        nodes.put("T101", new Node("T101", -5, 25, 1)); // Στρίβει και πάει προς την αίθουσα

        // Τέρμα διαδρόμου στα 25 βήματα
        nodes.put("Έξοδος Κινδύνου", new Node("Έξοδος Κινδύνου", 0, 25, 1));

        // Αρχικοποίηση λιστών γειτνίασης
        for (String key : nodes.keySet()) {
            adjList.put(key, new ArrayList<>());
        }

        // ==========================================
        // --- ΑΚΜΕΣ (EDGES) ΙΣΟΓΕΙΟΥ ---
        // ==========================================
        addEdge("Κεντρική Είσοδος", "Διασταύρωση Τουαλέτες Ισόγειο", 3, "Προχώρα ευθεία για 3 βήματα.");
        addEdge("Διασταύρωση Τουαλέτες Ισόγειο", "Κεντρική Είσοδος", 3, "Προχώρα ευθεία για 3 βήματα προς την έξοδο.");

        addEdge("Διασταύρωση Τουαλέτες Ισόγειο", "Τουαλέτες Ισόγειο", 15, "Στρίψε δεξιά και προχώρα 15 βήματα για τις τουαλέτες του ισογείου.");
        addEdge("Τουαλέτες Ισόγειο", "Διασταύρωση Τουαλέτες Ισόγειο", 15, "Βγες από τις τουαλέτες, προχώρα 15 βήματα ευθεία μέχρι τον διάδρομο.");

        addEdge("Διασταύρωση Τουαλέτες Ισόγειο", "Διασταύρωση Σκάλας", 7, "Προχώρα ευθεία στον κεντρικό διάδρομο για 7 βήματα.");
        addEdge("Διασταύρωση Σκάλας", "Διασταύρωση Τουαλέτες Ισόγειο", 7, "Προχώρα ευθεία στον διάδρομο για 7 βήματα.");

        addEdge("Διασταύρωση Σκάλας", "Βάση Σκάλας", 5, "Στρίψε δεξιά και προχώρα 5 βήματα. Η σκάλα είναι μπροστά σου.");
        addEdge("Βάση Σκάλας", "Διασταύρωση Σκάλας", 5, "Από τη βάση της σκάλας, προχώρα 5 βήματα ευθεία μέχρι τον κεντρικό διάδρομο.");

        addEdge("Διασταύρωση Σκάλας", "Ασανσέρ Ισόγειο", 16, "Συνέχισε ευθεία στον διάδρομο για 16 βήματα για το ασανσέρ.");
        addEdge("Ασανσέρ Ισόγειο", "Διασταύρωση Σκάλας", 16, "Από το ασανσέρ, προχώρα ευθεία στον διάδρομο για 16 βήματα.");

        // ==========================================
        // --- Η ΓΕΦΥΡΑ: ΣΥΝΔΕΣΗ ΟΡΟΦΩΝ ---
        // ==========================================
        addEdge("Βάση Σκάλας", "Κεφαλόσκαλο", 22, "Ανέβα τα 22 σκαλιά για τον πρώτο όροφο. Στο τέλος θα βρεθείς στο κεφαλόσκαλο.");
        addEdge("Κεφαλόσκαλο", "Βάση Σκάλας", 22, "Κατέβα τα 22 σκαλιά για να πας στο ισόγειο.");

        // ==========================================
        // --- ΑΚΜΕΣ (EDGES) ΠΡΩΤΟΥ ΟΡΟΦΟΥ ---
        // ==========================================
        addEdge("Κεφαλόσκαλο", "Διασταύρωση Παροχών", 5, "Προχώρα 5 βήματα ευθεία από το κεφαλόσκαλο.");
        addEdge("Διασταύρωση Παροχών", "Κεφαλόσκαλο", 5, "Προχώρα 5 βήματα. Το κεφαλόσκαλο είναι μπροστά σου.");

        addEdge("Διασταύρωση Παροχών", "Τουαλέτες", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για τις τουαλέτες.");
        addEdge("Τουαλέτες", "Διασταύρωση Παροχών", 5, "Βγες από τις τουαλέτες και προχώρα 5 βήματα μέχρι τον διάδρομο.");

        addEdge("Διασταύρωση Παροχών", "Ασανσέρ", 6, "Στρίψε δεξιά και προχώρα 6 βήματα για το ασανσέρ.");
        addEdge("Ασανσέρ", "Διασταύρωση Παροχών", 6, "Από το ασανσέρ, προχώρα 6 βήματα ευθεία μέχρι τον διάδρομο.");

        addEdge("Διασταύρωση Παροχών", "Διασταύρωση T103", 5, "Προχώρα ευθεία στον κεντρικό διάδρομο για 5 βήματα.");
        addEdge("Διασταύρωση T103", "Διασταύρωση Παροχών", 5, "Προχώρα ευθεία στον κεντρικό διάδρομο για 5 βήματα.");

        addEdge("Διασταύρωση T103", "T103", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για την αίθουσα Ταφ 103.");
        addEdge("T103", "Διασταύρωση T103", 5, "Βγες από την αίθουσα και προχώρα 5 βήματα μέχρι τον διάδρομο.");

        addEdge("Διασταύρωση T103", "Διασταύρωση T102", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");
        addEdge("Διασταύρωση T102", "Διασταύρωση T103", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");

        addEdge("Διασταύρωση T102", "T102", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για την αίθουσα Ταφ 102.");
        addEdge("T102", "Διασταύρωση T102", 5, "Βγες από την αίθουσα και προχώρα 5 βήματα μέχρι τον διάδρομο.");

        addEdge("Διασταύρωση T102", "Διασταύρωση T101", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");
        addEdge("Διασταύρωση T101", "Διασταύρωση T102", 5, "Συνέχισε ευθεία στον διάδρομο για 5 βήματα.");

        addEdge("Διασταύρωση T101", "Γωνία T101", 5, "Στρίψε αριστερά και προχώρα 5 βήματα.");
        addEdge("Γωνία T101", "Διασταύρωση T101", 5, "Στρίψε δεξιά και προχώρα 5 βήματα μέχρι τον κεντρικό διάδρομο.");

        addEdge("Γωνία T101", "T101", 5, "Στρίψε δεξιά και προχώρα 5 βήματα για την αίθουσα Ταφ 101.");
        addEdge("T101", "Γωνία T101", 5, "Βγες από την αίθουσα, προχώρα 5 βήματα ευθεία μέχρι τη γωνία.");

        addEdge("Διασταύρωση T101", "Έξοδος Κινδύνου", 5, "Προχώρα ευθεία 5 βήματα. Η έξοδος κινδύνου είναι μπροστά σου.");
        addEdge("Έξοδος Κινδύνου", "Διασταύρωση T101", 5, "Προχώρα ευθεία 5 βήματα.");
    }

    private void addEdge(String from, String to, int weight, String instr) {
        adjList.get(from).add(new Edge(to, weight, instr));
    }

    // 3D Manhattan Distance με Floor Penalty
    private int heuristic(String nodeA, String nodeB) {
        Node a = nodes.get(nodeA);
        Node b = nodes.get(nodeB);
        if (a == null || b == null) return 0;

        int floorPenalty = 25; // 1 όροφος διαφορά = 25 "νοητά" βήματα
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + (Math.abs(a.z - b.z) * floorPenalty);
    }

    public List<Edge> findPathAStar(String start, String goal) {
        if (!nodes.containsKey(start) || !nodes.containsKey(goal)) {
            return null;
        }

        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();
        Map<String, String> cameFromNode = new HashMap<>();
        Map<String, Edge> cameFromEdge = new HashMap<>();

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
                    cameFromNode.put(neighbor.targetNode, current);
                    cameFromEdge.put(neighbor.targetNode, neighbor);
                    gScore.put(neighbor.targetNode, tentative_gScore);

                    int fScore = tentative_gScore + heuristic(neighbor.targetNode, goal);
                    openSet.add(new AStarNode(neighbor.targetNode, fScore));
                }
            }
        }
        return null;
    }

    private List<Edge> reconstructPath(Map<String, String> cameFromNode, Map<String, Edge> cameFromEdge, String current) {
        List<Edge> totalPath = new ArrayList<>();
        while (cameFromNode.containsKey(current)) {
            totalPath.add(cameFromEdge.get(current));
            current = cameFromNode.get(current);
        }
        Collections.reverse(totalPath);
        return totalPath;
    }
}