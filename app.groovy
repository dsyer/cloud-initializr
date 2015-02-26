package app

import io.spring.initializr.*
import io.spring.initializr.web.*
import io.spring.initializr.InitializrMetadata.BootVersion

@Grab('io.spring.initalizr:initializr:1.0.0.BUILD-SNAPSHOT')
class InitializerService {

  @Autowired
  ProjectGenerationMetricsListener metricsListener

  @Autowired
  CloudProperties cloud

  @Autowired
  InitializrMetadata metadata

  @PostConstruct
  void update() {
    metadata.defaults.description = 'Demo project for Spring Cloud'
  }

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
        map.put('springCloudVersion', request.springCloudVersion ?: InitializrMetadata.getDefault(cloud.versions))
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
    String groupId
    String artifactId
}

@Controller
class CloudController extends MainController {

  @Autowired
  CloudProperties cloud

  @ModelAttribute
  @Override
  ProjectRequest projectRequest() {
    def request = new ProjectRequest() {
      String springCloudVersion
      protected addDefaultDependency() {
		def root = new InitializrMetadata.Dependency()
		root.id = cloud.artifactId
        root.groupId = cloud.groupId
        root.artifactId = cloud.artifactId
		resolvedDependencies << root
      }
    }
    metadataProvider.get().initializeProjectRequest(request)
    request
  }

}