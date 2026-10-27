package br.com.ativeplay.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@ConditionalOnProperty(name = "STORAGE_TIPO", havingValue = "s3")
public class S3Config {

    @Bean
    @ConfigurationProperties(prefix = "ativeplay.storage.s3")
    public S3Properties s3Properties() {
        return new S3Properties();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties props) {
        return S3Client.builder()
                .endpointOverride(props.getEndpoint())
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                // Supabase e outros S3 compatíveis usam o bucket no caminho, não no host.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
