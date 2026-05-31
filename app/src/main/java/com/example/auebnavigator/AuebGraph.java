package com.example.auebnavigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 AuebGraph is a map of the building as a weighted graph,plus an A* pathfinder

 Each location is a Node, each walkable connection between two locations is an
 edge that carries the number of steps and a spoken instruction. findPathAStar()
 computes the cheapest route (fewest steps) between two locations and returns it
 as an ordered list of Edges (each with its turn-by-turn instruction).
 */

public class AuebGraph {

    //variables for the location in the building. The z coordinate is the floor number
    public static class Node {
        String name;
        int x, y, z;

        public Node(String name, int x, int y, int z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z; // z = floor number
        }
    }


    //A directed, walkable connection from one node to another
    public static class Edge {
        String targetNode; //destination Node
        int steps;
        String instruction; //Spoken instruction

        public Edge(String targetNode, int steps, String instruction) {
            this.targetNode = targetNode;
            this.steps = steps;
            this.instruction = instruction;
        }
    }


     //Wrapper used inside the A* priority queue. Orders nodes by fScore(estimated total cost), so the most promising node is always explored first
    private static class AStarNode implements Comparable<AStarNode> {
        String nodeName;
        int fScore; //gScore (cost so far) + heuristic (estimated cost to goal)

        public AStarNode(String nodeName, int fScore) {
            this.nodeName = nodeName;
            this.fScore = fScore;
        }

        @Override
        public int compareTo(AStarNode other) {
            return Integer.compare(this.fScore, other.fScore); //smaller fScore = higher priority
        }
    }

    private Map<String, Node> nodes = new HashMap<>(); //Nodes
    private Map<String, List<Edge>> adjList = new HashMap<>();  //outgoing edges (adjacency list) for each node

    public AuebGraph() {
        buildGraph();
    }

