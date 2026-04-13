{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.persistence.migration

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import org.bson.Document

@ChangeUnit(id = "V001__create_sample_index", order = "001", author = "{{author}}")
class `V001__create_sample_index` {

    @Execution
    fun execute(db: MongoDatabase) {
        db.getCollection("sample_documents", Document::class.java)
            .createIndex(Indexes.ascending("name"), IndexOptions().unique(true))
    }

    @RollbackExecution
    fun rollback(db: MongoDatabase) {
        db.getCollection("sample_documents", Document::class.java)
            .dropIndex(Indexes.ascending("name"))
    }
}
{{- end }}
