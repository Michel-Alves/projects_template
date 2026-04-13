package {{tld}}.{{author}}.{{app_name}}.domain.sample

import java.time.Instant

data class Sample(
    val id: SampleId,
    val name: String,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Sample name must not be blank" }
    }
}
