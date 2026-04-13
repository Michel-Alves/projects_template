{{- if eq stack_profile "relational-db" -}}
package {{tld}}.{{author}}.{{app_name}}.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["{{tld}}.{{author}}.{{app_name}}.infrastructure.adapters.out.persistence.jpa"])
@EntityScan(basePackages = ["{{tld}}.{{author}}.{{app_name}}.infrastructure.adapters.out.persistence.jpa"])
class JpaRepositoriesConfig
{{- end -}}
