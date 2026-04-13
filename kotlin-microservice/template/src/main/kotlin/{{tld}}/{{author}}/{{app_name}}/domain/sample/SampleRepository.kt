package {{tld}}.{{author}}.{{app_name}}.domain.sample

interface SampleRepository {
    fun save(sample: Sample): Sample

    fun findById(id: SampleId): Sample?
}
