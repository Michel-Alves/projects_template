{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.persistence

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.MongoCollection
import com.mongodb.kotlin.client.MongoDatabase
import org.springframework.stereotype.Component

@Component
class SampleDocumentRepository(db: MongoDatabase) {

    private val collection: MongoCollection<SampleDocument> =
        db.getCollection("sample_documents", SampleDocument::class.java)

    fun findById(id: String): SampleDocument? =
        collection.find(Filters.eq("_id", id)).firstOrNull()

    fun findByName(name: String): SampleDocument? =
        collection.find(Filters.eq("name", name)).firstOrNull()

    fun insert(doc: SampleDocument) {
        collection.insertOne(doc)
    }

    fun deleteById(id: String) {
        collection.deleteOne(Filters.eq("_id", id))
    }
}
{{- end }}
