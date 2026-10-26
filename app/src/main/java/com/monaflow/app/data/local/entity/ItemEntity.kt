package com.monaflow.app.data.local.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 物品。purchasePriceMinor 为最小货币单位（分），currency 为 ISO 4217 代码；
 * 日期均为当地日期的 epochDay。未填写的字段保持 null，不得自动生成。
 */
@Serializable
@Entity(
    tableName = "items",
    indices = [Index("category"), Index("isArchived"), Index("warrantyEndDate")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    val quantity: Int = 1,
    val purchasePriceMinor: Long? = null,
    val currency: String? = null,
    val purchaseDate: Long? = null, // epochDay
    val storageLocation: String = "",
    val warrantyEndDate: Long? = null, // epochDay
    val imageUri: String? = null,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean = false,
)
