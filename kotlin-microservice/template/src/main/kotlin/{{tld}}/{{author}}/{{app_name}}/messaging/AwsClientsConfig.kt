package {{tld}}.{{author}}.{{app_name}}.messaging

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
class AwsClientsConfig(private val props: AwsMessagingProperties) {

    @Bean
    fun snsClient(): SnsClient = SnsClient.builder()
        .region(Region.of(props.region))
        .credentialsProvider(credentialsProvider())
        .apply { props.endpointOverride.takeIf { it.isNotBlank() }?.let { endpointOverride(URI.create(it)) } }
        .build()

    @Bean
    fun sqsClient(): SqsClient = SqsClient.builder()
        .region(Region.of(props.region))
        .credentialsProvider(credentialsProvider())
        .apply { props.endpointOverride.takeIf { it.isNotBlank() }?.let { endpointOverride(URI.create(it)) } }
        .build()

    private fun credentialsProvider() =
        if (props.accessKey.isNotBlank() && props.secretKey.isNotBlank()) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey, props.secretKey))
        } else {
            DefaultCredentialsProvider.create()
        }
}
