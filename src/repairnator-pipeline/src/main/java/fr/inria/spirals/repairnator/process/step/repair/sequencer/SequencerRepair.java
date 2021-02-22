package fr.inria.spirals.repairnator.process.step.repair.sequencer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.*;
import fr.inria.coming.codefeatures.RepairnatorFeatures;
import fr.inria.spirals.repairnator.docker.DockerHelper;
import fr.inria.spirals.repairnator.config.SequencerConfig;
import fr.inria.spirals.repairnator.process.inspectors.GitRepositoryProjectInspector;
import fr.inria.spirals.repairnator.process.step.repair.sequencer.detection.ModificationPoint;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.RepairPatch;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.process.step.repair.AbstractRepairStep;
import fr.inria.spirals.repairnator.process.step.repair.sequencer.detection.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
* SequencerRepair is one builtin repair tool. It generates
* patches by invoking SequenceR docker image.
* 
* SequencerRepair is dependent on Astor as it collects info
* about suspicious modification locations from the latter
* to feed SequenceR.
* 
* SequenceR is one seq2seq model designed to predict source
* code change on line level. Check its paper for more info:
* https://arxiv.org/abs/1901.01808
*
 * @author Jian GU
 * @author Javier Ron
*/
public class SequencerRepair extends AbstractRepairStep {
    protected static final String TOOL_NAME = "SequencerRepair";
    private final SequencerConfig config;
    private final DockerClient docker;

    private DetectionStrategy detectionStrategy;

    public SequencerRepair(){
        this.config = SequencerConfig.getInstance();
        this.docker = DockerHelper.initDockerClient();
        this.detectionStrategy = new AstorDetectionStrategy();
    }

    public SequencerRepair(DetectionStrategy detectionStrategy){
        this.config = SequencerConfig.getInstance();
        this.docker = DockerHelper.initDockerClient();
        this.detectionStrategy = detectionStrategy;
    }

    @Override
    public String getRepairToolName() {
        return TOOL_NAME;
    }

