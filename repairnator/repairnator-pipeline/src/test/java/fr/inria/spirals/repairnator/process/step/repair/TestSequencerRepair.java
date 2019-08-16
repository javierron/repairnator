package fr.inria.spirals.repairnator.process.step.repair;

import ch.qos.logback.classic.Level;
import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.process.step.repair.sequencer.SequencerRepair;
import fr.inria.spirals.repairnator.utils.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.files.FileHelper;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.RepairPatch;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.process.step.CloneRepository;
import fr.inria.spirals.repairnator.process.step.TestProject;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutBuggyBuild;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldFail;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherTestInformation;
import fr.inria.spirals.repairnator.process.step.paths.ComputeClasspath;
import fr.inria.spirals.repairnator.process.step.paths.ComputeSourceDir;
import fr.inria.spirals.repairnator.process.utils4tests.Utils4Tests;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

// todo recheck
public class TestSequencerRepair {

    private File tmpDir;

    @Before
    public void setup() {
        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setZ3solverPath(Utils4Tests.getZ3SolverPath());
        Utils.setLoggersLevel(Level.ERROR);
    }

    @After
    public void tearDown() throws IOException {
        RepairnatorConfig.deleteInstance();
        FileHelper.deleteFile(tmpDir);
    }

    /* main issue
    ModuleNotFoundError: No module named 'torch' (running OpenNMT-py)
     */
    @Test
    public void testSequencerRepair() throws IOException {
        // 207890790 susp.size() == 34
//        long buildId = 207890790; // surli/failingProject build
        // 252712792 susp.size() == 3 all fail
//        long buildId = 252712792; // surli/failingProject build
        // 376820338 susp.size() == 6 all fail except the last one
        long buildId = 376820338; // surli/failingProject build
        Build build = this.checkBuildAndReturn(buildId, false);

        tmpDir = Files.createTempDirectory("test_sequencer_repair").toFile();
        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");
        ProjectInspector inspector = new ProjectInspector(toBeInspected, tmpDir.getAbsolutePath(), null, null);

        CloneRepository cloneStep = new CloneRepository(inspector);
        SequencerRepair sequencerRepair = new SequencerRepair();
        sequencerRepair.setProjectInspector(inspector);

        RepairnatorConfig.getInstance().setRepairTools(Collections.singleton(sequencerRepair.getRepairToolName()));

        cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
            .addNextStep(new TestProject(inspector))
            .addNextStep(new GatherTestInformation(inspector, true, new BuildShouldFail(), false))
            .addNextStep(new ComputeClasspath(inspector, true))
            .addNextStep(new ComputeSourceDir(inspector, true, false))
            .addNextStep(sequencerRepair);
        cloneStep.execute();

        assertThat(sequencerRepair.isShouldStop(), is(false));

        List<StepStatus> stepStatusList = inspector.getJobStatus().getStepStatuses();
        assertThat(stepStatusList.size(), is(7));
        assertThat(stepStatusList.get(6).getStep(), is(sequencerRepair));

        for (StepStatus stepStatus : stepStatusList.subList(0, 6)) {
            assertThat(stepStatus.isSuccess(), is(true));
        }
        System.out.println(stepStatusList.get(6).getStatus());
        System.out.println(stepStatusList.get(6).getDiagnostic());
        assertThat(stepStatusList.get(6).getStatus(), is(StepStatus.StatusKind.SKIPPED));
//        assertThat(stepStatusList.get(6).getStatus(), is(StepStatus.StatusKind.SUCCESS));
//        assertThat(stepStatusList.get(6).isSuccess(), is(true));

//        String finalStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
//        assertThat(finalStatus, is("PATCHED"));

        List<RepairPatch> allPatches = inspector.getJobStatus().getAllPatches();
        assertThat(allPatches.size(), is(16));
        assertThat(inspector.getJobStatus().getToolDiagnostic().get(sequencerRepair.getRepairToolName()), notNullValue());
    }

    private Build checkBuildAndReturn(long buildId, boolean isPR) {
        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());

        Build build = optionalBuild.get();
        assertThat(build, IsNull.notNullValue());
        assertThat(buildId, Is.is(build.getId()));
        assertThat(build.isPullRequest(), Is.is(isPR));

        return build;
    }
}