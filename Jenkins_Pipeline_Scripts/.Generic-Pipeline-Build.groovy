def props
def artifactoryServer
def buildInfo = ""
def modifiedBranch = ""
def scmSourceRef = ""
def UCD_SERVER = "Prod uDeploy Pipeline"
def ucd_component_name
def ucd_version_name
def BUILD_PIPELINE_PROPS = 'BuildPipelineProps'
def BUILD_PIPELINE_EXTERNAL = 'BuildPipelineExternal'
def external_method = null
def lib_build_external_method = null
def lib_scm_external_method = null
def lib_publish_external_method = null
def LIB_EMAIL_EXTERNAL_METHOD = null
def LIB_UCD_EXTERNAL_METHOD = null
def NUGET_CONFIG_FILENAME = 'nuget.config'
def BUILD_PIPELINE_SCRIPT_URL = "https://wfs-github.wellsfargo.com/DevOps/Jenkins_Pipeline_Scripts"
def FILE_PROVIDER_FOR_GRADLE_PROPERTIES = 'gradle.properties'
def FILE_PROVIDER_FOR_MAVEN_SETTINGS = 'maven.ge.settings.xml'
def ARTIFACTORY_IDS = '1798436334@1477399862005'
def PROJECT_WORKSPACE
def DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES = false
def AHP_URL = "https://wts-ahp.wellsfargo.com:8443/trigger"
def DEFAULT_BUILD_NUMBER
def UCD_URL = "https://ucd.wellsfargo.com:8443"
def DEFAULT_SET_UCD_STATUS = "Release"
def EXTERNAL_CHECKOUT_BRANCH = "master"

node {	
	stage('Prepare For Build') {
		env.BRANCH = env.BRANCH.trim()
		pp("BRANCH: ${env.BRANCH}")
		echo "Getting Properties File"
		def wamID
		if (env.wam_ID && env.wam_ID.length()> 0) {
			echo "Using wam_ID property set on Jenkins project"
			wamID = env.wam_ID
		} else {
			echo "Determining wamID based on Jenkins project name"
			def nameArray = env.JOB_NAME.split("-")
			wamID = nameArray[0]
		}
		def propFilePath = wamID+"/"+env.JOB_NAME+".properties"
		echo "propFilePath: "+propFilePath
		def propRepoUrl = "https://wfs-github.wellsfargo.com/DevOps/Jenkins_Pipeline_Property_Files"
		try {
			pp("Checking out ${propFilePath}")
			checkout_build_pipeline_file(propRepoUrl, propFilePath, BUILD_PIPELINE_PROPS, 'master')
		} catch (Exception e) {
			pp("Error: Verify that ${propFilePath} exists.")
      throw e
    }
		props = load_property_file(BUILD_PIPELINE_PROPS + '/' + propFilePath)
		modifiedBranch = env.BRANCH.replaceAll("/", "-")
		if ((props['scmType']) == "n/a") {
			if (env.BRANCH == "\$GIT_BRANCH" || !env.BRANCH) {
				modifiedBranch = "na"
			} 
		}
		if (should_load_external_method(props)) {
			def gvyFilePath = wamID+"/"+env.JOB_NAME+".gvy"
			try {
				pp("Checking out external script ${gvyFilePath}")
				external_method = load_external_method(propRepoUrl, gvyFilePath, BUILD_PIPELINE_EXTERNAL, 'master')
			} catch (Exception e) {
				pp("Error: Verify that ${gvyFilePath} exists.")
	      throw e
	    }			
		}
		switch(props['buildType']) {
			case ['fortify java', 'fortify msbuild']:	
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/fortify.gvy", "build_pipeline/fortify", EXTERNAL_CHECKOUT_BRANCH)
				break
			case ["gradle", "gradle shell"]:
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/gradle.gvy", "build_pipeline/gradle", EXTERNAL_CHECKOUT_BRANCH)
				break
			case ["maven", "maven shell"]:
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/maven.gvy", "build_pipeline/maven", EXTERNAL_CHECKOUT_BRANCH)
				break											
			case "ibm integration toolkit":
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/ibm.toolkit.gvy", "build_pipeline/ibm.toolkit", EXTERNAL_CHECKOUT_BRANCH)
				break	
			case ['sonar', 'sonar msbuild']:
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/sonar.gvy", "build_pipeline/sonar", EXTERNAL_CHECKOUT_BRANCH)
				break
			case ['msbuild']:
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/msbuild.gvy", "build_pipeline/msbuild", EXTERNAL_CHECKOUT_BRANCH)
				break						
			case ['tibco']:
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/tibco.gvy", "build_pipeline/tibco", EXTERNAL_CHECKOUT_BRANCH)
				break					
			case ['npm']:
				lib_build_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/npm.gvy", "build_pipeline/npm", EXTERNAL_CHECKOUT_BRANCH)
				break					
			default:
				break
		}
		if (['tfs'].contains(props['scmType'])) {
			lib_scm_external_method 		= load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/scm.gvy", "build_pipeline/scm", EXTERNAL_CHECKOUT_BRANCH)
		}

		if (isSet(props['nugetPackageDir'])) {
			lib_publish_external_method = load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/nuget.gvy", "build_pipeline/nuget", EXTERNAL_CHECKOUT_BRANCH)
		}
		LIB_EMAIL_EXTERNAL_METHOD 		= load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/email.gvy", "build_pipeline/email", EXTERNAL_CHECKOUT_BRANCH)
		LIB_UCD_EXTERNAL_METHOD 			= load_external_method(BUILD_PIPELINE_SCRIPT_URL, "lib/ucd.gvy", "build_pipeline/ucd", EXTERNAL_CHECKOUT_BRANCH)

		DEFAULT_BUILD_NUMBER = env.BUILD_NUMBER
		if (props['appCustomVersion'] == "true") {
			def customVersionStringHash = getCustomVersionStringHash(props['appCustomVersionString'])
			def version = null
			if (customVersionStringHash[modifiedBranch]) {
				println "Setting $BRANCH custom version number"
				version = VersionNumber projectStartDate: '1969-12-31', skipFailedBuilds: true, versionNumberString: customVersionStringHash[modifiedBranch], versionPrefix: ''
			} else if(customVersionStringHash['default']) {
				println "Setting default custom version number"
				version = VersionNumber projectStartDate: '1969-12-31', skipFailedBuilds: true, versionNumberString: customVersionStringHash['default'], versionPrefix: ''	
			} 
			if (version) {
				env.BUILD_NUMBER 					= version
				currentBuild.displayName 	= version
				println version
			} else {
				println "No custom version number string found..."
			}
		}
		if (isTrue(props['appCustomVersionToUCD'])) {
			ucd_version_name 		= get_version_name(modifiedBranch, props, env.BUILD_NUMBER)
		} else {
			ucd_version_name 		= get_version_name(modifiedBranch, props, DEFAULT_BUILD_NUMBER)
		}
		ucd_component_name 	= get_ucd_component_name(props)
		// jenkins_instance = get_jenkins_instance(env.BUILD_URL, JENKINS_INSTANCES)
	}
}

