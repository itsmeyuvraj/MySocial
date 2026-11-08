package com.mysocial.mesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mysocial.mesh.model.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(story: StoryEntity)

    @Query("SELECT * FROM stories_view WHERE expiresAt > :now ORDER BY createdAt DESC")
    fun observeActiveStories(now: Long = System.currentTimeMillis()): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories_view WHERE authorId = :authorId AND expiresAt > :now ORDER BY createdAt ASC")
    fun observeStoriesForAuthor(authorId: String, now: Long = System.currentTimeMillis()): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories_view WHERE authorId = :authorId AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getStoriesForAuthor(authorId: String, now: Long = System.currentTimeMillis()): List<StoryEntity>

    @Query("UPDATE stories_view SET isViewed = 1 WHERE storyId = :storyId")
    suspend fun markViewed(storyId: String)

    @Query("DELETE FROM stories_view WHERE expiresAt <= :now")
    suspend fun deleteExpiredStories(now: Long = System.currentTimeMillis()): Int
}

