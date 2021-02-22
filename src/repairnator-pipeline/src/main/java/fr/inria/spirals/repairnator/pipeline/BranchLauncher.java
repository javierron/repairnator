package fr.inria.spirals.repairnator.pipeline;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;

import com.martiansoftware.jsap.stringparsers.EnumeratedStringParser;
import fr.inria.spirals.repairnator.LauncherUtils;
import fr.inria.spirals.repairnator.TravisLauncherUtils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.step.repair.NPERepair;
import fr.inria.spirals.repairnator.states.LauncherMode;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.notifier.PatchNotifier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BranchLauncher implements LauncherAPI{
	private static Logger LOGGER = LoggerFactory.getLogger(BranchLauncher.class);
	private String[] args;

	public BranchLauncher(String[] args) {
		this.args = args;
	}


	/* This should be only those args neccessary to construct the correct launcher */
	public static JSAP defineBasicArgs() throws JSAPException {
		JSAP jsap = new JSAP();

		FlaggedOption opt2 = new FlaggedOption("launcherMode");
        opt2.setShortFlag('l');
        opt2.setLongFlag("launcherMode");
        opt2.setStringParser(JSAP.STRING_PARSER);
        opt2.setDefault(LauncherMode.REPAIR.name());
        opt2.setHelp("specify launcherMode." 
        	+ "REPAIR: standard repairnator repair with Travis build ids. BEARS: analyze pairs of bugs and human-produced patches. "
        	+ "CHECKSTYLE: analyze build failing because of checkstyle. "
        	+ "GIT_REPOSITORY: repairnator repair with Git instead of standard Travis. "
        	+ "KUBERNETES_LISTENER: run repairnator as a Activemq server listening for Travis build ids. ");
        jsap.registerParameter(opt2);

        return jsap;
	}

	public static MainProcess getMainProcess(String[] args) throws JSAPException{
		JSAP jsap = defineBasicArgs();
		JSAPResult jsapResult = jsap.parse(args);

		String launcherMode = jsapResult.getString("launcherMode");

		if (launcherMode.equals(LauncherMode.REPAIR.name())) {

			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.REPAIR);

			return MainProcessFactory.getTravisMainProcess(args);

		} else if (launcherMode.equals(LauncherMode.BEARS.name())) {

			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.BEARS);

			return MainProcessFactory.getTravisMainProcess(args);

		} else if (launcherMode.equals(LauncherMode.CHECKSTYLE.name())) {

			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.CHECKSTYLE);

			return MainProcessFactory.getTravisMainProcess(args);

		} else if (launcherMode.equals(LauncherMode.GIT_REPOSITORY.name())) {

			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.GIT_REPOSITORY);

			return MainProcessFactory.getGithubMainProcess(args);

		} else if (launcherMode.equals(LauncherMode.SEQUENCER_REPAIR.name())) {

			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.SEQUENCER_REPAIR);

			return MainProcessFactory.getGithubMainProcess(args);

		}
		else if (launcherMode.equals(LauncherMode.SORALD.name())) {

			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.SORALD);

			return MainProcessFactory.getGithubMainProcess(args);

		} else if (launcherMode.equals(LauncherMode.KUBERNETES_LISTENER.name())) {
			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.KUBERNETES_LISTENER);
			return MainProcessFactory.getPipelineListenerMainProcess(args);
		} else {
			LOGGER.warn("Unknown launcher mode: " + launcherMode + ". Please choose the following: REPAIR, BEARS, CHECKSTYLE, GIT_REPOSITORY, KUBERNETES_LISTENER, JENKINS_PLUGIN");
			return null;
		}
	}


	public static void main(String[] args) throws JSAPException {
//		JSAP jsap = defineBasicArgs();
//		JSAPResult jsapResult = jsap.parse(args);
		
		MainProcess mainProcess = getMainProcess(args);
		mainProcess.run();
	}

	@Override
	public void launch() {
		try {
			main(this.args);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public RepairnatorConfig getConfig() {
        return RepairnatorConfig.getInstance();
    }

    @Override
    public JSAP defineArgs() {
    	return null;
    }

    @Override
    public boolean mainProcess() {
		try {
			main(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return true;
    }

    @Override
    public ProjectInspector getInspector() {
        return null;
    }

    @Override
    public void setPatchNotifier(PatchNotifier patchNotifier) {
    }
}