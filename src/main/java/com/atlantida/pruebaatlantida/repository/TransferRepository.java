package com.atlantida.pruebaatlantida.repository;

import com.atlantida.pruebaatlantida.model.Transfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    @EntityGraph(attributePaths = {"source", "destination"})
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"source", "destination"})
    Optional<Transfer> findWithAccountsById(Long id);

    @Query("""
            SELECT t FROM Transfer t
            JOIN FETCH t.source
            JOIN FETCH t.destination
            WHERE t.source.id = :accountId OR t.destination.id = :accountId
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<Transfer> findHistoryByAccountId(@Param("accountId") Long accountId);
}
