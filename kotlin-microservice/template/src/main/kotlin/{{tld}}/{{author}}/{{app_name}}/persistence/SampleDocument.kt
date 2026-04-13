{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.persistence

import org.bson.codecs.pojo.annotations.BsonId

data class SampleDocument(
    @BsonId val id: String = "",
    val name: String = "",
)
{{- end }}