def generic_pipeline_closure = {
	try {
		pre_checkout_step(external_method, props)
		artifactoryServer 					= Artifactory.server(ARTIFACTORY_IDS)
		artifactoryServer.username 	= 'jenkins'
		artifactoryServer.password 	= 'AP947ydr1p47PbWkVykjcMzGXC5'

		stage('SCM Checkout') {

			if (props['buildCleanWorkspace'] == "true") {
				echo "Cleaning workspace"
				step([$class: 'WsCleanup'])
			}

			echo "Checking out Source Code"	
			
			switch (props['scmType']) {
				case "svn":
					echo "Checking out source code from SVN repository"
					def svn_locations 	= set_svn_locations(props)
					def info 						= ""
					def revision 				= [:]
					def svnRevisionMap 	= [:]
					checkout poll: false,
							scm: [$class: 'SubversionSCM', 
								additionalCredentials: [], 
								excludedCommitMessages: props['svnExcludedCommitMessages'], 
								excludedRegions: props['svnExcludedRegions'], 
								excludedRevprop: props['svnExcludedRevprop'], 
								excludedUsers: props['svnExcludedUsers'], 
								filterChangelog: false, 
								ignoreDirPropChanges: false, 
								includedRegions: props['svnIncludedRegions'], 
								locations: svn_locations,
								workspaceUpdater: [$class: props['svnCheckoutStrategy']]]

					for (i=0; i < svn_locations.size(); i++) {
						if (isUnix()) {
							info = sh(script: "svn info \"${svn_locations[i].local}\"", returnStdout: true)
						} else {
							info = bat(script: "svn info \"${svn_locations[i].local}\"", returnStdout: true)
						}
						revision = get_hash_from_svn_info(info)
						if (!svnRevisionMap[revision["Repository Root"]]) {
							svnRevisionMap[revision["Repository Root"]] = revision["Revision"]
							if (scmSourceRef == "") {
								scmSourceRef = "${revision['Repository Root']}:${revision['Revision']}"
							} else {
								scmSourceRef += "|${revision['Repository Root']}:${revision['Revision']}"
							}
						}
					}
					echo "scmSourceRef = "+scmSourceRef 
					break
				case "git":
					echo "Checking out source code from Git repository"
					def git_extension 
					switch (props['gitSparseCheckout']) {
						case "true":					
							echo "Performing Sparse Checkout"
							git_extension = [[$class: 'CleanBeforeCheckout'], [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: set_git_sparse_checkout_paths(props)]]
							break
						default:
							echo "Performing Full Checkout"
							git_extension = [[$class: 'CleanBeforeCheckout']]
							break
					}
					checkout 	poll: false, 
										scm: [$class: 'GitSCM', 
											branches: [[name: BRANCH]], 
											doGenerateSubmoduleConfigurations: false, 
											extensions: git_extension, 
											submoduleCfg: [], 
											userRemoteConfigs: [[credentialsId: 'Wfsjenkinsbuild', url: props['gitRepoURL']]]
										]	
					break
				case "tfs":
					lib_scm_external_method.tfs_checkout(props, PROJECT_WORKSPACE)
					break
				case "n/a":
					echo "Vendor drop, no source code"		
					break			
				default: 
					error '\"'+props['scmType']+'\" is not a valid SCM Type!!  Valid value must be set in the project specific properties file.'
			}
		}

		if (props['buildType'] == 'sonar' || props['buildType'] == 'sonar msbuild') {
			switch(props['buildType']) {
				case "sonar":
					pre_build_step(external_method, props)
					lib_build_external_method.java_scan(props, artifactoryServer, PROJECT_WORKSPACE, FILE_PROVIDER_FOR_GRADLE_PROPERTIES, external_method)
				  post_build_step(external_method, props)						
					break
				case "sonar msbuild":
					pre_build_step(external_method, props)
					nuget_restore(props, NUGET_CONFIG_FILENAME)
					lib_build_external_method.msbuild_scan(props, artifactoryServer, PROJECT_WORKSPACE, FILE_PROVIDER_FOR_GRADLE_PROPERTIES, external_method)
					post_build_step(external_method, props)
					break				
				default:
	    		pp_e("${props['buildType']} is not a supported Build Type!!  Valid value must be set in the project specific properties file.")
			}
		} else {
			stage('Build & Publish to Artifactory') {		
				echo "Starting Build"
				if (isSet(props['nugetPackageDir'])) {
					lib_publish_external_method.pre_build_step(props)
				}				
				switch (props['buildType']) {
					case "n/a":
						pre_build_step(external_method, props)
						echo "No compile build"
						post_build_step(external_method, props)
						if (props['artifactoryPublishToArtifactory'] && props['artifactoryPublishToArtifactory'] == 'false') {
							echo "Skipping artifact publish to Artifactory based on artifactoryPublishToArtifactory property value set to false"
						} else {
							dir(props['buildArtifactsPath']) {
								buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
								artifactoryServer.publishBuildInfo buildInfo
							}							
						}
						break
					case "ibm integration toolkit":
						pre_build_step(external_method, props)
						lib_build_external_method.build(props)
						post_build_step(external_method, props)
						if (props['artifactoryPublishToArtifactory'] && props['artifactoryPublishToArtifactory'] == 'false') {
							echo "Skipping artifact publish to Artifactory based on artifactoryPublishToArtifactory property value set to false"
						} else {
							dir(props['buildArtifactsPath']) {
								buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
								artifactoryServer.publishBuildInfo buildInfo
							}							
						}
						break						
					case "maven":
						pre_build_step(external_method, props)
						if (shouldDisableArtifactDeployForBranches(props)) {
							DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES = true			
						}
						buildInfo = lib_build_external_method.build(props, DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES, artifactoryServer, PROJECT_WORKSPACE)
						post_build_step(external_method, props)
					  if (isTrue(props['mavenPublishToGenericRepo'])) {
							dir(props['buildArtifactsPath']) {
								buildInfo.append artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))			
							}					  	
					  }
						artifactoryServer.publishBuildInfo buildInfo
						if (is_snapshot_build(buildInfo)) {
							DEFAULT_SET_UCD_STATUS = 'Snapshot'
						} 											
				    break
					case "maven shell":
						pre_build_step(external_method, props)
						lib_build_external_method.build_shell(props, FILE_PROVIDER_FOR_MAVEN_SETTINGS, PROJECT_WORKSPACE)
				    post_build_step(external_method, props)
					  if (isTrue(props['mavenPublishToGenericRepo'])) {
							dir(props['buildArtifactsPath']) {
								buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))			
								artifactoryServer.publishBuildInfo buildInfo				    
							}					  	
					  }
						if (lib_build_external_method.is_shell_snapshot_build(props, FILE_PROVIDER_FOR_MAVEN_SETTINGS,PROJECT_WORKSPACE)) {
							DEFAULT_SET_UCD_STATUS = 'Snapshot'
						} 						  
				    break			    
		    	case "msbuild":
		    		pre_build_step(external_method, props)
		    		lib_build_external_method.build(props, NUGET_CONFIG_FILENAME, PROJECT_WORKSPACE)
						post_build_step(external_method, props)
	    			dir(props['buildArtifactsPath']) {
							buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
							artifactoryServer.publishBuildInfo buildInfo
						}
		    		break
		    	case "ant":
		    		pre_build_step(external_method, props)
		    		env.JAVA_HOME=tool name: props['jdkVersion']
						env.setProperty('PATH+JDK', env.JAVA_HOME + '/bin')
	    			if (isUnix()) {
	    				sh "\"${tool "${props['antVersion']}"}\"/bin/ant -buildfile ${props['antBuildFile']} ${props['antBuildTarget']}"
	    			} else {
	    				bat "\"${tool "${props['antVersion']}"}\"/bin/ant.bat -buildfile ${props['antBuildFile']} ${props['antBuildTarget']}"
	    			}    			
						post_build_step(external_method, props)
						dir(props['buildArtifactsPath']) {
							buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
							artifactoryServer.publishBuildInfo buildInfo
						}
		    		break
		    	case "grunt":
		    		pre_build_step(external_method, props)
		    		def phantomjs_version 
	    			if (isSet(props['gruntPhantomJSVersion'])) {
		    			phantomjs_version = tool name: props['gruntPhantomJSVersion']
							env.setProperty('PATH+PHANTOMJS', phantomjs_version + "/bin")
	    			}
		    		env.NODEJS_HOME = tool name: props['gruntNodeJSVersion']
		    		env.setProperty('PATH+NODE', env.NODEJS_HOME + '/bin')	
    				if (isSet(props['gruntNodeModulesPath'])) {
							env.setProperty('PATH+GRUNT', PROJECT_WORKSPACE + "/${props['gruntNodeModulesPath']}/.bin")	
    				} else {
							env.setProperty('PATH+GRUNT', PROJECT_WORKSPACE + "/node_modules/.bin")	
    				}
		    		// Window have npm in the default home dir???
		    		if (!isUnix()) {
		    			env.setProperty('PATH+NODEHOME', env.NODEJS_HOME)
		    		}    					    		
	    			dir(PROJECT_WORKSPACE + "/" + props['gruntBuildDir']) {
		    			if (isUnix()) {
		    				if (fileExists('npm-shrinkwrap.json') && props['gruntDeleteShrinkwrapFile'] && props['gruntDeleteShrinkwrapFile'] == "true") {
		    					sh "rm -f npm-shrinkwrap.json"
		    				}
		    				sh 'npm install grunt-cli'
		    				sh 'npm install'
		    				sh "grunt ${props['gruntBuildTask']} --gruntfile ${props['gruntBuildFile']}"
		    			} else {
		    				if (fileExists('npm-shrinkwrap.json') && props['gruntDeleteShrinkwrapFile'] && props['gruntDeleteShrinkwrapFile'] == "true") {
		    					bat "del npm-shrinkwrap.json"
		    				}
								bat 'npm install grunt-cli'    				    				
		    				bat 'npm install'
		    				bat "grunt ${props['gruntBuildTask']} --gruntfile ${props['gruntBuildFile']}"
		    			}
		    		}
		    		def artifactsPath = PROJECT_WORKSPACE + "/" + props['buildArtifactsPath']
		    		post_build_step(external_method, props)
	    			if (props['dependencyBuild'] == "true") {
							echo "Publishing dependency build artifacts to Artifactory"
							def npmRepo = "repo-"+props['appWamDistID']+"-npm"
							def npmUrl = props['artifactoryUrl']+"/api/npm/"+npmRepo
							def npmArtifactPath = artifactsPath+"/"+props['deployableArtifactsPattern']
							def npm_command = """
							npm pack
							npm publish $npmArtifactsPath --registry $npmUrl
							"""
							if (isUnix()) {
								sh npm_command
							} else {
								bat npm_command
							}
						} else {
							dir(artifactsPath) {
								buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
								artifactoryServer.publishBuildInfo buildInfo						
							}
						}
		    		break
		    	case "gradle":
		    		pre_build_step(external_method, props)
						if (shouldDisableArtifactDeployForBranches(props)) {
							DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES = true			
						}
						buildInfo = lib_build_external_method.build(props, FILE_PROVIDER_FOR_GRADLE_PROPERTIES, DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES, artifactoryServer)
						post_build_step(external_method, props)
					  if (isTrue(props['gradlePublishToGenericRepo'])) {
							dir(props['buildArtifactsPath']) {
								buildInfo.append artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))			
							}					  	
					  }
						artifactoryServer.publishBuildInfo buildInfo
						if (is_snapshot_build(buildInfo)) {
							DEFAULT_SET_UCD_STATUS = 'Snapshot'
						} 
		    		break	    		
		    	case "gradle shell":
		    		pre_build_step(external_method, props)
		    		lib_build_external_method.build_shell(props, FILE_PROVIDER_FOR_GRADLE_PROPERTIES)
						post_build_step(external_method, props)
					  if (isTrue(props['gradlePublishToGenericRepo'])) {
							dir(props['buildArtifactsPath']) {
								buildInfo.append artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))			
							}					  	
					  }						
		    		break
	    		case "tibco":
						pre_build_step(external_method, props)
						lib_build_external_method.build(props, artifactoryServer, PROJECT_WORKSPACE, FILE_PROVIDER_FOR_GRADLE_PROPERTIES)
					  post_build_step(external_method, props)
						dir(props['buildArtifactsPath']) {
							buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
							artifactoryServer.publishBuildInfo buildInfo
						}
						break
	    		case "npm":
						pre_build_step(external_method, props)
						lib_build_external_method.build(props, PROJECT_WORKSPACE)
					  post_build_step(external_method, props)
						if (props['dependencyBuild'] == "true") {
							dir(props['npmBuildDir']) {
								lib_build_external_method.publish(props)
							}
						} else {
							dir(props['buildArtifactsPath']) {
								buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
								artifactoryServer.publishBuildInfo buildInfo
							}
						}
						break						
					case "fortify java":
						pre_build_step(external_method, props)
						lib_build_external_method.fortify_java_build_step(props, artifactoryServer, PROJECT_WORKSPACE, FILE_PROVIDER_FOR_GRADLE_PROPERTIES, external_method)
					  post_build_step(external_method, props)
						dir(props['buildArtifactsPath']) {
							buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
							artifactoryServer.publishBuildInfo buildInfo						
						}					  						
						break	
					case "fortify msbuild":
						pre_build_step(external_method, props)
						nuget_restore(props, NUGET_CONFIG_FILENAME)
						lib_build_external_method.fortify_msbuild_build_step(props, PROJECT_WORKSPACE, external_method)
					  post_build_step(external_method, props)
						echo "Publishing Fortify build artifacts to Artifactory"
						dir(props['buildArtifactsPath']) {
							buildInfo = artifactoryServer.upload(set_uploadSpec(props, modifiedBranch))
							artifactoryServer.publishBuildInfo buildInfo						
						}					  						
						break														    		
		    	case "shell":
		    		pp_e("Shell build not yet supported - See Cory with details")
		    		break
		    	default:
		    		pp_e("${props['buildType']} is not a supported Build Type!!  Valid value must be set in the project specific properties file.")
				}

				// // validate that there was content publish to artifactory
				if (buildInfo) {
					if (buildInfo['deployedArtifacts'].size() < 1 && buildInfo['modules'].size() < 1 ) {
						if(DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES) {
							pp("Skipping Artifactory publish due to disableDeployArtifactsForBranches/enableDeployArtifactsForBranches property")
						} else {
							error "No build artifacts were push to Artifactory. This presents a significant audit issue as artifacts are not getting placed in the SOR(Artifactory)"								
						}
					}
				}		
			}

			if (isSet(props['nugetPackageDir'])) {
				if (DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES) {
					pp("Skipping Nuget publish due to disableDeployArtifactsForBranches/enableDeployArtifactsForBranches property")
				} else {
					lib_publish_external_method.post_build_step(props)
				}
			}	

			if (isSet(props['ahpTriggerCode'])){
				post_ahp(props, AHP_URL, modifiedBranch, DEFAULT_BUILD_NUMBER)
			}
			
			if (["git", "tfs"].contains(props['scmType'])) {
				stage('Create Build Tag') {
					if (props['scmType'] == "git") {
						def buildTag = ucd_version_name
						echo "Creating build tag in GitHub repo"
						def repoArray = props['gitRepoURL'].split("//")
						def gitRepo 	= repoArray[1]
						// Using withCredentials as GitPublisher is not yet supported in pipeline scripts
						withCredentials([usernamePassword(credentialsId: 'Wfsjenkinsbuild', passwordVariable: 'gitPassword', usernameVariable: 'gitUserName')]) {
							if (isUnix()) {
								sh 'git tag -a '+buildTag+' -m "tagged by Jenkins"'
								sh 'git push https://${gitUserName}:${gitPassword}@'+gitRepo+' '+buildTag
							} else {
								bat "git tag -a ${buildTag} -m \"tagged by Jenkins\""
								bat "git push https://${gitUserName}:${gitPassword}@${gitRepo} ${buildTag}"
							}
							scmSourceRef = buildTag		
						}
					}
					if (props['scmType'] == "tfs") {
						scmSourceRef = lib_scm_external_method.tfs_label(props, PROJECT_WORKSPACE)
					}
				}
			}

			LIB_UCD_EXTERNAL_METHOD.publish_to_ucd(props, DISABLE_DEPLOY_ARTIFACTS_FOR_BRANCHES, PROJECT_WORKSPACE, UCD_SERVER, ucd_component_name, ucd_version_name, scmSourceRef, UCD_URL, DEFAULT_SET_UCD_STATUS, DEFAULT_BUILD_NUMBER)
			LIB_UCD_EXTERNAL_METHOD.deploy_to_ucd(props, UCD_SERVER, ucd_component_name, ucd_version_name)			
		}

		if (props['buildDownstreamJobs'] && props['buildDownstreamJobs'].length() > 0) {
			stage('Run Downstream Jobs') {
				def jobList = props['buildDownstreamJobs'].split(",")

				for (i=0; i<jobList.size(); i++) {
					pp "Running Downstream Job: \""+jobList[i]+"\" on Branch \""+BRANCH+"\""
					build job: jobList[i], parameters: [[$class: 'StringParameterValue', name: 'BRANCH', value: BRANCH]], wait: props['buildDownstreamJobsWait'].toBoolean()
				}
			}
		}
		if (props['emailSuccessRecipients'] && props['emailSuccessRecipients'].length() > 0) {
			pp "Sending Success notification"
			LIB_EMAIL_EXTERNAL_METHOD.send_success_email(props)
		}
	} catch (Exception e) {
		if (props['emailSendFailureToCommitters'] && props['emailSendFailureToCommitters'] == "true") {
			pp "Sending Failure notification to committers"
			LIB_EMAIL_EXTERNAL_METHOD.send_blame_email(props)
		} else if (props['emailFailureRecipients'] && props['emailFailureRecipients'].length() > 0) {
			echo "Sending Failure notification"
			LIB_EMAIL_EXTERNAL_METHOD.send_failure_email(props)
		}
		throw e
	}
}

