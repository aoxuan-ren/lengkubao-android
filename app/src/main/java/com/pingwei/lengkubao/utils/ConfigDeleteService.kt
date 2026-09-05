package com.pingwei.lengkubao.utils



import android.content.Context

import com.pingwei.lengkubao.data.db.AppDatabase

import com.pingwei.lengkubao.data.db.entity.Location

import com.pingwei.lengkubao.data.db.entity.Operator

import com.pingwei.lengkubao.data.db.entity.PackagingType



const val PC_ONLY_CONFIG_DELETE_MESSAGE = "请在电脑端系统设置中删除或禁用"



sealed class ConfigDeleteResult {

    data object PcOnly : ConfigDeleteResult()

    data object PhysicallyDeleted : ConfigDeleteResult()

    data class DisabledDueToReferences(val refCount: Int) : ConfigDeleteResult()

    data class Failed(val message: String) : ConfigDeleteResult()

}



object ConfigDeleteService {



    suspend fun countOperatorBillRefs(context: Context, operatorId: Long): Int {

        return AppDatabase.getInstance(context).operatorDao().countOperatorBillRefs(operatorId)

    }



    suspend fun countLocationBillRefs(context: Context, locationId: Long): Int {

        return AppDatabase.getInstance(context).locationDao().countLocationBillRefs(locationId)

    }



    suspend fun countPackTypeBillRefs(context: Context, typeId: Long): Int {

        return AppDatabase.getInstance(context).packagingTypeDao().countPackTypeBillRefs(typeId)

    }



    suspend fun deleteOperator(context: Context, operator: Operator): ConfigDeleteResult {

        return ConfigDeleteResult.PcOnly

    }



    suspend fun deleteLocation(context: Context, location: Location): ConfigDeleteResult {

        return ConfigDeleteResult.PcOnly

    }



    suspend fun deletePackType(context: Context, packagingType: PackagingType): ConfigDeleteResult {

        return ConfigDeleteResult.PcOnly

    }

}


