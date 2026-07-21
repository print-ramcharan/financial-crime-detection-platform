package com.crime.detection.backend_service.service;

import com.crime.detection.backend_service.model.Transaction;
import com.crime.detection.backend_service.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GraphAnalysisService {

    @Autowired
    private TransactionRepository transactionRepository;

    public static class GraphData {
        public List<Map<String, Object>> nodes = new ArrayList<>();
        public List<Map<String, Object>> links = new ArrayList<>();
        public List<String> alerts = new ArrayList<>();
    }

    public GraphData analyzeNetworks() {
        List<Transaction> transactions = transactionRepository.findAll();
        GraphData graph = new GraphData();
        
        Set<String> accounts = new HashSet<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (Transaction tx : transactions) {
            accounts.add(tx.getSenderAccount());
            accounts.add(tx.getReceiverAccount());

            adjList.computeIfAbsent(tx.getSenderAccount(), k -> new ArrayList<>()).add(tx.getReceiverAccount());

            Map<String, Object> link = new HashMap<>();
            link.put("source", tx.getSenderAccount());
            link.put("target", tx.getReceiverAccount());
            link.put("amount", tx.getAmount());
            link.put("id", tx.getId());
            graph.links.add(link);
        }

        for (String acc : accounts) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", acc);
            node.put("label", acc);
            graph.nodes.add(node);
        }

        // Detect Cycles for dashboard
        detectCycles(adjList, graph.alerts, 5);

        return graph;
    }

    public Map<String, Object> extractGraphFeatures(Transaction currentTx) {
        Map<String, Object> features = new HashMap<>();
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        
        List<Transaction> recentTx = transactionRepository.findByTimestampAfter(last24h);
        
        // Build graph from last 24h
        Map<String, List<String>> adjList = new HashMap<>();
        for (Transaction tx : recentTx) {
            adjList.computeIfAbsent(tx.getSenderAccount(), k -> new ArrayList<>()).add(tx.getReceiverAccount());
        }
        
        // Add current transaction (even if not saved yet)
        adjList.computeIfAbsent(currentTx.getSenderAccount(), k -> new ArrayList<>()).add(currentTx.getReceiverAccount());

        // 1. fan_out_count
        long fanOut = adjList.getOrDefault(currentTx.getSenderAccount(), new ArrayList<>()).stream().distinct().count();
        features.put("fan_out_count", fanOut);
        
        // 2. fan_in_count
        long fanIn = 0;
        for (List<String> receivers : adjList.values()) {
            if (receivers.contains(currentTx.getReceiverAccount())) {
                fanIn++;
            }
        }
        features.put("fan_in_count", fanIn);
        
        // 3. cycle_detected (<= 5 hops)
        boolean hasCycle = checkCycleFromNode(currentTx.getSenderAccount(), adjList, 5);
        features.put("cycle_detected", hasCycle);
        
        // 4. hop_count (layering chain length)
        int hopCount = getMaxPathLength(currentTx.getSenderAccount(), adjList, 5);
        features.put("hop_count", hopCount);
        
        return features;
    }

    private boolean checkCycleFromNode(String startNode, Map<String, List<String>> adjList, int maxDepth) {
        return dfsFindCycle(startNode, startNode, adjList, new HashSet<>(), 0, maxDepth);
    }
    
    private boolean dfsFindCycle(String current, String target, Map<String, List<String>> adjList, Set<String> visited, int depth, int maxDepth) {
        if (depth > 0 && current.equals(target)) {
            return true;
        }
        if (depth >= maxDepth) {
            return false;
        }
        
        visited.add(current);
        List<String> neighbors = adjList.getOrDefault(current, Collections.emptyList());
        
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor) || neighbor.equals(target)) {
                if (dfsFindCycle(neighbor, target, adjList, new HashSet<>(visited), depth + 1, maxDepth)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getMaxPathLength(String node, Map<String, List<String>> adjList, int maxDepth) {
        return dfsMaxPath(node, adjList, new HashSet<>(), 0, maxDepth);
    }

    private int dfsMaxPath(String current, Map<String, List<String>> adjList, Set<String> visited, int depth, int maxDepth) {
        if (depth >= maxDepth) return depth;
        
        visited.add(current);
        List<String> neighbors = adjList.getOrDefault(current, Collections.emptyList());
        
        int max = depth;
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                int childDepth = dfsMaxPath(neighbor, adjList, new HashSet<>(visited), depth + 1, maxDepth);
                max = Math.max(max, childDepth);
            }
        }
        return max;
    }

    private void detectCycles(Map<String, List<String>> adjList, List<String> alerts, int maxDepth) {
        for (String node : adjList.keySet()) {
            if (checkCycleFromNode(node, adjList, maxDepth)) {
                alerts.add("Circular money flow (<= " + maxDepth + " hops) detected starting at: " + node);
            }
        }
    }
}
