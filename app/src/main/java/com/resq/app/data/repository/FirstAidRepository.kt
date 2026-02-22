package com.resq.app.data.repository

import com.resq.app.data.dao.FirstAidCategoryDao
import com.resq.app.data.dao.FirstAidContentDao
import com.resq.app.data.entity.FirstAidCategoryEntity
import com.resq.app.data.entity.FirstAidContentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for first aid operations.
 * All ViewModel-facing method names are normalized here.
 */
class FirstAidRepository(
    private val firstAidCategoryDao: FirstAidCategoryDao,
    private val firstAidContentDao: FirstAidContentDao
) {

    // ─── Category operations ──────────────────────────────────────────────────

    fun getAllCategories(): Flow<List<FirstAidCategoryEntity>> =
        firstAidCategoryDao.getAllCategories()

    suspend fun getAllCategoriesOnce(): List<FirstAidCategoryEntity> =
        firstAidCategoryDao.getAllCategoriesOnce()

    /**
     * Primary method used by FirstAidViewModel.
     * Returns Flow of a single category by ID.
     */
    fun getCategoryById(categoryId: Long): Flow<FirstAidCategoryEntity?> =
        firstAidCategoryDao.getCategoryById(categoryId)

    suspend fun getCategoryByIdOnce(categoryId: Long): FirstAidCategoryEntity? =
        firstAidCategoryDao.getCategoryByIdOnce(categoryId)

    fun searchCategories(query: String): Flow<List<FirstAidCategoryEntity>> =
        firstAidCategoryDao.searchCategories(query)

    suspend fun getCategoryCount(): Int =
        firstAidCategoryDao.getCategoryCount()

    // ─── Content operations ───────────────────────────────────────────────────

    /**
     * Primary method used by FirstAidViewModel.
     * Alias: getCategoryContent(categoryId) maps to getContentByCategoryId.
     */
    fun getCategoryContent(categoryId: Long): Flow<List<FirstAidContentEntity>> =
        firstAidContentDao.getContentByCategoryId(categoryId)

    /** Legacy alias */
    fun getContentByCategoryId(categoryId: Long): Flow<List<FirstAidContentEntity>> =
        firstAidContentDao.getContentByCategoryId(categoryId)

    suspend fun getContentByCategoryIdOnce(categoryId: Long): List<FirstAidContentEntity> =
        firstAidContentDao.getContentByCategoryIdOnce(categoryId)

    suspend fun getContentById(contentId: Long): FirstAidContentEntity? =
        firstAidContentDao.getContentById(contentId)

    suspend fun getSectionsByCategory(categoryId: Long): List<String> =
        firstAidContentDao.getSectionsByCategory(categoryId)

    fun getContentBySection(
        categoryId: Long,
        sectionTitle: String
    ): Flow<List<FirstAidContentEntity>> =
        firstAidContentDao.getContentBySection(categoryId, sectionTitle)

    fun searchContent(query: String): Flow<List<FirstAidContentEntity>> =
        firstAidContentDao.searchContent(query)

    /**
     * Returns the number of content/step entries for a given category.
     * Used by FirstAidViewModel to build CategoryWithStepCount.
     */
    suspend fun getContentCountByCategoryId(categoryId: Long): Int =
        firstAidContentDao.getContentCountByCategoryId(categoryId)
}