package fr.inria.spirals.repairnator.config;

/**
 * Configuration manager for Sequencer repair step:
 * Config values are read directly as environment variables,
 * so that we avoid adding more options through the launcher's
 * command line args.
 */
public final class SequencerConfig {

    public enum RAW_URL_SOURCE {
        RAW_GITHUB
    }

    public final String dockerTag;
    public final int threads;
    public final int beamSize;
    public final int timeout;
    public final String collectorPath;
    public final int contextSize;
    public final RAW_URL_SOURCE rawURLSource;
    public final String ODSPath;

    private static SequencerConfig instance;

    private SequencerConfig(){
        this.dockerTag = getEnvOrDefault("SEQUENCER_DOCKER_TAG", "javierron/sequencer-multimodel:1.0");
        this.threads = Integer.parseInt(getEnvOrDefault("SEQUENCER_THREADS", "4"));
        this.beamSize = Integer.parseInt(getEnvOrDefault("SEQUENCER_BEAM_SIZE", "50"));
        this.timeout = Integer.parseInt(getEnvOrDefault("SEQUENCER_TIMEOUT", "120"));
        this.collectorPath = getEnvOrDefault("SEQUENCER_COLLECTOR_PATH",
                System.getProperty("user.home") + "/continuous-learning-data");
        this.contextSize = Integer.parseInt(getEnvOrDefault("SEQUENCER_CONTEXT_SIZE", "3"));
        this.ODSPath = (getEnvOrDefault("SEQUENCER_ODS_PATH", "/ODSPatches"));
        this.rawURLSource = parseRawURLSource();

    }

    private String getEnvOrDefault(String name, String dfault){

        String env = System.getenv(name);
        if(env == null || env.equals(""))
            return dfault;

        return env;
    }

    private RAW_URL_SOURCE parseRawURLSource(){
        /* here switch statement if another source is implemented

           String value = getEnvOrDefault("SEQUENCER_RAW_URL_SOURCE", "raw_github");
           switch(value){
             case "raw_github":
                return RAW_URL_SOURCE.RAW_GITHUB;
             case "mirror_123":
                .......
           }

        */

        return RAW_URL_SOURCE.RAW_GITHUB;
    }

    public static SequencerConfig getInstance(){
        if (instance == null)
            instance = new SequencerConfig();

        return instance;
    }


}
