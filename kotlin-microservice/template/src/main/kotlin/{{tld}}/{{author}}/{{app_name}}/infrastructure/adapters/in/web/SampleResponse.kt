package {{tld}}.{{author}}.{{app_name}}.infrastructure.adapters.`in`.web

import {{tld}}.{{author}}.{{app_name}}.domain.sample.Sample
import java.time.Instant

data class SampleResponse(
    val id: String,
    val name: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(sample: Sample): SampleResponse =
            SampleResponse(id = sample.id.value, name = sample.name, createdAt = sample.createdAt)
    }
}
