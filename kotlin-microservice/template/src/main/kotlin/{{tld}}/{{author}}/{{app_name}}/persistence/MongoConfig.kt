{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.persistence

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.MongoClient
import com.mongodb.kotlin.client.MongoDatabase
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.PojoCodecProvider
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
        val codecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                PojoCodecProvider.builder().automatic(true).build(),
            ),
        )
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(properties.uri))
            .codecRegistry(codecRegistry)
            .build()
        return MongoClient.create(settings)
    }

    @Bean
    fun mongoDatabase(client: MongoClient): MongoDatabase =
        client.getDatabase(properties.database)
}
{{- end }}
