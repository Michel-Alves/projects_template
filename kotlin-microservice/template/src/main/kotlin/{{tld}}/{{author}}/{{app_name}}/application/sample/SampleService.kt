package {{tld}}.{{author}}.{{app_name}}.application.sample

import {{tld}}.{{author}}.{{app_name}}.commons.Clock
import {{tld}}.{{author}}.{{app_name}}.domain.sample.Sample
import {{tld}}.{{author}}.{{app_name}}.domain.sample.SampleId
import {{tld}}.{{author}}.{{app_name}}.domain.sample.SampleRepository
import org.springframework.stereotype.Service

@Service
class SampleService(
    private val sampleRepository: SampleRepository,
    private val clock: Clock,
) {
    fun register(name: String): Sample =
        sampleRepository.save(
            Sample(id = SampleId.random(), name = name, createdAt = clock.now()),
        )

    fun findById(id: SampleId): Sample? = sampleRepository.findById(id)
}
