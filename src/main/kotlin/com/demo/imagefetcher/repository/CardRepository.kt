package com.demo.imagefetcher.repository

import com.demo.imagefetcher.model.entity.card.CardEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

interface CardRepository : ReactiveCrudRepository<CardEntity, UUID> {

    fun findByCanonicalName(canonicalName: String): Mono<CardEntity>

    @Query(
        """
        SELECT id, canonical_name, requested_name, png_url, status, fetched_at, fail_reason, updated_at
        FROM cards
        WHERE id IN (:ids)
    """
    )
    fun findByIds(ids: List<UUID>): Flux<CardEntity>

    @Query(
        """
        INSERT INTO cards (canonical_name, requested_name, png_url, status, fetched_at, fail_reason, updated_at)
        VALUES (:canonicalName, :requestedName, :pngUrl, :status, :fetchedAt, :failReason, now())
        ON CONFLICT (canonical_name) DO UPDATE SET
          requested_name = EXCLUDED.requested_name,
          png_url        = EXCLUDED.png_url,
          status         = EXCLUDED.status,
          fetched_at     = EXCLUDED.fetched_at,
          fail_reason    = EXCLUDED.fail_reason,
          updated_at     = now()
        RETURNING id, canonical_name, requested_name, png_url, status, fetched_at, fail_reason, updated_at
    """
    )
    fun upsert(
        canonicalName: String,
        requestedName: String,
        pngUrl: String?,
        status: String,
        fetchedAt: Instant?,
        failReason: String?
    ): Mono<CardEntity>

    @Query(
        """
        UPDATE cards
           SET status='PROCESSING', updated_at=now()
         WHERE canonical_name=:canonicalName AND status IN ('NEW','FAILURE','STALE')
        RETURNING canonical_name
    """
    )
    fun tryMarkProcessing(canonicalName: String): Mono<String>

    @Query("""
        SELECT * FROM cards
        WHERE status IN (:s1, :s2)
        ORDER BY updated_at NULLS LAST
        LIMIT :limit
    """)
    fun findByStatuses(s1: String, s2: String, limit: Int): Flux<CardEntity>

    @Query("""
        SELECT * FROM cards
        WHERE status = :success AND fetched_at IS NOT NULL AND fetched_at < :threshold
        ORDER BY fetched_at
        LIMIT :limit
    """)
    fun findFetchedBefore(success: String, threshold: Instant, limit: Int): Flux<CardEntity>

}