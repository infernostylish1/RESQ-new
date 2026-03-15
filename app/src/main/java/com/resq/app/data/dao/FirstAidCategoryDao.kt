package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resq.app.data.entity.FirstAidCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FirstAidCategoryDao {

    @Query("SELECT * FROM first_aid_categories ORDER BY display_order ASC")
    fun getAllCategories(): Flow<List<FirstAidCategoryEntity>>

    @Query("SELECT * FROM first_aid_categories ORDER BY display_order ASC")
    suspend fun getAllCategoriesOnce(): List<FirstAidCategoryEntity>

    @Query("SELECT * FROM first_aid_categories WHERE id = :categoryId")
    fun getCategoryById(categoryId: Long): Flow<FirstAidCategoryEntity?>

    @Query("SELECT * FROM first_aid_categories WHERE id = :categoryId")
    suspend fun getCategoryByIdOnce(categoryId: Long): FirstAidCategoryEntity?

    /**
     * One-shot lookup used by VoiceCommandViewModel.
     * Matches on English name, Tagalog name, or a LIKE partial match so
     * e.g. "CPR & Choking" is still found if the spoken key is "CPR & Choking".
     */
    @Query("""
        SELECT * FROM first_aid_categories
        WHERE name LIKE :name OR name_tagalog LIKE :name
        LIMIT 1
    """)
    suspend fun getCategoryByName(name: String): FirstAidCategoryEntity?

    @Query("""
        SELECT * FROM first_aid_categories 
        WHERE name LIKE '%' || :query || '%' 
           OR name_tagalog LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR description_tagalog LIKE '%' || :query || '%'
        ORDER BY display_order ASC
    """)
    fun searchCategories(query: String): Flow<List<FirstAidCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FirstAidCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCategories(categories: List<FirstAidCategoryEntity>)

    @Query("SELECT COUNT(*) FROM first_aid_categories")
    suspend fun getCategoryCount(): Int

    @Query("DELETE FROM first_aid_categories")
    suspend fun deleteAllCategories()
}