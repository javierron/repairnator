package fr.inria.spirals.repairnator.pipeline;

import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.states.LauncherMode;

import java.util.List;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;

import fr.inria.spirals.repairnator.process.inspectors.InspectorFactory;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.GitRepositoryProjectInspector;
import fr.inria.spirals.repairnator.process.step.repair.Sorald;
import fr.inria.spirals.repairnator.serializer.HardwareInfoSerializer;

import fr.inria.spirals.repairnator.serializer.InspectorSerializer;
import fr.inria.spirals.repairnator.serializer.InspectorSerializer4Bears;
import fr.inria.spirals.repairnator.serializer.InspectorTimeSerializer;
import fr.inria.spirals.repairnator.serializer.PullRequestSerializer;
import fr.inria.spirals.repairnator.serializer.PropertiesSerializer;
import fr.inria.spirals.repairnator.serializer.PipelineErrorSerializer;
import fr.inria.spirals.repairnator.serializer.PatchesSerializer;
import fr.inria.spirals.repairnator.serializer.ToolDiagnosticSerializer;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;

import fr.inria.spirals.repairnator.serializer.InspectorSerializer4GitRepository;
import fr.inria.spirals.repairnator.serializer.InspectorTimeSerializer4GitRepository;
import fr.inria.spirals.repairnator.serializer.PatchesSerializer4GitRepository;
import fr.inria.spirals.repairnator.serializer.PipelineErrorSerializer4GitRepository;
import fr.inria.spirals.repairnator.serializer.PropertiesSerializer4GitRepository;
import fr.inria.spirals.repairnator.serializer.ToolDiagnosticSerializer4GitRepository;
import fr.inria.spirals.repairnator.serializer.PullRequestSerializer4GitRepository;

import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.notifier.AbstractNotifier;

import fr.inria.spirals.repairnator.pipeline.travis.TravisMainProcess;
import fr.inria.spirals.repairnator.pipeline.travis.TravisDefineJSAPArgs;
import fr.inria.spirals.repairnator.pipeline.travis.TravisInitNotifiers;
import fr.inria.spirals.repairnator.pipeline.travis.TravisInitSerializerEngines;
import fr.inria.spirals.repairnator.pipeline.travis.TravisInitConfig;

import fr.inria.spirals.repairnator.pipeline.github.GithubMainProcess;
import fr.inria.spirals.repairnator.pipeline.github.GithubDefineJSAPArgs;
import fr.inria.spirals.repairnator.pipeline.github.GithubInitNotifiers;
import fr.inria.spirals.repairnator.pipeline.github.GithubInitSerializerEngines;
import fr.inria.spirals.repairnator.pipeline.github.GithubInitConfig;

import fr.inria.spirals.repairnator.pipeline.listener.PipelineBuildListenerMainProcess;

/* This will manufacture different kind of repairnator type */
public class MainProcessFactory {

	public static RepairnatorConfig getConfig() {
		return RepairnatorConfig.getInstance();
	}

	/* Standard repair with Travis build id */
	public static MainProcess getTravisMainProcess(String[] inputArgs) {

		TravisDefineJSAPArgs travisDefineJSAPArgs = new TravisDefineJSAPArgs();
		TravisInitConfig travisInitConfig = new TravisInitConfig();
		TravisInitNotifiers travisInitNotifiers = new TravisInitNotifiers();
		TravisInitSerializerEngines travisInitSerializerEngines = new TravisInitSerializerEngines();
		
		JSAP jsap;
		try {
			jsap = travisDefineJSAPArgs.defineArgs();
		} catch (JSAPException e) {
			throw new RuntimeException("Failed to parse JSAP");
		}
		travisInitConfig.initConfigWithJSAP(jsap,inputArgs);
		travisInitSerializerEngines.initSerializerEngines();
		travisInitNotifiers.initNotifiers();

		TravisMainProcess travisMainProcess = new TravisMainProcess(travisDefineJSAPArgs,
																		travisInitConfig,
																		travisInitSerializerEngines,
																			travisInitNotifiers);

		serializeHardwareInfoSerializer(travisInitSerializerEngines.getEngines());

		ProjectInspector inspector = constructInspector4Default(travisMainProcess.getBuildToBeInspected(),travisInitSerializerEngines.getEngines(),travisInitNotifiers.getNotifiers());

		travisMainProcess = travisMainProcess.setInspector(inspector)
												.setEngines(travisInitSerializerEngines.getEngines())
												.setNotifiers(travisInitNotifiers.getNotifiers());

		return travisMainProcess;
	}

