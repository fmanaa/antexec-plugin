/*
 * The MIT License
 *
 * Copyright (c) 2011, Milos Svasek, Kohsuke Kawaguchi, etc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.antexec;

import hudson.*;
import hudson.model.*;
//TODO: Dependency on hudson.tasks._ant.AntConsoleAnnotator
import hudson.tasks._ant.AntConsoleAnnotator;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Invokes the Apache Ant script entered on the hudson build configuration.
 * <p/>
 *
 * @author Milos Svasek
 */
public class AntExec extends Builder {
    protected static final String myName = "antexec";
    protected static final String buildXml = myName + "_build.xml";
    private String scriptSource;
    private String properties;
    private String antHome;
    private String antOpts;
    private Boolean verbose;
    private Boolean emacs;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public AntExec(String scriptSource, String properties, String antHome, String antOpts, Boolean verbose, Boolean emacs) {
        this.scriptSource = scriptSource;
        this.properties = properties;
        this.antHome = antHome;
        this.antOpts = antOpts;
        this.verbose = verbose;
        this.emacs = emacs;
    }

    /**
     * Returns content of text area with script source from job configuration screen
     *
     * @return String scriptSource
     */
    public String getScriptSource() {
        return scriptSource;
    }

    /**
     * Returns content of text field with properties from job configuration screen
     *
     * @return String properties
     */
    public String getProperties() {
        return properties;
    }

    /**
     * Returns content of text field with ANT_HOME from job configuration screen
     *
     * @return String antHome
     */
    public String getAntHome() {
        return antHome;
    }

    /**
     * Returns content of text field with java/ant options from job configuration screen.
     * It will be used for ANT_OPTS environment variable
     *
     * @return String antOpts
     */
    public String getAntOpts() {
        return antOpts;
    }

    /**
     * Returns checkbox boolean from job configuration screen
     *
     * @return Boolean verbose
     */
    public Boolean getVerbose() {
        return verbose;
    }

    /**
     * Returns checkbox boolean from job configuration screen
     *
     * @return Boolean verbose
     */
    public Boolean getEmacs() {
        return emacs;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = new EnvVars(build.getBuildVariables());
        env.putAll(hudson.slaves.SlaveComputer.currentComputer().getEnvironment());
        //env.putAll(EnvironmentVariablesNodeProperty(build.getBuiltOn().getNodeName()));
        //hudson.slaves.EnvironmentVariablesNodeProperty.all();

        //Create Ant build.xml file
        FilePath antExeFile = AntExecUtils.getAntHome(build, listener.getLogger(), env, launcher.isUnix(), antHome, verbose);
        FilePath buildFile = AntExecUtils.makeBuildFile(scriptSource, build);
        args.add(antExeFile);

        //Make archive copy of build file to job directory
        buildFile.copyTo(new FilePath(new File(build.getRootDir(), buildXml)));

        //Added build file to the command line
        args.add("-file", buildFile.getName());
        VariableResolver<String> vr = build.getBuildVariableResolver();
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();
        args.addKeyValuePairs("-D", build.getBuildVariables(), sensitiveVars);
        args.addKeyValuePairsFromPropertyString("-D", properties, vr, sensitiveVars);

        if (antOpts != null && antOpts.length() > 0 && !antOpts.equals("")) {
            env.put("ANT_OPTS", env.expand(antOpts));
        }

        //Get and prepare ant-contrib.jar
        if (getDescriptor().useAntContrib()) {
            //TODO: Replace this with better methot
            if (verbose != null && verbose) listener.getLogger().println(Messages.AntExec_UseAntContribTasks());
            FilePath antContribJarOnMaster = new FilePath(Hudson.getInstance().getRootPath(), "plugins/antexec/META-INF/lib/ant-contrib.jar");
            //URL urlAntContrib = new URL(env.get("HUDSON_URL") + "plugin/" + myName + "/lib/ant-contrib.jar");
            FilePath antLibDir = new FilePath(build.getWorkspace(), "antlib");
            FilePath antContribJar = new FilePath(antLibDir, "ant-contrib.jar");
            antContribJar.copyFrom(antContribJarOnMaster.toURI().toURL());
            args.add("-lib", antLibDir.getName());
        } else {
            if (verbose != null && verbose) listener.getLogger().println(Messages.AntExec_UseAntCoreTasksOnly());
        }

        //Add Ant option: -verbose
        if (verbose != null && verbose) args.add("-verbose");

        //Add Ant option: -emacs
        if (emacs != null && emacs) args.add("-emacs");

        //Fixing command line for windows
        if (!launcher.isUnix()) {
            args = args.toWindowsCommand();
            // For some reason, ant on windows rejects empty parameters but unix does not.
            // Add quotes for any empty parameter values:
            List<String> newArgs = new ArrayList<String>(args.toList());
            newArgs.set(newArgs.size() - 1, newArgs.get(newArgs.size() - 1).replaceAll("(?<= )(-D[^\" ]+)= ", "$1=\"\" "));
            args = new ArgumentListBuilder(newArgs.toArray(new String[newArgs.size()]));
        }

        //Content of scriptSource and properties (only if verbose is true
        if (verbose != null && verbose) {
            listener.getLogger().println();
            listener.getLogger().println(Messages.AntExec_DebugScriptSourceFieldBegin());
            listener.getLogger().println(scriptSource);
            listener.getLogger().println(Messages.AntExec_DebugScriptSourceFieldEnd());
            listener.getLogger().println();
            listener.getLogger().println(Messages.AntExec_DebugPropertiesFieldBegin());
            listener.getLogger().println(properties);
            listener.getLogger().println(Messages.AntExec_DebugPropertiesFieldEnd());
            listener.getLogger().println();
        }

        try {
            AntConsoleAnnotator aca = new AntConsoleAnnotator(listener.getLogger(), build.getCharset());
            int r;
            try {
                r = launcher.launch().cmds(args).envs(env).stdout(aca).pwd(buildFile.getParent()).join();
            } finally {
                aca.forceEol();
            }
            return r == 0;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            String errorMessage = Messages.AntExec_ExecFailed() + " " + Messages.AntExec_ProjectConfigNeeded();
            e.printStackTrace(listener.fatalError(errorMessage));
            return false;
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private boolean useAntContrib;

        public DescriptorImpl() {
            super(AntExec.class);
            load();
        }

        //ANT_HOME job configuration field validation
        public FormValidation doCheckAntHome(@QueryParameter File value) {
            // this can be used to check the existence of a file on the server, so needs to be protected
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            if (value.length() == 0)
                return FormValidation.error(Messages.AntExec_AntHomeValidation());

            if (!value.isDirectory())
                return FormValidation.error(Messages.AntExec_NotADirectory(value));

            File antJar = new File(value, "lib/ant.jar");
            if (!antJar.exists())
                return FormValidation.error(Messages.AntExec_NotAntDirectory(value));
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // indicates that this builder can be used with all kinds of project types
            return true;
        }

        public String getDisplayName() {
            return Messages.AntExec_DisplayName();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            useAntContrib = formData.getBoolean("useAntContrib");
            save();
            return super.configure(req, formData);
        }

        public boolean useAntContrib() {
            return useAntContrib;
        }
    }

}