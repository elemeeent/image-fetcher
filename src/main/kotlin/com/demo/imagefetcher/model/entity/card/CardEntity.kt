package com.demo.imagefetcher.model.entity.card

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("cards")
data class CardEntity(
    @Id
    val id: UUID? = null,

    @Column("canonical_name")
    val canonicalName: String,

    @Column("requested_name")
    var requestedName: String,

    @Column("png_url")
    var pngUrl: String? = null,

    var status: String,

    @Column("fetched_at")
    var fetchedAt: Instant? = null,

    @Column("fail_reason")
    var failReason: String? = null,

    @Column("updated_at")
    var updatedAt: Instant = Instant.now()
)
