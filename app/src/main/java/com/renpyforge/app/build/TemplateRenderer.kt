package com.renpyforge.app.build

/**
 * RAPT 模板的轻量渲染：替换 {{ token }}，删除整行 {% ... %} jinja 块。
 * 我们生成的配置固定为 store="none"、无 asset packs，因此模板里的条件块直接删除。
 */
object TemplateRenderer {

    fun render(template: String, values: Map<String, String>): String {
        var out = template
        for ((k, v) in values) {
            out = out.replace("{{ $k }}", v).replace("{{$k}}", v)
        }
        // 删除 {% if ... %} / {% endif %} / 其它整行 jinja 标签
        out = out.replace(Regex("(?m)^\\s*\\{%.*?%\\}\\s*$"), "")
        // 行内残留的 {% ... %}（保守删除）
        out = out.replace(Regex("\\{%.*?%\\}"), "")
        return out
    }
}
