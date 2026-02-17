package com.resq.app.data.repository

import com.resq.app.data.dao.FirstAidCategoryDao
import com.resq.app.data.dao.FirstAidContentDao
import com.resq.app.data.entity.FirstAidCategoryEntity
import com.resq.app.data.entity.FirstAidContentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for first aid operations.
 * Handles first aid categories and content.
 */
@Singleton
class FirstAidRepository @Inject constructor(
    private val firstAidCategoryDao: FirstAidCategoryDao,
    private val firstAidContentDao: FirstAidContentDao
) {

    // Category operations
    fun getAllCategories(): Flow<List<FirstAidCategoryEntity>> =
        firstAidCategoryDao.getAllCategories()

    suspend fun getAllCategoriesOnce(): List<FirstAidCategoryEntity> =
        firstAidCategoryDao.getAllCategoriesOnce()

    fun getCategoryById(categoryId: Long): Flow<FirstAidCategoryEntity?> =
        firstAidCategoryDao.getCategoryById(categoryId)

    suspend fun getCategoryByIdOnce(categoryId: Long): FirstAidCategoryEntity? =
        firstAidCategoryDao.getCategoryByIdOnce(categoryId)

    fun searchCategories(query: String): Flow<List<FirstAidCategoryEntity>> =
        firstAidCategoryDao.searchCategories(query)

    suspend fun getCategoryCount(): Int = firstAidCategoryDao.getCategoryCount()

    // Content operations
    fun getContentByCategoryId(categoryId: Long): Flow<List<FirstAidContentEntity>> =
        firstAidContentDao.getContentByCategoryId(categoryId)

    suspend fun getContentByCategoryIdOnce(categoryId: Long): List<FirstAidContentEntity> =
        firstAidContentDao.getContentByCategoryIdOnce(categoryId)

    suspend fun getContentById(contentId: Long): FirstAidContentEntity? =
        firstAidContentDao.getContentById(contentId)

    suspend fun getSectionsByCategory(categoryId: Long): List<String> =
        firstAidContentDao.getSectionsByCategory(categoryId)

    fun getContentBySection(categoryId: Long, sectionTitle: String): Flow<List<FirstAidContentEntity>> =
        firstAidContentDao.getContentBySection(categoryId, sectionTitle)

    fun searchContent(query: String): Flow<List<FirstAidContentEntity>> =
        firstAidContentDao.searchContent(query)
}