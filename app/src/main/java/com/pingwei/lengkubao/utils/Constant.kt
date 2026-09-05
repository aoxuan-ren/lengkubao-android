package com.pingwei.lengkubao.utils

/**
 * 全局常量定义
 */
object Constant {
    // ========== UDP广播发现相关 ==========
    const val UDP_BROADCAST_PORT = 8888              // UDP广播端口
    const val UDP_BROADCAST_TIMEOUT = 8000L          // UDP发现超时时间
    const val ACTION_UDP_DISCOVERY = "com.pingwei.lengkubao.UDP_DISCOVERY"
    const val EXTRA_UDP_SERVER_NAME = "udp_server_name"
    const val EXTRA_UDP_SERVER_IP = "udp_server_ip"
    const val EXTRA_UDP_SERVER_PORT = "udp_server_port"
    const val EXTRA_UDP_PAIRING_CODE = "udp_pairing_code"

    // UDP消息协议
    const val UDP_MSG_DISCOVER = "DISCOVER_LENGKUBAO"        // 发现请求
    const val UDP_MSG_RESPONSE = "LENGKUBAO_SERVER"          // 服务器响应
    const val UDP_MSG_PAIRING_REQUEST = "PAIRING_REQUEST"    // 配对请求
    const val UDP_MSG_PAIRING_ACCEPTED = "PAIRING_ACCEPTED"  // 配对接受
    const val UDP_MSG_PAIRING_REJECTED = "PAIRING_REJECTED"  // 配对拒绝
    const val UDP_MSG_SERVER_ANNOUNCE = "LENGKUBAO_SERVER_ANNOUNCE" // 服务器主动广播

    // ========== mDNS相关常量 ==========
    const val MDNS_SERVICE_TYPE = "_lengkubao._tcp.local."  // mDNS服务类型
    const val MDNS_PAIRING_CODE_KEY = "pairingCode"         // 配对码在TXT记录中的key
    const val MDNS_DEVICE_NAME_KEY = "deviceName"           // 设备名称key
    const val MDNS_SERVICE_NAME = "LengKuBaoServer"         // 服务名称前缀

    // ========== 新增：mDNS广播Action ==========
    const val ACTION_MDNS_DEVICE_FOUND = "com.pingwei.lengkubao.MDNS_DEVICE_FOUND"
    const val ACTION_MDNS_DEVICE_LOST = "com.pingwei.lengkubao.MDNS_DEVICE_LOST"
    const val ACTION_MDNS_PAIRING_SUCCESS = "com.pingwei.lengkubao.MDNS_PAIRING_SUCCESS"
    const val ACTION_MDNS_PAIRING_FAILED = "com.pingwei.lengkubao.MDNS_PAIRING_FAILED"

    // SharedPreferences 键名
    const val PREF_PAIRED_SERVER_IP = "paired_server_ip"    // 已配对服务器IP
    const val PREF_PAIRED_SERVER_PORT = "paired_server_port" // 已配对服务器端口
    const val PREF_SERVER_EVER_CONNECTED = "server_ever_connected" // 是否曾成功连上过服务器
    const val PREF_PAIRING_CODE = "pairing_code"            // 保存的配对码
    const val PREF_AUTO_CONNECT = "auto_connect_mdns"       // 是否自动连接
    const val PREF_AUTO_SYNC = "auto_sync"                  // 是否自动后台同步
    const val PREF_AUTO_SYNC_DEFAULT = true
    const val PREF_DISCOVERY_METHOD = "discovery_method"    // 发现方式: udp/mdns/both
    const val PREF_SHOW_ADVANCED_SYNC = "show_advanced_sync" // 是否展开高级同步设置

    // 广播Action（UDP）
    const val ACTION_UDP_DEVICE_FOUND = "com.pingwei.lengkubao.UDP_DEVICE_FOUND"
    const val ACTION_UDP_PAIRING_SUCCESS = "com.pingwei.lengkubao.UDP_PAIRING_SUCCESS"
    const val ACTION_UDP_PAIRING_FAILED = "com.pingwei.lengkubao.UDP_PAIRING_FAILED"

    // 广播Extra（通用）
    const val EXTRA_DEVICE_IP = "device_ip"
    const val EXTRA_DEVICE_PORT = "device_port"
    const val EXTRA_DEVICE_NAME = "device_name"
    const val EXTRA_PAIRING_CODE = "pairing_code"
    const val EXTRA_MESSAGE = "message"

    // 扫描超时时间（毫秒）
    const val MDNS_SCAN_TIMEOUT = 10000L

    // ========== TCP同步确认机制相关常量（新增） ==========
// 预支扣款类型
    const val TCP_CMD_ADVANCE = "ADVANCE"                    // 预支款
    const val TCP_CMD_DEDUCTION = "DEDUCTION"                // 扣款

