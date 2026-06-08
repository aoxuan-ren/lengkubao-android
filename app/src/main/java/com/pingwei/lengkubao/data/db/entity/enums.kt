// data/db/entity/enums.kt
package com.pingwei.lengkubao.data.db.entity

enum class ChangeType {
    INBOUND,     // 入库
    OUTBOUND,    // 出库
    RESERVE,     // 预留
    RELEASE,     // 释放
    VOID,        // 作废
    ADJUST       // 调整
}

enum class BillType {
    INBOUND,    // 入库单
    SALE,       // 销售单
    PACKAGING,  // 包装单
    PRESALE     // 预售单
}