package com.wifimanager.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "router_configs")
data class RouterConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String = "الراوتر الرئيسي",
    val ipAddress: String = "192.168.1.1",
    val username: String = "admin",
    val password: String = "",
    val routerType: RouterType = RouterType.GENERIC,
    val isActive: Boolean = true,
    val remoteAccessEnabled: Boolean = false,
    val remoteHost: String = "",
    val remotePort: Int = 8080,
    val lastConnected: Long = 0L
)

enum class RouterType {
    GENERIC,
    TP_LINK,
    D_LINK,
    HUAWEI,
    ASUS,
    NETGEAR,
    MIKROTIK,
    OPENWRT
}
