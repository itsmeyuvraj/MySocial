package com.mysocial.mesh.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a blocked or Sybil-evicted author whose gossip packets and log entries
 * are rejected at the radio transport layer before storing to disk.
 */
@Entity(
    tableName = "blocked_authors",
    indices = [Index(value = ["authorId"])]
)
data class BlockedAuthorEntity(
    @PrimaryKey
    val authorId: String,
    val blockedAt: Long = System.currentTimeMillis(),
    val reason: String = "User blocked",
    val isSybilEvicted: Boolean = false
)
