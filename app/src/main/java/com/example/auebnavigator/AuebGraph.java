package com.example.auebnavigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class AuebGraph {

    // ✅ ΑΝΑΒΑΘΜΙΣΗ: Προσθήκη του άξονα Z για τον όροφο (0 = Ισόγειο, 1 = Πρώτος)
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
        // --- ΜΕΡΟΣ 1: ΙΣΟΓΕΙΟ (z = 0) ---
        // Η Είσοδος είναι στην αρχή του άξονα Y στο ισόγειο, και οι σκάλες 20 βήματα πιο πάνω
        nodes.put("Κεντρική Είσοδος", new Node("Κεντρική Είσοδος", 0, -20, 0));
        nodes.put("Σκάλες Ισογείου", new Node("Σκάλες Ισογείου", 0, 0, 0));

        // --- ΜΕΡΟΣ 2: ΠΡΩΤΟΣ ΟΡΟΦΟΣ (z = 1) (Βάσει του σκίτσου σου!) ---
        // Το Κεφαλόσκαλο είναι ακριβώς πάνω από τις σκάλες του ισογείου στον άξονα Z
        nodes.put("Κεφαλόσκαλο", new Node("Κεφαλόσκαλο", 0, 0, 1));
        nodes.put("Τουαλέτες", new Node("Τουαλέτες", -5, 0, 1));
        nodes.put("Ασανσέρ", new Node("Ασανσέρ", 5, 0, 1));

        nodes.put("Διασταύρωση T103", new Node("Διασταύρωση T103", 0, 10, 1));
        nodes.put("Διασταύρωση T102", new Node("Διασταύρωση T102", 0, 15, 1));
        nodes.put("Σημείο 20 Βημάτων", new Node("Σημείο 20 Βημάτων", 0, 20, 1));
        nodes.put("Διασταύρωση T101", new Node("Διασταύρωση T101", 0, 25, 1));
        nodes.put("Έξοδος Κινδύνου", new Node("Έξοδος Κινδύνου", 0, 28, 1));

        nodes.put("T103", new Node("T103", -5, 10, 1));
        nodes.put("T102", new Node("T102", -5, 15, 1));
        nodes.put("Γωνία T101", new Node("Γωνία T101", -5, 25, 1));
        nodes.put("T101", new Node("T101", -5, 28, 1));

        // Αρχικοποίηση λιστών γειτνίασης
        for (String key : nodes.keySet()) {
            adjList.put(key, new ArrayList<>());
        }

        // --- ΑΚΜΕΣ ΙΣΟΓΕΙΟΥ & ΣΥΝΔΕΣΗ ΟΡΟΦΩΝ (3D LINK) ---
        // Σύνδεση Εισόδου με Σκάλες στο Ισόγειο (20 βήματα ευθεία)
        addEdge("Κεντρική Είσοδος", "Σκάλες Ισογείου", 20, "Βρίσκεσαι στο ισόγειο. Προχώρα ευθεία για 20 βήματα μέχρι να βρεις τις σκάλες.");
        addEdge("Σκάλες Ισογείου", "Κεντρική Είσοδος", 20, "Προχώρα ευθεία 20 βήματα για να βγεις στην Κεντρική Είσοδο.");

        // Η ΜΑΓΙΚΗ ΓΕΦΥΡΑ: Σύνδεση Σκάλες Ισογείου (z=0) με Κεφαλόσκαλο 1ου ορόφου (z=1)
        addEdge("Σκάλες Ισογείου", "Κεφαλόσκαλο", 15, "Ανέβα τις σκάλες για να πας στον πρώτο όροφο. Μόλις φτάσεις, θα είσαι στο κεφαλόσκαλο.");
        addEdge("Κεφαλόσκαλο", "Σκάλες Ισογείου", 15, "Κατέβα τις σκάλες για να πας στο ισόγειο.");

        // --- ΑΚΜΕΣ ΠΡΩΤΟΥ ΟΡΟΦΟΥ (Όπως τις είχαμε) ---
        addEdge("Κεφαλόσκαλο", "Τουαλέτες", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για τις τουαλέτες.");
        addEdge("Τουαλέτες", "Κεφαλόσκαλο", 5, "Βγες από τις τουαλέτες, προχώρα ευθεία 5 βήματα μέχρι το κεφαλόσκαλο.");

        addEdge("Κεφαλόσκαλο", "Ασανσέρ", 5, "Στρίψε δεξιά and προχώρα 5 βήματα για το ασανσέρ.");
        addEdge("Ασανσέρ", "Κεφαλόσκαλο", 5, "Από το ασανσέρ, προχώρα ευθεία 5 βήματα μέχρι το κεφαλόσκαλο.");

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

        addEdge("Διασταύρωση T103", "T103", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για την αίθουσα T103.");
        addEdge("T103", "Διασταύρωση T103", 5, "Βγες από την αίθουσα και προχώρα ευθεία 5 βήματα μέχρι τον κεντρικό διάδρομο.");

        addEdge("Διασταύρωση T102", "T102", 5, "Στρίψε αριστερά και προχώρα 5 βήματα για την αίθουσα T102.");
        addEdge("T102", "Διασταύρωση T102", 5, "Βγες από την αίθουσα και προχώρα ευθεία 5 βήματα μέχρι τον κεντρικό διάδρομο.");

        addEdge("Διασταύρωση T101", "Γωνία T101", 5, "Στρίψε αριστερά και προχώρα 5 βήματα.");
        addEdge("Γωνία T101", "Διασταύρωση T101", 5, "Στρίψε δεξιά και προχώρα 5 βήματα μέχρι τον κεντρικό διάδρομο.");

        addEdge("Γωνία T101", "T101", 3, "Στρίψε δεξιά και προχώρα 3 βήματα για την αίθουσα T101.");
        addEdge("T101", "Γωνία T101", 3, "Βγες από την αίθουσα, προχώρα 3 βήματα ευθεία μέχρι τη γωνία.");
    }

    private void addEdge(String from, String to, int weight, String instr) {
        adjList.get(from).add(new Edge(to, weight, instr));
    }

    // ✅ ΑΝΑΒΑΘΜΙΣΗ: 3D Manhattan Distance με ποινή ορόφου
    private int heuristic(String nodeA, String nodeB) {
        Node a = nodes.get(nodeA);
        Node b = nodes.get(nodeB);
        if (a == null || b == null) return 0;

        int floorPenalty = 25; // 1 όροφος διαφορά ισούται με 25 "νοητά" βήματα για τον Α*

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