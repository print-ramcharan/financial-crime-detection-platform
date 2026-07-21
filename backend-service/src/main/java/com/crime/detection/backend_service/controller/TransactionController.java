package com.crime.detection.backend_service.controller;

import com.crime.detection.backend_service.model.Transaction;
import com.crime.detection.backend_service.repository.TransactionRepository;
import com.crime.detection.backend_service.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private KafkaProducerService kafkaProducerService;
    @Autowired
    private com.crime.detection.backend_service.service.AMLAnalyzer amlAnalyzer;

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction tx) {
        if (tx.getId() == null) {
            tx.setId(UUID.randomUUID().toString());
        }
        tx.setTimestamp(LocalDateTime.now());
        tx.setStatus("PENDING_ML_SCORE");
        
        Transaction saved = transactionRepository.save(tx);
        // Analyze transaction history for AML typologies before sending to ML pipeline
        try {
            amlAnalyzer.analyzeTransaction(saved);
        } catch (Exception e) {
            System.err.println("AML analysis error: " + e.getMessage());
        }
        kafkaProducerService.sendTransaction(saved);
        
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionRepository.findAll());
    }
}
