{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SampleCache(private val redis: StringRedisTemplate) {

    private val ttl: Duration = Duration.ofMinutes(5)
    private val prefix: String = "sample:"

    fun get(key: String): String? = redis.opsForValue().get(prefix + key)

    fun put(key: String, value: String) {
        redis.opsForValue().set(prefix + key, value, ttl)
    }

    fun evict(key: String) {
        redis.delete(prefix + key)
    }
}
{{- end }}
