package com.pingwei.lengkubao.ui.query.advancededuction

import com.pingwei.lengkubao.data.db.entity.Advance
import com.pingwei.lengkubao.data.db.entity.Deduction

enum class AdvanceDeductionRecordType {
    ADVANCE,
    DEDUCTION
}

data class AdvanceDeductionQueryRecord(
    val id: Long,
    val type: AdvanceDeductionRecordType,
    val customerNo: String,
    val customerName: String,
    val amount: Double,
    val recordDate: String,
    val createTime: Long,
    val reason: String?,
    val handler: String?,
    val syncStatus: Int,
    val quantity: Int = 0,
    val unitPrice: Double = 0.0,
) {
    val typeLabel: String
        get() = when (type) {
            AdvanceDeductionRecordType.ADVANCE -> "预支"
            AdvanceDeductionRecordType.DEDUCTION -> "扣款"
        }

    val billType: String
        get() = when (type) {
            AdvanceDeductionRecordType.ADVANCE -> "ADVANCE"
            AdvanceDeductionRecordType.DEDUCTION -> "DEDUCTION"
        }

    companion object {
        fun fromAdvance(advance: Advance) = AdvanceDeductionQueryRecord(
            id = advance.id,
            type = AdvanceDeductionRecordType.ADVANCE,
            customerNo = advance.customerNo,
            customerName = advance.customerName,
            amount = advance.amount,
            recordDate = advance.advanceDate,
            createTime = advance.createTime,
            reason = advance.reason,
            handler = advance.handler,
            syncStatus = advance.syncStatus,
        )

        fun fromDeduction(deduction: Deduction) = AdvanceDeductionQueryRecord(
            id = deduction.id,
            type = AdvanceDeductionRecordType.DEDUCTION,
            customerNo = deduction.customerNo,
            customerName = deduction.customerName,
            amount = deduction.amount,
            recordDate = deduction.deductDate,
            createTime = deduction.createTime,
            reason = deduction.reason,
            handler = deduction.handler,
            syncStatus = deduction.syncStatus,
            quantity = deduction.quantity,
            unitPrice = deduction.unitPrice,
        )
    }
}
