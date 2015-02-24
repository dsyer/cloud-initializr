package app

import io.spring.initializr.*
import io.spring.initializr.InitializrMetadata.BootVersion

@Grab('io.spring.initalizr:initializr:1.0.0.BUILD-SNAPSHOT')
class InitializerService {

  @Autowired
  ProjectGenerationMetricsListener metricsListener

  @Autowired
  CloudProperties cloud

  @Bean
  InitializrMetadataProvider initializrMetadataProvider(InitializrMetadata metadata) {
    new DefaultInitializrMetadataProvider(metadata) {
      protected List<InitializrMetadata.BootVersion> fetchBootVersions() {
        // Prevent older versions of Boot from being selected
        []
      }   
    }
  }

  @Bean
  ProjectGenerator projectGenerator() {
    def generator = new ProjectGenerator() {
      protected Map initializeModel(ProjectRequest request) {
        Map map = super.initializeModel(request)
        map.put('springCloudVersion', request.extra.springCloudVersion ?: InitializrMetadata.getDefault(cloud.versions))
        map
      }
    }
    generator.listeners << metricsListener
    generator
  }
  
}

@ConfigurationProperties('cloud')
class CloudProperties {
	final List<BootVersion> versions = []
}