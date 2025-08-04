package com.demo.imagefetcher.repository

import com.demo.imagefetcher.model.entity.operation.OperationEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

interface OperationRepository : ReactiveCrudRepository<OperationEntity, UUID> {

    @Query(
        """
        UPDATE operations 
        SET status = :status, completed_at = :completedAt 
        WHERE id = :id
    """
    )
    fun updateStatus(id: UUID, status: String, completedAt: Instant?): Mono<Void>

}