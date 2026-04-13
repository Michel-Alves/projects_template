{{- if eq stack_profile "default" -}}
package {{tld}}.{{author}}.{{app_name}}

import {{tld}}.{{author}}.{{app_name}}.main.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [Application::class])
@TestPropertySource(properties = [
    "aws.messaging.endpoint-override=http://localhost:4566",
    "aws.messaging.access-key=test",
    "aws.messaging.secret-key=test",
    "aws.messaging.queue-url=",
    "aws.messaging.topic-arn=",
])
class ApplicationTests {

    @Test
    fun contextLoads() {
    }
}
{{- end -}}