    // 在单据类型中添加
    const val BILL_TYPE_ADVANCE = "ADVANCE"                  // 预支款
    const val BILL_TYPE_DEDUCTION = "DEDUCTION"              // 扣款
    // TCP消息协议命令字
    const val TCP_CMD_REGISTER = "REGISTER"                  // 注册请求
    const val TCP_CMD_REGISTER_OK = "REGISTER_OK"            // 注册成功
    const val TCP_CMD_REGISTER_FAIL = "REGISTER_FAIL"        // 注册失败
    const val TCP_CMD_PING = "PING"                          // 心跳Ping
    const val TCP_CMD_PONG = "PONG"                          // 心跳Pong
    const val TCP_CMD_HEARTBEAT = "HEARTBEAT"                // 心跳（简单版）
    const val TCP_CMD_HEARTBEAT_OK = "HEARTBEAT_OK"          // 心跳响应

    // 数据同步命令字（与电脑端保持一致）
    const val TCP_CMD_INBOUND = "INBOUND"                    // 入库单
    const val TCP_CMD_SALES = "SALES"                        // 销售单
    const val TCP_CMD_PACKAGING = "PACKAGING"                // 包装单
    const val TCP_CMD_CUSTOMER = "CUSTOMER"                  // 客户
    const val TCP_CMD_PRODUCT = "PRODUCT"                    // 商品
    const val TCP_CMD_LOCATION = "LOCATION"                  // 库位
    const val TCP_CMD_OPERATOR = "OPERATOR"                  // 经手人

    // 同步确认命令字（新增）
    const val TCP_CMD_SYNC_SUCCESS = "SYNC_SUCCESS"          // 同步成功确认
    const val TCP_CMD_SYNC_FAILED = "SYNC_FAILED"            // 同步失败确认
    const val TCP_CMD_SYNC_ERROR = "SYNC_ERROR"              // 同步错误
    const val TCP_CMD_SYNC_OK = "SYNC_OK"                    // 同步OK（旧版兼容）
    const val TCP_CMD_SYNC_RESULT = "SYNC_RESULT"            // 同步结果
    const val TCP_CMD_PUSH_DATA = "PUSH_DATA"                // 推送数据
    const val TCP_CMD_PUSH_COMPLETE = "PUSH_COMPLETE"        // 推送完成
    const val TCP_CMD_REQUEST_PENDING = "REQUEST_PENDING"    // 请求待同步数据
    const val TCP_CMD_AUTO_SYNC = "AUTO_SYNC"                // 自动同步
    const val TCP_CMD_QUERY = "QUERY"                         // 查询命令

    // 同步超时设置（毫秒）
    const val SYNC_WAIT_TIMEOUT = 30000L                      // 等待同步确认超时时间（30秒）
    const val SYNC_ITEM_DELAY = 50L                           // 明细发送间隔（毫秒）
    const val SYNC_BATCH_DELAY = 500L                         // 单据间发送间隔（毫秒）

    // 同步状态值
    const val SYNC_STATUS_PENDING = 0                         // 待同步
    const val SYNC_STATUS_SYNCING = 1                         // 同步中
    const val SYNC_STATUS_SUCCESS = 2                         // 同步成功
    const val SYNC_STATUS_FAILED = 3                          // 同步失败

    // 广播Action（同步相关）
    const val ACTION_SYNC_COMPLETE = "com.pingwei.lengkubao.SYNC_COMPLETE"
    const val ACTION_SYNC_PROGRESS = "com.pingwei.lengkubao.SYNC_PROGRESS"
    const val ACTION_SYNC_ERROR = "com.pingwei.lengkubao.SYNC_ERROR"

    // 广播Extra（同步相关）
    const val EXTRA_BILL_ID = "bill_id"
    const val EXTRA_BILL_TYPE = "bill_type"
    const val EXTRA_BILL_NO = "bill_no"
    const val EXTRA_SYNC_RESULT = "sync_result"
    const val EXTRA_SYNC_SUCCESS = "sync_success"
    const val EXTRA_SYNC_ERROR_MSG = "sync_error_msg"
    const val EXTRA_SYNC_PROGRESS = "sync_progress"
    const val EXTRA_SYNC_TOTAL = "sync_total"
    const val EXTRA_SYNC_CURRENT = "sync_current"

    // 单据类型
    const val BILL_TYPE_IN_STOCK = "IN_STOCK"                 // 入库单
    const val BILL_TYPE_SALE = "SALE"                          // 销售单
    const val BILL_TYPE_PACKAGING = "PACKAGING"                // 包装单

    // 包装单标记
    const val PACK_FLAG_TAKE = "TAKE"                          // 出包装
    const val PACK_FLAG_RETURN = "RETURN"                      // 进包装

    // 默认配对码
    const val DEFAULT_PAIRING_CODE = "123456"
}