node(props['buildHostLabel']) {
	if(props['customWorkSpace'] && props['customWorkSpace'].size() > 0) {
		ws(props['customWorkSpace']) {
			PROJECT_WORKSPACE = pwd()
			generic_pipeline_closure.call()
		}
	} else {
		PROJECT_WORKSPACE = pwd()
		generic_pipeline_closure.call()
	}
}


@NonCPS
//Method to trim property values
def trimProps(props) {
	def trimmedProps = [:]

	props.each() {key, value ->
		trimmedProps[key] = value.trim()
	}
	
	trimmedProps
}

@NonCPS
// Method returns value for Artifactory upload spec
def set_uploadSpec(props, modifiedBranch) {
	def artifactoryPath

	if (props['artifactoryPath']) {
		artifactoryPath = props['artifactoryPath']
	} else {
 		artifactoryPath = "repo-"+props['appWamDistID'].toLowerCase()+"-deployable/" + appComponentNameToLowercase(props)
	}

	
	if (modifiedBranch == "na") {
		artifactoryPath += "/${env.BUILD_NUMBER}/"
	} else {
		artifactoryPath += "/$modifiedBranch/${env.BUILD_NUMBER}/"
	}


	def recursivePublish = true
	if (props['buildArtifactsRecursivePublish'] && props['buildArtifactsRecursivePublish'] == "false") {
		recursivePublish = false
	}

	def uploadSpec
	if (props['artifactoryArtifactsFilePatternRegex'] && props['artifactoryArtifactsFilePatternRegex'].length() > 0) {
		uploadSpec = """{
			"files": [
				{
					"pattern": "${props['artifactoryArtifactsFilePatternRegex']}",
					"target": "${artifactoryPath}",
					"recursive": "${recursivePublish}",
					"regexp": "true",
					"flat": "false"
				}
			]
		}"""
	} else {
		uploadSpec = """{
			"files": [
				{
					"pattern": "${props['artifactoryArtifactsFilePatternWildcard']}",
					"target": "${artifactoryPath}",
					"recursive": "${recursivePublish}",
					"flat": "false"
				}
			]
		}"""
	}

	uploadSpec
}

