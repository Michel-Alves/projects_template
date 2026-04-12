package {{tld}}.{{author}}.{{app_name}}

object Main {
    fun enabledModules(): List<String> = buildList {
        add("core")
{{ if or (eq stack_profile "cli") (eq stack_profile "web") (eq stack_profile "db") (eq stack_profile "web-db") -}}
        add("{{stack_profile}}")
{{ end -}}
{{ if or (eq stack_profile "web") (eq stack_profile "web-db") -}}
        add(ServerModule().name)
{{ end -}}
{{ if or (eq stack_profile "db") (eq stack_profile "web-db") -}}
        add(DatabaseModule().name)
{{ end -}}
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Hello {{author}} your program {{app_name}} is ready: ${enabledModules().joinToString()}")
    }
}

{{ if or (eq stack_profile "web") (eq stack_profile "web-db") -}}
class ServerModule {
    val name: String = "web"
}
{{ end -}}

{{ if or (eq stack_profile "db") (eq stack_profile "web-db") -}}
class DatabaseModule {
    val name: String = "db"
}
{{ end -}}
