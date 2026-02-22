package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resq.app.data.entity.FirstAidContentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for FirstAidContent operations.
 * Handles detailed first aid instructions.
 */
@Dao
interface FirstAidContentDao {

    @Query("SELECT * FROM first_aid_content WHERE category_id = :categoryId ORDER BY display_order ASC")
    fun getContentByCategoryId(categoryId: Long): Flow<List<FirstAidContentEntity>>

    @Query("SELECT * FROM first_aid_content WHERE category_id = :categoryId ORDER BY display_order ASC")
    suspend fun getContentByCategoryIdOnce(categoryId: Long): List<FirstAidContentEntity>

    @Query("SELECT * FROM first_aid_content WHERE id = :contentId")
    suspend fun getContentById(contentId: Long): FirstAidContentEntity?

    @Query("SELECT DISTINCT section_title FROM first_aid_content WHERE category_id = :categoryId ORDER BY display_order ASC")
    suspend fun getSectionsByCategory(categoryId: Long): List<String>

    @Query("""
        SELECT * FROM first_aid_content 
        WHERE category_id = :categoryId AND section_title = :sectionTitle
        ORDER BY display_order ASC
    """)
    fun getContentBySection(categoryId: Long, sectionTitle: String): Flow<List<FirstAidContentEntity>>

    @Query("""
        SELECT * FROM first_aid_content 
        WHERE content LIKE '%' || :query || '%' 
           OR content_tagalog LIKE '%' || :query || '%'
           OR title LIKE '%' || :query || '%'
           OR title_tagalog LIKE '%' || :query || '%'
        ORDER BY display_order ASC
    """)
    fun searchContent(query: String): Flow<List<FirstAidContentEntity>>

    @Query("SELECT COUNT(*) FROM first_aid_content WHERE category_id = :categoryId")
    suspend fun getContentCountByCategoryId(categoryId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: FirstAidContentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllContent(contentList: List<FirstAidContentEntity>)

    @Query("DELETE FROM first_aid_content WHERE category_id = :categoryId")
    suspend fun deleteContentByCategoryId(categoryId: Long)

    @Query("DELETE FROM first_aid_content")
    suspend fun deleteAllContent()
}