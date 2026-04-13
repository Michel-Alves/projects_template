{{- if eq stack_profile "relational-db" -}}
package {{tld}}.{{author}}.{{app_name}}.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class SampleRepositoryIntegrationTest @Autowired constructor(
    private val repository: SampleRepository,
) {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgres:{{postgres_image_tag}}")
        )
            .withDatabaseName("{{app_name}}")
            .withUsername("{{app_name}}")
            .withPassword("{{app_name}}")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.driver-class-name") { postgres.driverClassName }
        }
    }

    @Test
    fun `saves and reads back a sample entity`() {
        val saved = repository.save(SampleEntity(name = "hello"))
        assertThat(saved.id).isNotNull()

        val fetched = repository.findById(saved.id!!).orElseThrow()
        assertThat(fetched.name).isEqualTo("hello")
    }
}
{{- end -}}