@NonCPS
def appComponentNameToLowercase(props) {
	return props['appName'].toLowerCase()+"/"+props['appComponentName'].toLowerCase()
}

@NonCPS
// svn_locations method is used to build 1 to many locations that will be used in the SVN checkout of the application source repository
// this enables having 1 to many sub directories in the checkout without having to checkout entire repository or unnecessary directories
def set_svn_locations(props) {
	def arrayList = []
	def repoArray = []
	def offsetListArray = []
	def offsetArray = []
	def repoList = props['svnRepo']
	def offsetList = props['svnRepoOffset']

	repoArray = repoList.tokenize("|")
	
	if (offsetList) {
		offsetListArray = offsetList.tokenize("|")
	}
	
	for (i=0; i < repoArray.size(); i++) {
		def offsetPath = ""

		if (offsetListArray.size() > 0) {
			offsetArray = offsetListArray[i].tokenize(",")
			offsetPath = offsetListArray[i]
		}

		if (offsetArray.size() > 1) {
			offsetArray.each() { path ->
				arrayList.push([
					credentialsId: 'Wfsjenkinsbuild', 
					depthOption: props['svnDepth'], 
					ignoreExternalsOption: true, 
					local: path, 
					remote: repoArray[i]+"/$BRANCH/"+path
				],)
			}
		} else {
			if (repoArray.size() == 1){
				localVal = "."
			} else {
				localVal = offsetPath
			}

			arrayList.push([
				credentialsId: 'Wfsjenkinsbuild', 
				depthOption: props['svnDepth'], 
				ignoreExternalsOption: true, 
				local: localVal, 
				remote: repoArray[i]+"/$BRANCH/"+offsetPath
			])
		}
	}

	arrayList
}

