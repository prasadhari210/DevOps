def build(props, disable_deploy_flag, artifactoryServer, project_workspace) {
	if (!props["mavenVersion"]) {
		pp_e("mavenVersion property required")
	}
	if (!props["jdkVersion"]) {
		pp_e("jdkVersion property required")
	}
	tool_exists(props["mavenVersion"])
	tool_exists(props["jdkVersion"])

	def mvnGoals 	= props['mavenGoals']
	if (isSet(props['mavenLocalRepo'])) {
		mvnGoals 		= "-Dmaven.repo.local=" + props['mavenLocalRepo'] + " " + mvnGoals
	}
	def artifactoryReleaseRepo 			= getAt(props["artifactoryResolverReleaseRepo"], "libs-release")
	def artifactorySnapshotRepo 		= getAt(props["artifactoryResolverSnapshotRepo"], "libs-snapshot")
	def artifactoryPublishReleases 	= "repo-"+props['appWamDistID'].toLowerCase()+"-releases"
	def artifactoryPublsihSnapshots = "repo-"+props['appWamDistID'].toLowerCase()+"-snapshots"
	def mvnBuild = Artifactory.newMavenBuild()
	mvnBuild.resolver server: 			artifactoryServer, 
										releaseRepo: 	artifactoryReleaseRepo, 
										snapshotRepo: artifactorySnapshotRepo
	mvnBuild.deployer server: 			artifactoryServer, 
										releaseRepo: 	artifactoryPublishReleases, 
										snapshotRepo: artifactoryPublsihSnapshots
	mvnBuild.tool 	= props['mavenVersion']
	if (isSet(props['mavenOpts'])) {
		mvnBuild.opts = props['mavenOpts']
	}
	env.JAVA_HOME =	tool name: props['jdkVersion']
	env.setProperty('PATH+JDK', env.JAVA_HOME + '/bin')
	if (isUnix() && props['buildPreBuildSetWorkspacePermsTo'].length() > 0) {
		sh 'chmod -Rf ' + props['buildPreBuildSetWorkspacePermsTo'] + ' ' + project_workspace
	}
	if (disable_deploy_flag) {
		mvnBuild.deployer.deployArtifacts = false	
	}

	// alex: not turning this on as some project seem to be setting this to strange value
	// if(isSet(props['mavenSettingsConfig'])) {
	//   configFileProvider([configFile(fileId: props['mavenSettingsConfig'], variable: 'jenkinsMavenSettingConfig')]) {
	//   	mvnGoals += " --settings ${env.jenkinsMavenSettingConfig}"
	//   }
	// }

	buildInfo = mvnBuild.run pom: props['mavenBuildFile'], goals: mvnGoals
  return buildInfo
}

def build_shell(props, maven_setting_config, project_workspace) {
	if (!props['mavenVersion']) {
		pp_e("mavenVersion property required")
	}
	if (!props["jdkVersion"]) {
		pp_e("jdkVersion property required")
	}
	if (!props["mavenBuildFile"]) {
		pp_e("mavenBuildFile property required")
	}	
	tool_exists(props["mavenVersion"])
	tool_exists(props["jdkVersion"])
	def mvnGoals 	= props['mavenGoals']
	if (props['mavenLocalRepo'] && props['mavenLocalRepo'].length() > 0) {
		mvnGoals += " -Dmaven.repo.local=" + props['mavenLocalRepo']
	}
	if (isUnix() && props['buildPreBuildSetWorkspacePermsTo'].length() > 0) {
		sh 'chmod -Rf ' + props['buildPreBuildSetWorkspacePermsTo'] + ' ' + project_workspace
	}

	def artifactoryPublishReleases = "repo-"+props['appWamDistID'].toLowerCase()+"-releases"
	def artifactoryPublsihSnapshots = "repo-"+props['appWamDistID'].toLowerCase()+"-snapshots"
	mvnGoals += " -f " + props['mavenBuildFile']
	if (!isTrue(props['mavenBypassAltDeploymentRepository'])) {
  	def alt_repo = '-DaltDeploymentRepository=central::default::http://wpvra98a0413.wellsfargo.com:8081/artifactory/'
	  if (is_snapshot(props['mavenBuildFile'])) {
	    alt_repo += artifactoryPublsihSnapshots
	  } else {
	    alt_repo += artifactoryPublishReleases
	  }
	  mvnGoals += " " + alt_repo
	}
  if (isSet(props['mavenSettingsConfig'])) {
  	maven_setting_config = props['mavenSettingsConfig']
  }
  withMaven(jdk: props['jdkVersion'], 
        maven: props['mavenVersion'], 
        mavenLocalRepo: '',
        mavenOpts: props['mavenOpts'], 
        mavenSettingsConfig: maven_setting_config,
        mavenSettingsFilePath: '$M2_HOME/conf/settings.xml') {
		if (isUnix()) {
			sh "mvn $mvnGoals"
		}	else {
			bat "mvn $mvnGoals"
		}				        
  } 
}

