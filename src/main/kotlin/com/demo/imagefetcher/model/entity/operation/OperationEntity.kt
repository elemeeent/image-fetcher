package com.demo.imagefetcher.model.entity.operation

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("operations")
data class OperationEntity(
    @Id val id: UUID? = null,
    var status: String = OperationStatus.PROCESSING.name,
    @Column("created_at") val createdAt: Instant = Instant.now(),

    @Column("card_ids")
    val cardIds: Array<UUID>,

    @Column("requested_names")
    val requestedNames: Array<String>,

    @Column("completed_at") var completedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OperationEntity

        if (id != other.id) return false
        if (status != other.status) return false
        if (createdAt != other.createdAt) return false
        if (!cardIds.contentEquals(other.cardIds)) return false
        if (!requestedNames.contentEquals(other.requestedNames)) return false
        if (completedAt != other.completedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + status.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + cardIds.contentHashCode()
        result = 31 * result + requestedNames.contentHashCode()
        result = 31 * result + (completedAt?.hashCode() ?: 0)
        return result
    }
}