@NonCPS
// return hash of from svn info
def get_hash_from_svn_info(info) {
	def revision = [:]

	info = info.trim().tokenize("\n")
	info.each() { line ->
		line = line.split(": ")
		if (line.size() > 1) {
			revision[line[0]] = line[1].trim()
		}
 	}
 	revision
}

@NonCPS
def getCustomVersionStringHash(versionString) {
	def customStringHash = [:]
	def ari = versionString.tokenize(",")
	def tmp
	if (ari.size() > 1) {
		ari.each() { v ->
			tmp = v.tokenize("|")
			if (tmp.size() > 1) {
				tmp[0] = tmp[0].replaceAll("/", "-")
				customStringHash[tmp[0]] = tmp[1]
			}
		}
	} else {
		tmp = ari[0].tokenize("|")
		if (tmp.size() > 1) {
			tmp[0] = tmp[0].replaceAll("/", "-")
			customStringHash[tmp[0]] = tmp[1]
		}	else {
			customStringHash["default"] = ari[0]
		}
	}
	customStringHash
}

@NonCPS
//Method to trim property value
def trimSpaces(value) {
	return value.trim()
}

def get_version_name(branch, props, DEFAULT_BUILD_NUMBER) {
	if (branch == "na") {
		return DEFAULT_BUILD_NUMBER + "_" + props["appName"] + "_" + props['appComponentName']	
	} else {
		return branch + "_" + DEFAULT_BUILD_NUMBER + "_" + props["appName"] + "_" + props['appComponentName']	
	}
}

