{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.infrastructure.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "app.mongo")
data class MongoProperties(
    val uri: String = "",
    val database: String = "",
)

@Configuration
@EnableConfigurationProperties(MongoProperties::class)
class MongoConfig(private val properties: MongoProperties) {

    @Bean(destroyMethod = "close")
    fun mongoClient(): MongoClient {
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(properties.uri))
            .build()
        return MongoClients.create(settings)
    }

    @Bean
    fun mongoDatabase(client: MongoClient): MongoDatabase =
        client.getDatabase(properties.database)
}
{{- end }}