def is_shell_snapshot_build(props, maven_setting_config, project_workspace) {
	def tmp_snapshot = false
	def tmp_content
	def tmp_cmd

  if (isSet(props['mavenSettingsConfig'])) {
  	maven_setting_config = props['mavenSettingsConfig']
  }
	if (!isSet(props['mavenEffectivePomName'])) {
		props['mavenEffectivePomName'] = 'jenkins.effective.pom'
	}
	tmp_cmd = "mvn org.apache.maven.plugins:maven-help-plugin:2.2:effective-pom -f ${props['mavenBuildFile']} -Doutput=${project_workspace}/${props['mavenEffectivePomName']}"

  withMaven(jdk: props['jdkVersion'], 
        maven: props['mavenVersion'], 
        mavenLocalRepo: '',
        mavenOpts: props['mavenOpts'], 
        mavenSettingsConfig: maven_setting_config,
        mavenSettingsFilePath: '$M2_HOME/conf/settings.xml') {
		if (isUnix()) {
			sh tmp_cmd
		}	else {
			bat tmp_cmd
		}		        
  }
	tmp_content = readFile(props['mavenEffectivePomName'])
	println tmp_content
	tmp_snapshot = matches_regex(tmp_content, /<version>(.+?)-SNAPSHOT<\/version>/)
	if (tmp_snapshot) {
		pp "SNAPSHOT version detected"
	} else {
		pp "NO SNAPSHOT version detected"
	}
	tmp_snapshot
}

def pp(msg) {
	println "\u2756 ${msg} \u2756" 
}

def pp_e(msg) {
	error "\u2756 ${msg} \u2756"
}

@NonCPS
def trimProps(props) {
	def trimmedProps = [:]
	props.each() {key, value ->
		trimmedProps[key] = value.trim()
	}
	trimmedProps
}

def isSet(property) {
	if (property && property.trim().length() > 0) {
		return true
	} else {
		return false
	}
}

def is_snapshot(pom_file) {
  def pom = readMavenPom file: pom_file
  def matcher = pom.version =~ /(?i)snapshot/
  matcher ? true : false
}

def tool_exists(tool_name) {
  def tool_home = tool name: tool_name 
  def exists = fileExists tool_home
  if (exists) {
    pp "${tool_name}: ${tool_home}"
  } else {
    pp_e "${tool_name}: ${tool_home} doesn't exists..."
  }
}

def getAt(property, default_value) {
	if (property && property.trim().length() > 0) {
		return property
	} else {
		return default_value
	}
}

@NonCPS
def matches_regex(text, regex) {
  def matcher = text =~ regex
  matcher ? true : null
}

def isTrue(property) {
	if (property && property.trim() == "true") {
		return true
	} else {
		return false
	}
}

return this
