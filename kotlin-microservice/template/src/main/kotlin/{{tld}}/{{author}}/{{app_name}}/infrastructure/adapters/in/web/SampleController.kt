package {{tld}}.{{author}}.{{app_name}}.infrastructure.adapters.`in`.web

import {{tld}}.{{author}}.{{app_name}}.application.sample.SampleService
import {{tld}}.{{author}}.{{app_name}}.domain.sample.SampleId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/samples")
class SampleController(
    private val sampleService: SampleService,
) {
    @PostMapping
    fun create(@RequestBody request: SampleRequest): ResponseEntity<SampleResponse> {
        val sample = sampleService.register(request.name)
        return ResponseEntity.status(201).body(SampleResponse.from(sample))
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: String): ResponseEntity<SampleResponse> =
        sampleService.findById(SampleId(id))
            ?.let { ResponseEntity.ok(SampleResponse.from(it)) }
            ?: ResponseEntity.notFound().build()
}
