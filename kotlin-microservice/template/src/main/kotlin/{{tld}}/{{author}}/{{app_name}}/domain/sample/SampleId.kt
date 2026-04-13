package {{tld}}.{{author}}.{{app_name}}.domain.sample

import java.util.UUID

@JvmInline
value class SampleId(val value: String) {
    init {
        require(value.isNotBlank()) { "SampleId must not be blank" }
    }

    companion object {
        fun random(): SampleId = SampleId(UUID.randomUUID().toString())
    }
}
