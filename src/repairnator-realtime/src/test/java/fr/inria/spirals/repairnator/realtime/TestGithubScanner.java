package fr.inria.spirals.repairnator.realtime;

import fr.inria.spirals.repairnator.InputBuild;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.realtime.githubapi.commits.models.SelectedCommit;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import static junit.framework.TestCase.*;
import static org.mockito.Mockito.*;

import org.mockito.internal.util.reflection.Whitebox;

import java.io.File;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestGithubScanner {


    @Test
    public void testBuildSubmission() throws InterruptedException {
        //DockerPipelineRunner runner = mock(DockerPipelineRunner.class);


        RepairnatorConfig.getInstance().setDockerImageName("javierron/pipeline:3.0");
        GithubScanner scanner = new GithubScanner(GithubScanner.FetchMode.FAILED);
        //Whitebox.setInternalState(scanner, "runner", runner);

        boolean isGithubActionsFailed = true;
        String commitId = "e182ccb9ef41b5adab602ed12bfc71b744ff0241";
        String repoName = "javierron/failingProject";
        SelectedCommit commit = new SelectedCommit(isGithubActionsFailed, commitId, repoName);

        scanner.setup();
        scanner.process(commit);

        Thread.sleep(1000 * 60 * 60 * 3);
        //verify(runner, times(1)).submitBuild(any(InputBuild.class));
    }

    @Test
    public void testFetchingAll() throws Exception {

        Set<String> repos = new HashSet<String>(FileUtils.readLines(new File(getClass()
                        .getResource("/GithubScannerTest_repos.txt").getFile()), "UTF-8"));
        GithubScanner scanner = new GithubScanner(GithubScanner.FetchMode.ALL, repos);
        scanner.setup();

        List<SelectedCommit> commits =
                scanner.fetch(new SimpleDateFormat("dd/MM/yyyy").parse("01/12/2020").getTime(),
                        new SimpleDateFormat("dd/MM/yyyy").parse("27/01/2021").getTime());
        assertTrue(commits.stream().anyMatch(x -> x.getCommitId().equals("290bdc01884f7c9bf2140a7c66d22ca72fe50fbb")));
    }

    @Test
    public void testFetchingFailed() throws Exception {

        Set<String> repos = new HashSet<String>(FileUtils.readLines(new File(getClass()
                .getResource("/GithubScannerTest_repos.txt").getFile()), "UTF-8"));
        GithubScanner scanner = new GithubScanner(GithubScanner.FetchMode.FAILED, repos);
        scanner.setup();

        List<SelectedCommit> commits =
                scanner.fetch(new SimpleDateFormat("dd/MM/yyyy").parse("01/12/2020").getTime(),
                        new SimpleDateFormat("dd/MM/yyyy").parse("27/01/2021").getTime());
        assertFalse(commits.stream().anyMatch(x -> x.getCommitId().equals("290bdc01884f7c9bf2140a7c66d22ca72fe50fbb")));
        assertTrue(commits.stream().anyMatch(x -> x.getCommitId().equals("e3456813fdcca1ed5075c0fb72b0bcbc9524e791")));
    }
}