def get_ucd_component_name(props) {
	def tmp_name = "WBT_"+env.JOB_NAME
	if (props['ucdComponentName'] && props['ucdComponentName'].length() > 0) {
		echo "Setting UCD Component Name based on ucdComponentName property value"
		tmp_name = props['ucdComponentName']
	}	
	return tmp_name
}

def get_ucd_application_name(props) {
	def tmp_name = "WBT_" + props['appWamDistID'] + '-' + props['appName']
	if (props['ucdCDApplicationName'] && props['ucdCDApplicationName'].length() > 0) {
		tmp_name = props['ucdCDApplicationName']
	}	
	return tmp_name
}

def load_property_file(path) {
		def tmp_props = readProperties file: path
		tmp_props = trimProps(tmp_props)	
		return tmp_props
}

@NonCPS
def is_controlled_environment(environment_name) {
	def matcher = environment_name =~ /(?i)bcp|prod|dr/
  matcher ? true : false
}

def load_external_method(repo_url, file_path, checkout_dir, scm_branch) {
	checkout_build_pipeline_file(repo_url, file_path, checkout_dir, scm_branch)
	def tmp_file = checkout_dir + '/' + file_path
	def tmp_method = null
	if (fileExists(tmp_file)) {
	  println "Loading external file " + tmp_file 
		tmp_method = load(tmp_file)
	} else {
	  println "Info: External file not found..."
	}
	return tmp_method
}

def checkout_build_pipeline_file(repo_url, file_path, checkout_dir, scm_branch) {
	checkout changelog: false, 
		poll: false, 
		scm: [$class: 'GitSCM', 
			branches: [[name: "${scm_branch}"]], 
			doGenerateSubmoduleConfigurations: false, 
			extensions: [
				[$class: 'CleanBeforeCheckout'],
				[$class: 'RelativeTargetDirectory', relativeTargetDir: checkout_dir],
				[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: file_path]]]
			], 
			submoduleCfg: [], 
			userRemoteConfigs: [[credentialsId: 'Wfsjenkinsbuild', url: repo_url]]
		]
}

