package com.crime.detection.backend_service.repository;

import com.crime.detection.backend_service.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findBySenderAccountAndTimestampAfter(String senderAccount, LocalDateTime timestamp);
    List<Transaction> findByReceiverAccount(String receiverAccount);
    List<Transaction> findBySenderAccount(String senderAccount);
    List<Transaction> findByTimestampAfter(LocalDateTime timestamp);
}