	/* Repair with git Url instead of travis */
	public static MainProcess getGithubMainProcess(String[] inputArgs) {

		GithubDefineJSAPArgs githubDefineJSAPArgs = new GithubDefineJSAPArgs();
		GithubInitConfig githubInitConfig = new GithubInitConfig();
		GithubInitNotifiers githubInitNotifiers = new GithubInitNotifiers();
		GithubInitSerializerEngines githubInitSerializerEngines = new GithubInitSerializerEngines();

		JSAP jsap;
		try {
			jsap = githubDefineJSAPArgs.defineArgs();
		} catch (JSAPException e) {
			throw new RuntimeException("Failed to parse JSAP");
		}

		githubInitConfig.initConfigWithJSAP(jsap,inputArgs);
		githubInitSerializerEngines.initSerializerEngines();
		githubInitNotifiers.initNotifiers();

		GithubMainProcess githubMainProcess = new GithubMainProcess(githubDefineJSAPArgs,
																	githubInitConfig,
																	githubInitSerializerEngines,
																	githubInitNotifiers);

		serializeHardwareInfoSerializer(githubInitSerializerEngines.getEngines());

		ProjectInspector inspector;

		switch (getConfig().getLauncherMode()){
			case SEQUENCER_REPAIR:
				inspector = constructInspector4SequencerRepair(githubInitSerializerEngines.getEngines(),githubInitNotifiers.getNotifiers());
				break;
			case SORALD:
				inspector = constructInspector4Sorald(githubInitSerializerEngines.getEngines(),githubInitNotifiers.getNotifiers());
				break;
			default:
				inspector = constructInspector4Github(githubInitSerializerEngines.getEngines(),githubInitNotifiers.getNotifiers());
				break;
		}

		githubMainProcess = githubMainProcess.setInspector(inspector)
												.setNotifiers(githubInitNotifiers.getNotifiers())
												.setEngines(githubInitSerializerEngines.getEngines());

		return githubMainProcess;
	}

	public static MainProcess getSoraldMainProcess(String[] inputArgs) {

		GithubDefineJSAPArgs githubDefineJSAPArgs = new GithubDefineJSAPArgs();
		GithubInitConfig githubInitConfig = new GithubInitConfig();
		GithubInitNotifiers githubInitNotifiers = new GithubInitNotifiers();
		GithubInitSerializerEngines githubInitSerializerEngines = new GithubInitSerializerEngines();

		JSAP jsap;
		try {
			jsap = githubDefineJSAPArgs.defineArgs();
		} catch (JSAPException e) {
			throw new RuntimeException("Failed to parse JSAP");
		}
		githubInitConfig.initConfigWithJSAP(jsap,inputArgs);
		githubInitSerializerEngines.initSerializerEngines();
		githubInitNotifiers.initNotifiers();

		GithubMainProcess githubMainProcess = new GithubMainProcess(githubDefineJSAPArgs,
				githubInitConfig,
				githubInitSerializerEngines,
				githubInitNotifiers);

		serializeHardwareInfoSerializer(githubInitSerializerEngines.getEngines());

		ProjectInspector inspector =  constructInspector4Github(githubInitSerializerEngines.getEngines(),githubInitNotifiers.getNotifiers());

		githubMainProcess = githubMainProcess.setInspector(inspector)
				.setNotifiers(githubInitNotifiers.getNotifiers())
				.setEngines(githubInitSerializerEngines.getEngines());

		return githubMainProcess;
	}

	public static MainProcess getPipelineListenerMainProcess(String[] inputArgs) {
		MainProcess travisMainProcess = getTravisMainProcess(inputArgs);
		return new PipelineBuildListenerMainProcess(travisMainProcess);
	}

	private static void serializeHardwareInfoSerializer(List<SerializerEngine> engines) {
		HardwareInfoSerializer hardwareInfoSerializer = new HardwareInfoSerializer(engines, getConfig().getRunId(), getConfig().getBuildId()+"");
        hardwareInfoSerializer.serialize();
	}