def should_load_external_method(props) {
	def tmp_bool = false
	if ( isTrue(props['externalPreBuildStep']) ||
			isTrue(props['externalPostBuildStep']) ||
			isTrue(props['externalPreFortifyScanStep']) ||
			isTrue(props['externalPreSonarScanStep']) ||
			isTrue(props['externalPreCheckoutStep'])) {
		tmp_bool = true
	}
	tmp_bool
}

def print_all_methods(obj){
  if (!obj){
		println("Object is null\r\n")
		return
  }
	if (!obj.metaClass && obj.getClass()){
    print_all_methods( obj.getClass())
		return
  }
	def str = "class ${obj.getClass().name} functions:\r\n"
	obj.metaClass.methods.name.unique().each { 
		str += it+"();"
	}
	println "${str}\r\n"
}

def post_build_step(external_method, props) {
	if (isTrue(props['externalPostBuildStep'])) {
		if (external_method) {
			println "Executing post build step..."
			try {
				external_method.post_build_step(props)
			} catch (NoSuchMethodError e) {
		    if (e.getMessage().contains("No such DSL method 'post_build_step' found among steps")) {
		    	external_method.post_build_step()
		    } else {
		    	throw e
		    }			
		  }
			println "Post build step completed"
		}
	}
}

def pre_build_step(external_method, props) {
	if (isTrue(props['externalPreBuildStep'])) {
		if (external_method) {
			println "Executing pre build step..."
			try {
				external_method.pre_build_step(props)
			} catch (NoSuchMethodError e) {
		    if (e.getMessage().contains("No such DSL method 'pre_build_step' found among steps")) {
		    	external_method.pre_build_step()
		    } else {
		    	throw e
		    }
			} 
			println "Pre build step completed"
		}
	}
}

def pre_checkout_step(external_method, props) {
	if (isTrue(props['externalPreCheckoutStep'])) {
		if (external_method) {
			println "Executing pre checkout step..."
			try {
				external_method.pre_checkout_step(props)
			} catch (NoSuchMethodError e) {
		    if (e.getMessage().contains("No such DSL method 'pre_checkout_step' found among steps")) {
		    	external_method.pre_checkout_step()
		    } else {
		    	throw e
		    }
			} 
			println "Pre Checkout step completed"
		}
	}
}

def is_snapshot(pom_file) {
  def pom = readMavenPom file: pom_file
  def matcher = pom.version =~ /(?i)snapshot/
  matcher ? true : false
}

// @NonCPS
// def get_jenkins_instance(url, instances) {
//   def instance_keys = instances.keySet() as String[]
//   def this_instance = null
//   def matcher
//   instance_keys.find { key ->
//     matcher = url =~ /(?i)${key}/
//     if (matcher) {
//       this_instance = instances[key]
//       return true
//     }
//     return false
//   }
//   return this_instance
// }

def set_gradle_environment_properties(gradle_props) {
	for(it2 in map_to_list(gradle_props)) {
		env.setProperty('ORG_GRADLE_PROJECT_'+it2[0], it2[1])
	}
}

@NonCPS 
def map_to_list(depmap) { 
	def dlist = [] 
	for (entry in depmap) { 
	   dlist.add([entry.key, entry.value]) 
	} 
	dlist 
} 

def get_list(files) {
	def tmp_list = []
	for (j=0; j < files.size(); j++) {
		tmp_list << [
	   	file: files[j].path,
	   	folder: files[j].path.tokenize("/")[0]
	  ]
	}
	return tmp_list
}

def get_dirs(list) {
	def tmp = []
	for (i=0; i < list.size(); i++) {
		if (!tmp.contains(list[i]['folder'])) {
			tmp << list[i]['folder']
		}
	}
	tmp
}

def validate_dir_exists(path) {
	if (fileExists(path)) {
		println "$path exists..."
	}	else {
		error "$path doesn't exists..."
	}
}

def getAt(property, default_value) {
	if (property && property.trim().length() > 0) {
		return property
	} else {
		return default_value
	}
}

def isTrue(property) {
	if (property && property.trim() == "true") {
		return true
	} else {
		return false
	}
}

def isSet(property) {
	if (property && property.trim().length() > 0) {
		return true
	} else {
		return false
	}
}

def shouldDisableArtifactDeployForBranches(props) {
	def tmp_bool = false
	def tmp_branches
	if (isSet(props['enableDeployArtifactsForBranches'])) {
		tmp_bool = true
		tmp_branches = props['enableDeployArtifactsForBranches'].tokenize(",")
		pp("Disabling Artifactory/uDeploy published for all branches EXCEPT for following: ${tmp_branches}")
		if (isMatchEnvBranch(tmp_branches)) {
			pp("Artifactory/uDeploy published ENABLED as ${env.BRANCH} matched ${tmp_branches}")
			tmp_bool = false
		}					
	}	
	if (isSet(props['disableDeployArtifactsForBranches'])) {
		tmp_branches = props['disableDeployArtifactsForBranches'].tokenize(",")
		pp("Disabling Artifactory/uDeploy published for following branches: ${tmp_branches}")
		if (isMatchEnvBranch(tmp_branches)) {
			pp("Artifactory/uDeploy published DISABLED as ${env.BRANCH} matched ${tmp_branches}")
			tmp_bool = true
		}					
	}	
	tmp_bool
}