    //Hard-coding of all nodes and edges of the ground floor and first floor of the building
    private void buildGraph() {
        // Ground floor (z=0)
        nodes.put("Κεντρική Είσοδος", new Node("Κεντρική Είσοδος", 0, 0, 0));
        nodes.put("Διασταύρωση Τουαλέτες Ισόγειο", new Node("Διασταύρωση Τουαλέτες Ισόγειο", 0, 3, 0));
        nodes.put("Τουαλέτες Ισόγειο", new Node("Τουαλέτες Ισόγειο", 15, 3, 0));
        nodes.put("Διασταύρωση Σκάλας", new Node("Διασταύρωση Σκάλας", 0, 10, 0)); // 3 + 7 steps
        nodes.put("Βάση Σκάλας", new Node("Βάση Σκάλας", 5, 10, 0)); // 5 steps right
        nodes.put("Ασανσέρ Ισόγειο", new Node("Ασανσέρ Ισόγειο", 0, 26, 0)); // 10 + 16 steps

        //First floor (z = 1)
        nodes.put("Κεφαλόσκαλο", new Node("Κεφαλόσκαλο", 0, 0, 1));

        // Junction (Διασταύρωση) at 5 steps
        nodes.put("Διασταύρωση Παροχών", new Node("Διασταύρωση Παροχών", 0, 5, 1));
        nodes.put("Τουαλέτες", new Node("Τουαλέτες", -5, 5, 1)); // Αριστερά
        nodes.put("Ασανσέρ", new Node("Ασανσέρ", 6, 5, 1));      // 6 βήματα δεξιά

        // Junction (Διασταύρωση) at 10 steps
        nodes.put("Διασταύρωση T103", new Node("Διασταύρωση T103", 0, 10, 1));
        nodes.put("T103", new Node("T103", -5, 10, 1)); // Αριστερά

        // Junction (Διασταύρωση) at 15 steps
        nodes.put("Διασταύρωση T102", new Node("Διασταύρωση T102", 0, 15, 1));
        nodes.put("T102", new Node("T102", -5, 15, 1)); // Αριστερά

        // Junction (Διασταύρωση) at 20 steps
        nodes.put("Διασταύρωση T101", new Node("Διασταύρωση T101", 0, 20, 1));
        nodes.put("Γωνία T101", new Node("Γωνία T101", -5, 20, 1)); // 5 βήματα αριστερά
        nodes.put("T101", new Node("T101", -5, 25, 1)); // Στρίβει και πάει προς την αίθουσα

        // End of the corridor at 25 steps
        nodes.put("Έξοδος Κινδύνου", new Node("Έξοδος Κινδύνου", 0, 25, 1));

        //Create an empty adjacency list for every node before adding edges
        for (String key : nodes.keySet()) {
            adjList.put(key, new ArrayList<>());
        }



        // --- Ground floor edges ---
        // Edges are added in both directions so the user can navigate either way.
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

        // Connection of the two floors (the staircase)
        addEdge("Βάση Σκάλας", "Κεφαλόσκαλο", 22, "Ανέβα τα 22 σκαλιά για τον πρώτο όροφο. Στο τέλος θα βρεθείς στο κεφαλόσκαλο.");
        addEdge("Κεφαλόσκαλο", "Βάση Σκάλας", 22, "Κατέβα τα 22 σκαλιά για να πας στο ισόγειο.");

        // --- First floor edges ---
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

     //A* heuristic: estimated remaining cost from node A to node B using 3D Manhattan distance (|dx| + |dy|) plus a penalty per floor difference,so changing floors is treated as expensive (it requires stairs/elevator)
    private int heuristic(String nodeA, String nodeB) {
        Node a = nodes.get(nodeA);
        Node b = nodes.get(nodeB);
        if (a == null || b == null) return 0;

        int floorPenalty = 25; //floor penalty = 25 steps
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + (Math.abs(a.z - b.z) * floorPenalty);
    }

    //Finding the lowest-cost route from start to goal using the A* algorithm
    public List<Edge> findPathAStar(String start, String goal) {
        if (!nodes.containsKey(start) || !nodes.containsKey(goal)) {
            return null;
        }

        // Nodes to explore, ordered by best (lowest) fScore first
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();
        //For each node, remember how we reached it (to rebuild the path at the end)
        Map<String, String> cameFromNode = new HashMap<>();
        Map<String, Edge> cameFromEdge = new HashMap<>();

        //gScore = cheapest known cost from start to each node (start at "infinity")
        Map<String, Integer> gScore = new HashMap<>();
        for (String node : nodes.keySet()) gScore.put(node, Integer.MAX_VALUE);
        gScore.put(start, 0);

        openSet.add(new AStarNode(start, heuristic(start, goal)));

        while (!openSet.isEmpty()) {
            //Take the most promising node (lowest estimated total cost)
            String current = openSet.poll().nodeName;

            //Reached the goal: rebuild and return the route
            if (current.equals(goal)) {
                return reconstructPath(cameFromNode, cameFromEdge, current);
            }

            //Try every neighbour reachable from the current node
            for (Edge neighbor : adjList.get(current)) {
                int tentative_gScore = gScore.get(current) + neighbor.steps;


                //Found a cheaper way to reach this neighbour: record it
                if (tentative_gScore < gScore.get(neighbor.targetNode)) {
                    cameFromNode.put(neighbor.targetNode, current);
                    cameFromEdge.put(neighbor.targetNode, neighbor);
                    gScore.put(neighbor.targetNode, tentative_gScore);

                    int fScore = tentative_gScore + heuristic(neighbor.targetNode, goal);
                    openSet.add(new AStarNode(neighbor.targetNode, fScore));
                }
            }
        }
        return null; //no route found
    }

     //Path reconstruction backwards from the goal to the start, collecting the edges used, then reversing them so the path reads start to goal
    private List<Edge> reconstructPath(Map<String, String> cameFromNode, Map<String, Edge> cameFromEdge, String current) {
        List<Edge> totalPath = new ArrayList<>();
        while (cameFromNode.containsKey(current)) {
            totalPath.add(cameFromEdge.get(current));
            current = cameFromNode.get(current);
        }
        Collections.reverse(totalPath);  //we built it goal to start, so reverse it
        return totalPath;
    }
}