{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.service

import {{tld}}.{{author}}.{{app_name}}.cache.SampleCache
import {{tld}}.{{author}}.{{app_name}}.persistence.SampleDocument
import {{tld}}.{{author}}.{{app_name}}.persistence.SampleDocumentRepository
import org.springframework.stereotype.Service

@Service
class SampleDocumentService(
    private val repository: SampleDocumentRepository,
    private val cache: SampleCache,
) {
    fun insert(doc: SampleDocument) {
        repository.insert(doc)
    }

    fun getByName(name: String): SampleDocument? {
        // Cache stores only the id (StringRedisTemplate-backed). If SampleDocument grows
        // new fields, either add JSON serialization here or switch to RedisTemplate<String, SampleDocument>.
        val cacheKey = "by-name:$name"
        cache.get(cacheKey)?.let { cachedId ->
            return SampleDocument(id = cachedId, name = name)
        }
        val doc = repository.findByName(name) ?: return null
        cache.put(cacheKey, doc.id)
        return doc
    }

    fun evict(name: String) {
        cache.evict("by-name:$name")
    }
}
{{- end }}