def isMatchEnvBranch(branches) {
	def found
	def match = false
	for (i=0; i < branches.size(); i++) {
		found = env.BRANCH =~ /${branches[i]}/
		if (found.count > 0) {
			match = true
		}
	}
	return match
}

def pp(msg) {
	println "\u2756 ${msg} \u2756" 
}

def pp_e(msg) {
	error "\u2756 ${msg} \u2756"
}

@NonCPS
def set_git_sparse_checkout_paths(props) {
	def repoArray = []
	def arrayList = []

	repoArray = props['gitSparseCheckoutPath'].tokenize(",")
	
	for (i=0; i < repoArray.size(); i++) {
		arrayList.push([path: repoArray[i]])
	}
	return arrayList
}

def nuget_restore(props, NUGET_CONFIG_FILENAME) {
	if(isTrue(props['msbuildNugetDependencies'])) {
	  configFileProvider([configFile(fileId: NUGET_CONFIG_FILENAME, variable: 'jenkinsNugetConfig')])
    { 
    	println env.jenkinsNugetConfig
    	def nuget_home
    	if(isSet(props['nugetVersion'])) {
				nuget_home=tool name: props['nugetVersion']
    	} else {
   			nuget_home = env.NUGET_HOME
    	}
    	pp "NUGET_HOME: ${nuget_home}"
    	env.setProperty('PATH+NUGET', nuget_home)
    	if(isSet(props['msbuildNugetRestorePackagesDirectory'])) {	
				bat "nuget restore \"${props['msbuildSolutionFile']}\" -PackagesDirectory \"${props['msbuildNugetRestorePackagesDirectory']}\" -ConfigFile \"${env.jenkinsNugetConfig}\""
    	} else {
				bat "nuget restore \"${props['msbuildSolutionFile']}\" -ConfigFile \"${env.jenkinsNugetConfig}\""
    	}
    }							   				
	}	
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

def post_ahp(props, AHP_URL, modifiedBranch, DEFAULT_BUILD_NUMBER) {
	pp("Kicking off Anthilll Pro Workflow")
	def payload_name = appComponentNameToLowercase(props)
	def payload = "code=${props['ahpTriggerCode']}&&artifactory.build.name=$JOB_NAME"
	payload += "&&artifactory.component.path=$payload_name&&build.URL=$BUILD_URL"
	payload += "&&artifactory.build.number=$DEFAULT_BUILD_NUMBER&&branch.name=$modifiedBranch"
	httpRequest httpMode: 'POST', url: AHP_URL+'?'+payload
}


def is_snapshot_build(buildInfo) {
	def tmp_name 
	def tmp_is_build_snapshot = false
	pp "Checking deployable artifacts for SNAPSHOT"
	tmp_is_build_snapshot = any_artifactory_objects_snapshot(buildInfo['deployedArtifacts'])
	if (tmp_is_build_snapshot) {
		pp "SNAPSHOT jar detected: " + tmp_is_build_snapshot
		return true
	} else {
		pp "Checking dependencies artifacts for SNAPSHOT"
		tmp_is_build_snapshot = any_artifactory_objects_snapshot(buildInfo['buildDependencies'])
		if (tmp_is_build_snapshot) {
			pp "SNAPSHOT jar detected: " + tmp_is_build_snapshot
			return true
		} else {
			pp "Checking modules artifacts for SNAPSHOT"
			tmp_is_build_snapshot = is_artifactory_modules_snapshot(buildInfo['modules'])
			if (tmp_is_build_snapshot) {
				pp "SNAPSHOT jar detected: " + tmp_is_build_snapshot
				return true
			} else {
				pp "No SNAPSHOT dependencies found"
				return false		
			}
		}
	}
	tmp_is_build_snapshot
}

def is_artifactory_modules_snapshot(modules) {
	def tmp_snapshot = false
	if(modules) {
		for (i=0; i < modules.size(); i++) {
			if (modules[i]) {
				print_array_dump(modules[i]['artifacts'])
				tmp_snapshot = any_artifactory_objects_snapshot(modules[i]['artifacts'])
				if (tmp_snapshot) { break }
				print_array_dump(modules[i]['dependencies'])
				tmp_snapshot = any_artifactory_dependency_snapshot(modules[i]['dependencies'])
				if (tmp_snapshot) { break }
			}
		}
	}
	tmp_snapshot
}

@NonCPS
def any_artifactory_objects_snapshot(array_of_object) {
	def tmp_snapshot = false
	def tmp_name
	array_of_object.any { dependency ->
		tmp_name = dependency.getName() 
		if (matches_regex(tmp_name, /-SNAPSHOT\./)) {
			tmp_snapshot = tmp_name
			return true
		} else {
			return false
		}
	}
	tmp_snapshot
}

@NonCPS
def any_artifactory_dependency_snapshot(array_of_object) {
	def tmp_snapshot = false
	def tmp_name
	array_of_object.any { dependency ->
		tmp_name = dependency['id']
		if (matches_regex(tmp_name, /-SNAPSHOT$/)) {
			tmp_snapshot = tmp_name
			return true
		} else {
			return false
		}
	}
	tmp_snapshot
}

@NonCPS
def matches_regex(text, regex) {
  def matcher = text =~ regex
  matcher ? true : null
}

def print_array_dump(array) {
	if (array) {
		for (j=0; j < array.size(); j++) {
			println array[j].dump()
		}
	}
}
