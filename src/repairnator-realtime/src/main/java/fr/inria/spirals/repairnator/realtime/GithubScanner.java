package fr.inria.spirals.repairnator.realtime;

import fr.inria.spirals.repairnator.GithubInputBuild;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.config.SequencerConfig;
import fr.inria.spirals.repairnator.realtime.githubapi.commits.GithubAPICommitAdapter;
import fr.inria.spirals.repairnator.realtime.githubapi.commits.models.SelectedCommit;
import fr.inria.spirals.repairnator.states.LauncherMode;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GithubScanner {
    static long scanIntervalDelay = 60 * 60 * 1000; // 1 hour
    static long scanIntervalLength = 60 * 60 * 1000; // 1 hour
    static long frequency = 60 * 60 * 1000; // 1 hour

    DockerPipelineRunner runner;

    FetchMode fetchMode;
    Set<String> repos;

    public static void main(String[] args) throws IOException {
        Set<String> repos = null;
        String reposPath = System.getenv("REPOS_PATH");
        if (reposPath != null)
            repos = new HashSet<String>(FileUtils.readLines(new File(reposPath), "UTF-8"));

        FetchMode fetchMode = FetchMode.ALL; // parseFetchMode();

        //RepairnatorConfig.getInstance().setDockerImageName("repairnator/pipeline:sorald");
        GithubScanner scanner = new GithubScanner(fetchMode, repos);
        scanner.setup();

        while (true) {
            try {
                List<SelectedCommit> selectedCommits = scanner.fetch();
//                List<SelectedCommit> selectedCommits = scanner.fetch(
//                        new SimpleDateFormat("dd/MM/yyyy").parse("05/01/2021").getTime(),
//                        new SimpleDateFormat("dd/MM/yyyy").parse("08/01/2021").getTime()
//                );

                selectedCommits.forEach(scanner::process);

                TimeUnit.MILLISECONDS.sleep(frequency);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public GithubScanner(FetchMode fetchMode, Set<String> repos) {
        this.fetchMode = fetchMode;
        this.repos = repos;

        runner = new DockerPipelineRunner();
        runner.initRunner();
    }

    public GithubScanner(FetchMode fetchMode) {
        this(fetchMode, null);
    }

    public List<SelectedCommit> fetch() throws Exception {
        long endTime = System.currentTimeMillis() - scanIntervalDelay;
        long startTime = endTime - scanIntervalLength;

        return fetch(startTime, endTime);
    }

    public void setup(){
        Set<String> repairTools = new HashSet();
        repairTools.add(getEnvOrDefault("REPAIR_TOOL", "SequencerRepair"));
        RepairnatorConfig.getInstance().setRepairTools(repairTools);
        RepairnatorConfig.getInstance().setNbThreads(16);
        RepairnatorConfig.getInstance().setPipelineMode(RepairnatorConfig.PIPELINE_MODE.DOCKER.name());
        RepairnatorConfig.getInstance().setGithubToken(System.getenv("GITHUB_OAUTH"));
        RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.SEQUENCER_REPAIR);

        RepairnatorConfig.getInstance().setDockerImageName("javierron/pipeline:3.0");
//        RepairnatorConfig.getInstance().setSoraldSkipPR(true);
//        RepairnatorConfig.getInstance().setSonarRules(new String[] {"2204"});//, "2111", "2142", "2272", "2167", "2116"});
//        RepairnatorConfig.getInstance().setSoraldSegmentSize(200);
//        RepairnatorConfig.getInstance().setSoraldMaxFixesPerRule(2000);

    }

    public List<SelectedCommit> fetch(long startTime, long endTime) throws Exception {
        return GithubAPICommitAdapter.getInstance().getSelectedCommits(startTime, endTime, fetchMode, repos);
    }

    public void process(SelectedCommit commit) {
        String url = "https://github.com/" + commit.getRepoName();
        String sha = commit.getCommitId();

        System.out.println(url);
        System.out.println(sha);

        runner.submitBuild(new GithubInputBuild(url, sha));
    }

    private static String getEnvOrDefault(String name, String dfault) {
        String env = System.getenv(name);
        if (env == null || env.equals(""))
            return dfault;

        return env;
    }

    private static FetchMode parseFetchMode() {
        String value = getEnvOrDefault("FETCH_MODE", "failed");
        switch (value) {
            case "all":
                return FetchMode.ALL;
            case "failed":
            default:
                return FetchMode.FAILED;
        }
    }

    public enum FetchMode {
        FAILED, ALL;
    }
}
