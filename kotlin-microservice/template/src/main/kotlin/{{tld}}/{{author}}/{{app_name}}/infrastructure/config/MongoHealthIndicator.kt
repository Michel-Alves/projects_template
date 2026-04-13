{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.infrastructure.config

import com.mongodb.client.MongoClient
import org.bson.Document
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component("mongo")
class MongoHealthIndicator(private val client: MongoClient) : HealthIndicator {
    override fun health(): Health =
        try {
            client.getDatabase("admin").runCommand(Document("ping", 1))
            Health.up().build()
        } catch (ex: Exception) {
            Health.down(ex).build()
        }
}
{{- end }}
