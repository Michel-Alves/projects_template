{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.persistence

import {{tld}}.{{author}}.{{app_name}}.cache.SampleCache
import {{tld}}.{{author}}.{{app_name}}.service.SampleDocumentService
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class SampleDocumentRepositoryIntegrationTest {

    @Autowired
    lateinit var service: SampleDocumentService

    @Autowired
    lateinit var repository: SampleDocumentRepository

    @Autowired
    lateinit var cache: SampleCache

    @Test
    fun `cache-aside round-trip across mongo and redis`() {
        val doc = SampleDocument(id = "sample-1", name = "alpha")
        service.insert(doc)

        val firstLookup = service.getByName("alpha")
        assertThat(firstLookup).isNotNull()
        assertThat(firstLookup!!.id).isEqualTo("sample-1")
        assertThat(cache.get("by-name:alpha")).isEqualTo("sample-1")

        repository.deleteById("sample-1")
        val secondLookup = service.getByName("alpha")
        assertThat(secondLookup).isNotNull()
        assertThat(secondLookup!!.id).isEqualTo("sample-1")

        service.evict("alpha")
        assertThat(cache.get("by-name:alpha")).isNull()
        val thirdLookup = service.getByName("alpha")
        assertThat(thirdLookup).isNull()
    }

    companion object {
        @Container
        @JvmStatic
        val mongo: MongoDBContainer = MongoDBContainer("mongo:{{mongo_image_tag}}").apply { start() }

        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:{{redis_image_tag}}"))
            .withExposedPorts(6379)
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("app.mongo.uri") { mongo.replicaSetUrl }
            registry.add("app.mongo.database") { "{{app_name}}-test" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
{{- end }}
