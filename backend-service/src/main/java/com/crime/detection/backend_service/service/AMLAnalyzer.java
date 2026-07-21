package com.crime.detection.backend_service.service;

import com.crime.detection.backend_service.model.Alert;
import com.crime.detection.backend_service.model.Transaction;
import com.crime.detection.backend_service.repository.AlertRepository;
import com.crime.detection.backend_service.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AMLAnalyzer {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AlertRepository alertRepository;

    // Entry point to run all AML checks for a new transaction
    public void analyzeTransaction(Transaction tx) {
        if (tx == null || tx.getId() == null) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff24h = now.minusHours(24);

        detectStructuring(tx, cutoff24h);
        detectLayering(tx, cutoff24h);
        detectCircularTransfers(tx, cutoff24h);
        detectMuleReceiver(tx, cutoff24h);
    }

    private void detectStructuring(Transaction tx, LocalDateTime cutoff) {
        try {
            List<Transaction> recent = transactionRepository.findBySenderAccountAndTimestampAfter(tx.getSenderAccount(), cutoff);
            // Consider only small transfers (e.g., < 10000) within window
            List<Transaction> smalls = recent.stream()
                    .filter(t -> t.getAmount() != null && t.getAmount().doubleValue() < 10000.0)
                    .collect(Collectors.toList());

            double total = smalls.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
            if (smalls.size() >= 5 && total > 50000.0) {
                String reason = String.format("Structuring detected: %d transfers under $10k totalling $%.2f in last 24h", smalls.size(), total);
                createAlert(tx.getId(), "HIGH", "Structuring", reason);
            }
        } catch (Exception e) {
            System.err.println("Error in structuring detection: " + e.getMessage());
        }
    }

    private void detectLayering(Transaction tx, LocalDateTime cutoff) {
        try {
            // Build adjacency map for recent transactions within window
            List<Transaction> recent = transactionRepository.findByTimestampAfter(cutoff);
            Map<String, Set<String>> adj = new HashMap<>();
            for (Transaction t : recent) {
                adj.computeIfAbsent(t.getSenderAccount(), k -> new HashSet<>()).add(t.getReceiverAccount());
            }

            // DFS up to depth 4 starting from tx.senderAccount
            String start = tx.getSenderAccount();
            boolean foundLongPath = dfsPathLength(adj, start, new HashSet<>(), 0, 3);
            if (foundLongPath) {
                String reason = "Layering chain detected (>=3 hops) within 24 hours starting from sender";
                createAlert(tx.getId(), "HIGH", "Layering", reason);
            }
        } catch (Exception e) {
            System.err.println("Error in layering detection: " + e.getMessage());
        }
    }

    // returns true if exists path of length >= minHops
    private boolean dfsPathLength(Map<String, Set<String>> adj, String current, Set<String> visited, int depth, int minHops) {
        if (depth >= minHops) return true;
        if (visited.contains(current)) return false;
        visited.add(current);
        Set<String> neighbors = adj.getOrDefault(current, Collections.emptySet());
        for (String nb : neighbors) {
            boolean found = dfsPathLength(adj, nb, new HashSet<>(visited), depth + 1, minHops);
            if (found) return true;
        }
        return false;
    }

    private void detectCircularTransfers(Transaction tx, LocalDateTime cutoff) {
        try {
            List<Transaction> recent = transactionRepository.findByTimestampAfter(cutoff);
            Map<String, Set<String>> adj = new HashMap<>();
            for (Transaction t : recent) {
                adj.computeIfAbsent(t.getSenderAccount(), k -> new HashSet<>()).add(t.getReceiverAccount());
            }

            String start = tx.getSenderAccount();
            boolean hasCycle = hasCycleContainingNode(adj, start);
            if (hasCycle) {
                String reason = "Circular transfer detected involving sender within 24 hours";
                createAlert(tx.getId(), "HIGH", "Circular Transfer", reason);
            }
        } catch (Exception e) {
            System.err.println("Error in circular transfer detection: " + e.getMessage());
        }
    }

    private boolean hasCycleContainingNode(Map<String, Set<String>> adj, String start) {
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();

        stack.push(start);
        onStack.add(start);

        return dfsCycle(adj, start, start, new HashSet<>());
    }

    private boolean dfsCycle(Map<String, Set<String>> adj, String current, String target, Set<String> visited) {
        if (visited.contains(current)) return false;
        visited.add(current);
        Set<String> neighbors = adj.getOrDefault(current, Collections.emptySet());
        for (String nb : neighbors) {
            if (nb.equals(target)) return true;
            if (dfsCycle(adj, nb, target, new HashSet<>(visited))) return true;
        }
        return false;
    }

    private void detectMuleReceiver(Transaction tx, LocalDateTime cutoff) {
        try {
            List<Transaction> recents = transactionRepository.findByReceiverAccount(tx.getReceiverAccount());
            List<Transaction> filtered = recents.stream()
                    .filter(t -> t.getTimestamp() != null && t.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
            long distinctSenders = filtered.stream().map(Transaction::getSenderAccount).distinct().count();
            if (distinctSenders >= 4 && filtered.size() >= 6) {
                String reason = String.format("Potential mule receiver: %d distinct senders in last 24h", distinctSenders);
                createAlert(tx.getId(), "HIGH", "Mule Network", reason);
            }
        } catch (Exception e) {
            System.err.println("Error in mule detection: " + e.getMessage());
        }
    }

    private void createAlert(String transactionId, String severity, String reasonShort, String reason) {
        try {
            Alert a = new Alert();
            a.setId(UUID.randomUUID().toString());
            a.setTransactionId(transactionId);
            a.setSeverity(severity);
            a.setReason(reasonShort + ": " + reason);
            a.setStatus("OPEN");
            a.setCreatedAt(LocalDateTime.now());
            alertRepository.save(a);
            // Mark the related transaction as on-hold so frontend displays a non-approved status immediately
            try {
                Optional<Transaction> txOpt = transactionRepository.findById(transactionId);
                if (txOpt.isPresent()) {
                    Transaction t = txOpt.get();
                    t.setStatus("HOLD");
                    transactionRepository.save(t);
                }
            } catch (Exception e) {
                System.err.println("Failed to update transaction status for alert: " + e.getMessage());
            }
            System.out.println("Created AML alert: " + a.getId() + " - " + a.getReason());
        } catch (Exception e) {
            System.err.println("Failed to create alert: " + e.getMessage());
        }
    }
}