	/* These methods below should be called after all other inits */
	/* move serializer into project inspector and get to construct */
	private static GitRepositoryProjectInspector constructInspector4Github(List<SerializerEngine> engines,List<AbstractNotifier> notifiers) {
		boolean shouldStaticAnalysis = getConfig().getRepairTools().contains(Sorald.TOOL_NAME) && getConfig().getRepairTools().size() == 1;

		System.out.println("Gitbranch " + getConfig().getGitRepositoryBranch());
		GitRepositoryProjectInspector inspector = (GitRepositoryProjectInspector) InspectorFactory.getGithubInspector(
                getConfig().getGitRepositoryUrl(),
                getConfig().getGitRepositoryBranch(),
                getConfig().getGitRepositoryIdCommit(),
                getConfig().isGitRepositoryFirstCommit(),
                getConfig().getWorkspacePath(),
                notifiers
            );

		inspector.setSkipPreSteps(shouldStaticAnalysis);
        inspector.getSerializers().add(new InspectorSerializer4GitRepository(engines, inspector));
        inspector.getSerializers().add(new PropertiesSerializer4GitRepository(engines, inspector));
        inspector.getSerializers().add(new InspectorTimeSerializer4GitRepository(engines, inspector));
        inspector.getSerializers().add(new PipelineErrorSerializer4GitRepository(engines, inspector));
        inspector.getSerializers().add(new PatchesSerializer4GitRepository(engines, inspector));
        inspector.getSerializers().add(new ToolDiagnosticSerializer4GitRepository(engines, inspector));
        inspector.getSerializers().add(new PullRequestSerializer4GitRepository(engines, inspector));

        return inspector;
	}
	private static GitRepositoryProjectInspector constructInspector4SequencerRepair(List<SerializerEngine> engines,List<AbstractNotifier> notifiers) {

		System.out.println("Gitbranch " + getConfig().getGitRepositoryBranch());
		GitRepositoryProjectInspector inspector = (GitRepositoryProjectInspector) InspectorFactory.getGitSequencerRepairInspector(
				getConfig().getGitRepositoryUrl(),
				getConfig().getGitRepositoryBranch(),
				getConfig().getGitRepositoryIdCommit(),
				getConfig().isGitRepositoryFirstCommit(),
				getConfig().getWorkspacePath(),
				notifiers
		);

		inspector.getSerializers().add(new InspectorSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PropertiesSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new InspectorTimeSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PipelineErrorSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PatchesSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new ToolDiagnosticSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PullRequestSerializer4GitRepository(engines, inspector));

		return inspector;
	}

	private static GitRepositoryProjectInspector constructInspector4Sorald(List<SerializerEngine> engines,List<AbstractNotifier> notifiers) {
		boolean shouldStaticAnalysis = getConfig().getRepairTools().contains(Sorald.TOOL_NAME) && getConfig().getRepairTools().size() == 1;

		System.out.println("Gitbranch " + getConfig().getGitRepositoryBranch());
		GitRepositoryProjectInspector inspector = (GitRepositoryProjectInspector) InspectorFactory.getGithubInspector(
				getConfig().getGitRepositoryUrl(),
				getConfig().getGitRepositoryBranch(),
				getConfig().getGitRepositoryIdCommit(),
				getConfig().isGitRepositoryFirstCommit(),
				getConfig().getWorkspacePath(),
				notifiers
		);

		inspector.setSkipPreSteps(shouldStaticAnalysis);
		inspector.getSerializers().add(new InspectorSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PropertiesSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new InspectorTimeSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PipelineErrorSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PatchesSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new ToolDiagnosticSerializer4GitRepository(engines, inspector));
		inspector.getSerializers().add(new PullRequestSerializer4GitRepository(engines, inspector));

		return inspector;
	}

	private static ProjectInspector constructInspector4Default(BuildToBeInspected buildToBeInspected, List<SerializerEngine> engines, List<AbstractNotifier> notifiers) {
		ProjectInspector inspector;

		boolean shouldStaticAnalysis = getConfig().getRepairTools().contains(Sorald.TOOL_NAME) && getConfig().getRepairTools().size() == 1;

		LauncherMode launcherMode = getConfig().getLauncherMode();
		String workspacePath = getConfig().getWorkspacePath();

		switch (launcherMode){
			case BEARS:
				inspector = InspectorFactory.getBearsInspector(buildToBeInspected, workspacePath, notifiers);
				break;
			case CHECKSTYLE:
				inspector = InspectorFactory.getCheckStyleInspector(buildToBeInspected, workspacePath, notifiers);
				break;
			case SEQUENCER_REPAIR:
				inspector = InspectorFactory.getSequencerRepairInspector(buildToBeInspected, workspacePath, notifiers);
				break;
			default:
				inspector = InspectorFactory.getTravisInspector(buildToBeInspected, workspacePath, notifiers);
				break;
		}

		if (launcherMode == LauncherMode.BEARS) {
            inspector.getSerializers().add(new InspectorSerializer4Bears(engines, inspector));
        } else {
            inspector.getSerializers().add(new InspectorSerializer(engines, inspector));
        }

        inspector.setSkipPreSteps(shouldStaticAnalysis);
        inspector.getSerializers().add(new PropertiesSerializer(engines, inspector));
        inspector.getSerializers().add(new InspectorTimeSerializer(engines, inspector));
        inspector.getSerializers().add(new PipelineErrorSerializer(engines, inspector));
        inspector.getSerializers().add(new PatchesSerializer(engines, inspector));
        inspector.getSerializers().add(new ToolDiagnosticSerializer(engines, inspector));
        inspector.getSerializers().add(new PullRequestSerializer(engines, inspector));

        return inspector;
	}
}