{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.persistence

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.springframework.stereotype.Component

@Component
class SampleDocumentRepository(db: MongoDatabase) {

    private val collection: MongoCollection<Document> =
        db.getCollection("sample_documents")

    fun findById(id: String): SampleDocument? =
        collection.find(Filters.eq("_id", id)).first()?.toSampleDocument()

    fun findByName(name: String): SampleDocument? =
        collection.find(Filters.eq("name", name)).first()?.toSampleDocument()

    fun insert(doc: SampleDocument) {
        collection.insertOne(doc.toBson())
    }

    fun deleteById(id: String) {
        collection.deleteOne(Filters.eq("_id", id))
    }

    private fun SampleDocument.toBson(): Document =
        Document("_id", id).append("name", name)

    private fun Document.toSampleDocument(): SampleDocument =
        SampleDocument(id = getString("_id"), name = getString("name"))
}
{{- end }}
