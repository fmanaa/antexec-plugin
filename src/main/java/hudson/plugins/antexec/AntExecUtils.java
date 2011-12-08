package hudson.plugins.antexec;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.*;

import java.io.IOException;
import java.io.PrintStream;

public class AntExecUtils {

    public static FilePath getAntHome(AbstractBuild build, PrintStream logger, EnvVars env, String antExe, String antHome, Boolean verbose) throws IOException, InterruptedException {
        String envAntHome = env.get("ANT_HOME");
        String useAntHome = null;

        //Setup ANT_HOME from Environment or job configuration screen
        if (envAntHome != null && envAntHome.length() > 0 && !envAntHome.equals("")) {
            useAntHome = envAntHome;
            if (verbose != null && verbose)
                logger.println(Messages.AntExec_AntHomeEnvVarFound(useAntHome));
        } else {
            if (verbose != null && verbose) logger.println(Messages.AntExec_AntHomeEnvVarNotFound());
        }

        //Forcing configured ANT_HOME in Environment
        if (antHome != null && antHome.length() > 0 && !antHome.equals("")) {
            if (useAntHome != null) {
                logger.println(Messages._AntExec_AntHomeReplacing(useAntHome, antHome));
            } else {
                logger.println(Messages._AntExec_AntHomeReplacing("", antHome));
                if (build.getBuiltOn().createPath(antHome).exists()) {
                    logger.println(build.getBuiltOn().createPath(antHome) + " exists!");
                }
            }
            useAntHome = antHome;
            env.put("ANT_HOME", useAntHome);
            logger.println(Messages.AntExec_EnvironmentChanged("ANT_HOME", useAntHome));
        }

        if (useAntHome == null) {
            logger.println(Messages.AntExec_AntHomeValidation());
            logger.println("Trying to run ant from PATH ...");
            return build.getBuiltOn().createPath("ant");
        } else {
            if (build.getBuiltOn().createPath(useAntHome + antExe).exists()) {
                logger.println(build.getBuiltOn().createPath(useAntHome + antExe) + " exists!");
            }
            return build.getBuiltOn().createPath(useAntHome + antExe);
        }
    }


    static FilePath makeBuildFile(String targetSource, AbstractBuild build) throws IOException, InterruptedException {
        FilePath buildFile = new FilePath(build.getWorkspace(), AntExec.buildXml);

        StringBuilder sb = new StringBuilder();
        sb.append("<project default=\"AntExec_Builder\" xmlns:antcontrib=\"antlib:net.sf.antcontrib\" basedir=\".\">\n");
        sb.append("<target name=\"AntExec_Builder\">\n\n");
        sb.append(targetSource);
        sb.append("\n</target>\n");
        sb.append("</project>\n");

        buildFile.write(sb.toString(), null);
        return buildFile;
    }
}
