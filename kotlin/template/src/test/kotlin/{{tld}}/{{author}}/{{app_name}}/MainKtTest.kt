package {{tld}}.{{author}}.{{app_name}}

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MainKtTest {
    @Test
    fun `enabled modules reflect the selected profile`() {
        assertEquals(
            listOf(
                "core",
{{ if or (eq stack_profile "cli") (eq stack_profile "web") (eq stack_profile "db") (eq stack_profile "web-db") -}}
                "{{stack_profile}}",
{{ end -}}
{{ if or (eq stack_profile "web") (eq stack_profile "web-db") -}}
                "web",
{{ end -}}
{{ if or (eq stack_profile "db") (eq stack_profile "web-db") -}}
                "db",
{{ end -}}
            ),
            Main.enabledModules(),
        )
    }
}
