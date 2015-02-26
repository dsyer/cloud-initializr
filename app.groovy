package app

import io.spring.initializr.*
import io.spring.initializr.web.*
import io.spring.initializr.InitializrMetadata.BootVersion
import io.spring.initializr.InitializrMetadata.DependencyGroup
import groovy.util.logging.Slf4j

@Grab('io.spring.initalizr:initializr:1.0.0.BUILD-SNAPSHOT')
@Slf4j
class InitializerService {

  @Autowired
  ProjectGenerationMetricsListener metricsListener

  @Autowired
  CloudProperties cloud

  @Autowired
  InitializrMetadata metadata

  @Bean
  InitializrMetadataCustomizer initializrMetadataCustomizer() {
    new InitializrMetadataCustomizer() {
      @Override
      void customize(InitializrMetadata metadata) {
        metadata.defaults.description = 'Demo project for Spring Cloud'
        log.info("Adding cloud dependencies")
        cloud.dependencies.reverse().each { group -> 
          metadata.dependencies.add(0, group)
        }
        metadata.bootVersions.clear()
        metadata.bootVersions.addAll(cloud.bootVersions)
      }
    }
  }

  @Bean
  InitializrMetadataProvider initializrMetadataProvider(InitializrMetadata metadata) {
    new InitializrMetadataProvider() {
      @Override
      InitializrMetadata get() {
        metadata
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
	final List<BootVersion> bootVersions = []
	final List<DependencyGroup> dependencies = []
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