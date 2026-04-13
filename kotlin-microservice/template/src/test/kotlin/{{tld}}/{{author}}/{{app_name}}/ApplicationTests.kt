package {{tld}}.{{author}}.{{app_name}}

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = [
    "aws.messaging.endpoint-override=http://localhost:4566",
    "aws.messaging.access-key=test",
    "aws.messaging.secret-key=test",
    "aws.messaging.queue-url=",
    "aws.messaging.topic-arn=",
{{- if eq stack_profile "nosql-cache" }}
    "app.mongo.uri=mongodb://localhost:27017/test",
    "app.mongo.database=test",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "mongock.enabled=false",
{{- end }}
])
class ApplicationTests {

    @Test
    fun contextLoads() {
    }
}
