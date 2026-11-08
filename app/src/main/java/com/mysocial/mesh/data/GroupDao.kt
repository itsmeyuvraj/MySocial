package com.mysocial.mesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mysocial.mesh.model.GroupEntity
import com.mysocial.mesh.model.GroupMemberEntity
import com.mysocial.mesh.model.GroupMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Upsert
    suspend fun upsertGroup(group: GroupEntity)

    @Query("SELECT * FROM groups_view WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups_view ORDER BY createdAt DESC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups_view ORDER BY createdAt DESC")
    suspend fun getAllGroups(): List<GroupEntity>

    @Query("DELETE FROM groups_view WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Upsert
    suspend fun upsertMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getGroupMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: GroupMessageEntity): Long

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun observeMessagesForGroup(groupId: String): Flow<List<GroupMessageEntity>>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    suspend fun getMessagesForGroup(groupId: String): List<GroupMessageEntity>

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    suspend fun deleteMessagesForGroup(groupId: String)

    @Query("UPDATE group_messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: Int)
}

