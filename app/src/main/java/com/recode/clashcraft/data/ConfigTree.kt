package com.recode.clashcraft.data

object ConfigTree {
    fun set(root: Map<String, Any?>, path: ConfigPath, value: Any?): LinkedHashMap<String, Any?> {
        require(path.parts.isNotEmpty()) { "不能替换配置根节点" }
        return transform(root, path.parts) { value }.asRoot()
    }

    fun remove(root: Map<String, Any?>, path: ConfigPath): LinkedHashMap<String, Any?> {
        require(path.parts.isNotEmpty()) { "不能删除配置根节点" }
        val parent = ConfigPath(path.parts.dropLast(1))
        val last = path.parts.last()
        return transform(root, parent.parts) { container ->
            when (last) {
                is ConfigPathPart.Key -> container.asMutableMap().apply { remove(last.value) }
                is ConfigPathPart.Index -> container.asMutableList().apply {
                    require(last.value in indices) { "列表位置已失效" }
                    removeAt(last.value)
                }
            }
        }.asRoot()
    }

    fun renameKey(
        root: Map<String, Any?>,
        parentPath: ConfigPath,
        oldKey: String,
        newKey: String,
    ): LinkedHashMap<String, Any?> {
        val clean = newKey.trim()
        require(clean.isNotEmpty()) { "字段名不能为空" }
        return transform(root, parentPath.parts) { container ->
            val source = container.asMutableMap()
            require(oldKey in source) { "原字段不存在" }
            require(clean == oldKey || clean !in source) { "字段 $clean 已存在" }
            linkedMapOf<String, Any?>().apply {
                source.forEach { (key, value) -> put(if (key == oldKey) clean else key, value) }
            }
        }.asRoot()
    }

    fun addMapEntry(
        root: Map<String, Any?>,
        mapPath: ConfigPath,
        key: String,
        type: ConfigValueType,
    ): LinkedHashMap<String, Any?> {
        val clean = key.trim()
        require(clean.isNotEmpty()) { "字段名不能为空" }
        return transform(root, mapPath.parts) { container ->
            container.asMutableMap().apply {
                require(clean !in this) { "字段 $clean 已存在" }
                put(clean, defaultValue(type))
            }
        }.asRoot()
    }

    fun addListItem(
        root: Map<String, Any?>,
        listPath: ConfigPath,
        type: ConfigValueType,
    ): LinkedHashMap<String, Any?> = transform(root, listPath.parts) { container ->
        container.asMutableList().apply { add(defaultValue(type)) }
    }.asRoot()

    fun moveListItem(
        root: Map<String, Any?>,
        listPath: ConfigPath,
        index: Int,
        direction: Int,
    ): LinkedHashMap<String, Any?> = transform(root, listPath.parts) { container ->
        container.asMutableList().apply {
            val target = index + direction
            require(index in indices && target in indices) { "已经到达列表边界" }
            val item = removeAt(index)
            add(target, item)
        }
    }.asRoot()

    private fun transform(
        current: Any?,
        path: List<ConfigPathPart>,
        change: (Any?) -> Any?,
    ): Any? {
        if (path.isEmpty()) return change(current)
        val next = path.first()
        val rest = path.drop(1)
        return when (next) {
            is ConfigPathPart.Key -> current.asMutableMap().apply {
                require(next.value in this) { "字段 ${next.value} 不存在" }
                this[next.value] = transform(this[next.value], rest, change)
            }
            is ConfigPathPart.Index -> current.asMutableList().apply {
                require(next.value in indices) { "列表位置已失效" }
                this[next.value] = transform(this[next.value], rest, change)
            }
        }
    }

    private fun Any?.asMutableMap(): LinkedHashMap<String, Any?> {
        require(this is Map<*, *>) { "目标不是对象" }
        return linkedMapOf<String, Any?>().apply {
            this@asMutableMap.forEach { (key, value) -> put(key.toString(), value) }
        }
    }

    private fun Any?.asMutableList(): MutableList<Any?> {
        require(this is List<*>) { "目标不是列表" }
        return toMutableList()
    }

    private fun Any?.asRoot(): LinkedHashMap<String, Any?> {
        require(this is Map<*, *>) { "配置根节点必须是对象" }
        return linkedMapOf<String, Any?>().apply {
            this@asRoot.forEach { (key, value) -> put(key.toString(), value) }
        }
    }

    private fun defaultValue(type: ConfigValueType): Any? = when (type) {
        ConfigValueType.TEXT -> ""
        ConfigValueType.NUMBER -> 0
        ConfigValueType.BOOLEAN -> true
        ConfigValueType.MAP -> linkedMapOf<String, Any?>()
        ConfigValueType.LIST -> mutableListOf<Any?>()
        ConfigValueType.NULL -> null
    }
}
