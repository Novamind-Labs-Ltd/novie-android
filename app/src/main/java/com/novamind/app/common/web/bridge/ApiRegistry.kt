package com.novamind.app.common.web.bridge

/**
 * JSAPI 注册表。Handler 在创建 Dispatcher 时注入；按 "命名空间.方法" 路由。
 * 实例级（非全局单例），便于不同容器/测试隔离注册不同 API 集合。
 */
class ApiRegistry {
    private val handlers = LinkedHashMap<String, BridgeHandler>()

    fun register(handler: BridgeHandler): ApiRegistry {
        handlers[handler.name] = handler
        return this
    }

    fun registerAll(list: List<BridgeHandler>): ApiRegistry {
        list.forEach { register(it) }
        return this
    }

    fun find(name: String): BridgeHandler? = handlers[name]

    /** 已注册 API 名清单，用于下发给 JS 做 hasApi 能力查询。 */
    fun names(): List<String> = handlers.keys.toList()
}