    @Override
    protected StepStatus businessExecute() {
        this.getLogger().info("Start SequencerRepair");

        String pathPrefix = ""; // for macOS: "/private";
        JobStatus jobStatus = this.getInspector().getJobStatus();
        Path patchDir = Paths.get(pathPrefix + this.getInspector().getRepoLocalPath() +
                "/repairnator." + TOOL_NAME + ".results");
        try {
            Files.createDirectory(patchDir);
        } catch (IOException e) {
            addStepError("Got exception when running SequencerRepair: ", e);
        }

        // check ...
        List<URL> classPath = this.getInspector().getJobStatus().getRepairClassPath();
        File[] sources = this.getInspector().getJobStatus().getRepairSourceDir();
        if (classPath == null || sources == null) {
            return StepStatus.buildSkipped(this,"Classpath or Sources not computed.");
        }

        detectionStrategy.setup(getInspector(), getPom(), getLogger());
        List<ModificationPoint> suspiciousPoints = detectionStrategy.detect(this);

        if(suspiciousPoints.isEmpty()){
            return StepStatus.buildPatchNotFound(this);
        }

        /// pull Sequencer if image not present

        try {
            getLogger().info("DOCKER TAG: " + config.dockerTag);
            List<Image> images = docker.listImages(DockerClient.ListImagesParam.byName(config.dockerTag));
            if(images.size() <= 0) docker.pull(config.dockerTag);
        } catch (Exception e) {
            return StepStatus.buildSkipped(this,"Error while retrieving sequencer docker image: " + e);
        }

        final ExecutorService executor = Executors.newFixedThreadPool(config.threads);
        List<Future<SequencerResult>> allResults = new ArrayList<>();

        suspiciousPoints.forEach( smp -> allResults.add(executor.submit(() -> {
            try {
                int smpId = smp.hashCode();
                Path suspiciousFile = smp.getFilePath().toRealPath();
                Path buggyFilePath = suspiciousFile.toAbsolutePath();
                Path buggyParentPath = suspiciousFile.getParent();
                Path repoPath = Paths.get(getInspector().getRepoLocalPath()).toRealPath();
                Path relativePath = repoPath.relativize(suspiciousFile);
                int buggyLineNumber = smp.getSuspiciousLine();
                int beamSize = config.beamSize;
                String buggyFileName = suspiciousFile.getFileName().toString();
                Path outputDirPath = patchDir.toAbsolutePath().resolve(buggyFileName + smpId);
                if ( !Files.exists(outputDirPath) || !Files.isDirectory(outputDirPath)) {
                    Files.createDirectory(outputDirPath);
                    outputDirPath = outputDirPath.toRealPath();
                }

                String sequencerCommand = "./sequencer-predict.sh "
                                            + "--buggy_file=" + "/tmp/" + buggyFileName + " "
                                            + "--buggy_line=" + buggyLineNumber + " "
                                            + "--beam_size=" + beamSize + " "
                                            + "--real_file_path=" + relativePath + " "
                                            + "--output=" + "/out" + " "
                                            + "--models_dir=" + "/root/sequencer/models";

                HostConfig.Builder hostConfigBuilder = HostConfig.builder();

                /*
                 * note: the following code block provides a way to
                 * mount directories from one docker container
                 * into a sibling container.
                 *
                 * Otherwise, the docker daemon will try to mount from its own filesystem.
                 *
                 * This solution may be _too_ ad hoc.
                 */

                String parentPathStr = buggyParentPath.toString();
                String outputPathStr = outputDirPath.toString();

                if( Files.exists(Paths.get("/.dockerenv"))){
                    ProcessBuilder processBuilder = new ProcessBuilder();
                    processBuilder.command("bash", "-c", "basename `cat /proc/1/cpuset`");
                    Process proc = processBuilder.start();

                    StringWriter writer = new StringWriter();
                    IOUtils.copy(proc.getInputStream(), writer);
                    String containerId = writer.toString().trim();

                    ContainerInfo info = docker.inspectContainer(containerId);

                    String workspaceDir = Paths.get(getInspector().getWorkspace()).toRealPath().toString();

                    String mountPointSrt = info.mounts().stream()
                            .filter(item -> item.destination().equals(workspaceDir))
                            .findFirst().get().source();

                    parentPathStr = parentPathStr.replaceFirst(workspaceDir, mountPointSrt);
                    outputPathStr = outputPathStr.replaceFirst(workspaceDir, mountPointSrt);
                }

                hostConfigBuilder
                        .appendBinds(HostConfig.Bind
                                .from(parentPathStr)
                                .to("/tmp")
                                .build())
                        .appendBinds(HostConfig.Bind
                                .from(outputPathStr)
                                .to("/out")
                                .build());

                HostConfig hostConfig = hostConfigBuilder.build();

                ContainerConfig containerConfig = ContainerConfig.builder()
                        .image(config.dockerTag)
                        .hostConfig(hostConfig)
                        .cmd("bash", "-c", sequencerCommand)
                        .attachStdout(true)
                        .attachStderr(true)
                        .build();

                 ContainerCreation container = docker.createContainer(containerConfig);

                 docker.startContainer(container.id());
                 docker.waitContainer(container.id());

                 String stdOut = docker.logs(
                         container.id(),
                         DockerClient.LogsParam.stdout()
                 ).readFully();

                String stdErr = docker.logs(
                        container.id(),
                        DockerClient.LogsParam.stderr()
                ).readFully();

                docker.removeContainer(container.id());

                this.getLogger().info("stdOut: \n" + stdOut);
                this.getLogger().info("stdErr: \n" + stdErr);

                return new SequencerResult(buggyFilePath.toString(), outputDirPath.toString(),
                        stdOut, stdErr);

            } catch (Throwable throwable) {
                addStepError("Got exception when running SequencerRepair: ", throwable);
                return null;
            }
        })));

        List<SequencerResult> sequencerResults = new ArrayList<>();
        try {
            executor.shutdown();
            executor.awaitTermination(config.timeout, TimeUnit.MINUTES);
            for (Future<SequencerResult> result : allResults){
                sequencerResults.add(result.get());
            }
        } catch (Exception e) {
            addStepError("Got exception when running SequencerRepair: ", e);
        }

        /// prepare results
        List<RepairPatch> listPatches = new ArrayList<>();
        JsonArray toolDiagnostic = new JsonArray();

        listPatches = sequencerResults.stream().flatMap( result -> {
            JsonObject diagnostic = new JsonObject();

            diagnostic.addProperty("success", result.isSuccess());
            diagnostic.addProperty("message", result.getMessage());
            diagnostic.addProperty("warning", result.getWarning());
            toolDiagnostic.add(diagnostic);

            List<String> diffs = result.getDiffs();

            Stream<RepairPatch> patches = diffs.stream()
                .map(diff -> {
                    getLogger().info(diff);
                    return new RepairPatch(this.getRepairToolName(), result.getBuggyFilePath(), diff);
                })
                .filter(detectionStrategy::validate);

            return patches;

        }).collect(Collectors.toList());

        if(listPatches.isEmpty()){
            return StepStatus.buildPatchNotFound(this);
        }

        GitRepositoryProjectInspector gitInspector = (GitRepositoryProjectInspector)this.getInspector();

        List<RepairPatch> classifiedPatches = RepairPatch.classifyByODSWithFeatures(
                listPatches,
                (gitInspector.getRepoSlug() + "-" + gitInspector.getGitRepositoryIdCommit()).replace("/", "-")
        );

        classifiedPatches.forEach(patch -> {
            this.getLogger().debug("patch: " + patch.getFilePath() + " " + patch.getODSLabel() + "\n" );
        });

        this.getLogger().debug("patches passing before overfitting detection: " + classifiedPatches.size() + "\n" );

        List<RepairPatch> filteredPatches = classifiedPatches.stream()
                                                .filter(patch -> patch.getODSLabel().equals(RepairnatorFeatures.ODSLabel.CORRECT))
                                                .collect(Collectors.toList());

        this.getLogger().debug("patches marked as CORRECT by overfitting detection: " + filteredPatches.size() + "\n" );


        if(filteredPatches.isEmpty()){
            return StepStatus.buildPatchNotFound(this);
        }

        this.recordPatches(filteredPatches, MAX_PATCH_PER_TOOL);
        this.recordToolDiagnostic(toolDiagnostic);

        try {
            Files.walk(patchDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            addStepError("Got exception when running SequencerRepair: ", e);
        }

        jobStatus.setHasBeenPatched(true);

        return StepStatus.buildSuccess(this);

    }
}
